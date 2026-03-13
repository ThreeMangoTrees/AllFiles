package com.convertx.heictopdf;

import com.convertx.pdfcompression.PdfCompressionService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileToPdfConversionServiceImplTest {

    private final FileToPdfConversionServiceImpl service = new FileToPdfConversionServiceImpl(new PassthroughPdfCompressionService());

    @Test
    void createPdfShouldProduceSinglePageDocument() throws IOException {
        BufferedImage image = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
        image.createGraphics().setColor(Color.ORANGE);

        byte[] bytes = service.createPdfFromImage(image);

        assertTrue(bytes.length > 0);

        try (PDDocument document = PDDocument.load(bytes)) {
            assertEquals(1, document.getNumberOfPages());
            assertEquals(120f, document.getPage(0).getMediaBox().getWidth());
            assertEquals(80f, document.getPage(0).getMediaBox().getHeight());
        }
    }

    @Test
    void shouldConvertTextFileToPdf() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "First line\nSecond line".getBytes()
        );

        byte[] bytes = service.convert(file);

        try (PDDocument document = PDDocument.load(bytes)) {
            assertEquals(1, document.getNumberOfPages());
        }
    }

    @Test
    void shouldConvertHtmlFileToPdf() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "page.html",
                "text/html",
                "<html><body><h1>Hello</h1><p>PDF</p></body></html>".getBytes()
        );

        byte[] bytes = service.convert(file);

        try (PDDocument document = PDDocument.load(bytes)) {
            assertEquals(1, document.getNumberOfPages());
        }
    }

    @Test
    void shouldRejectUnsupportedFileType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "archive.zip",
                "application/zip",
                "data".getBytes()
        );

        assertThrows(ResponseStatusException.class, () -> service.convert(file));
    }

    @Test
    void shouldMergeMultiplePdfs() throws IOException {
        MockMultipartFile first = new MockMultipartFile("files", "first.pdf", "application/pdf", samplePdfBytes());
        MockMultipartFile second = new MockMultipartFile("files", "second.pdf", "application/pdf", samplePdfBytes());

        byte[] bytes = service.mergePdfs(List.of(first, second));

        try (PDDocument document = PDDocument.load(bytes)) {
            assertEquals(2, document.getNumberOfPages());
        }
    }

    @Test
    void shouldCompressPdf() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "source.pdf", "application/pdf", samplePdfBytes());

        byte[] bytes = service.compressPdf(file, 50);

        try (PDDocument document = PDDocument.load(bytes)) {
            assertEquals(1, document.getNumberOfPages());
        }
    }

    @Test
    void shouldRejectInvalidPdfDuringCompression() {
        MockMultipartFile file = new MockMultipartFile("file", "source.pdf", "application/pdf", "not-a-pdf".getBytes());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.compressPdf(file, 50));

        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("The uploaded PDF file is invalid: source.pdf"));
    }

    @Test
    void shouldRejectInvalidCompressionTarget() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "source.pdf", "application/pdf", samplePdfBytes());

        assertThrows(ResponseStatusException.class, () -> service.compressPdf(file, 10));
    }

    @Test
    void shouldSkipCompressionWhenTargetIsHundred() throws IOException {
        byte[] original = samplePdfBytes();
        MockMultipartFile file = new MockMultipartFile("file", "source.pdf", "application/pdf", original);

        byte[] bytes = service.compressPdf(file, 100);

        assertEquals(original.length, bytes.length);
    }

    @Test
    void shouldConvertMergeAndSkipCompressionForHundredPercent() throws IOException {
        MockMultipartFile textFile = new MockMultipartFile(
                "files",
                "notes.txt",
                "text/plain",
                "one page".getBytes()
        );
        MockMultipartFile pdfFile = new MockMultipartFile(
                "files",
                "source.pdf",
                "application/pdf",
                samplePdfBytes()
        );

        byte[] bytes = service.convertMergeAndOptionallyCompress(List.of(textFile, pdfFile), 100);

        try (PDDocument document = PDDocument.load(bytes)) {
            assertEquals(2, document.getNumberOfPages());
        }
    }

    @Test
    void shouldRejectInvalidPdfDuringCombinedWorkflow() throws IOException {
        MockMultipartFile textFile = new MockMultipartFile(
                "files",
                "notes.txt",
                "text/plain",
                "one page".getBytes()
        );
        MockMultipartFile invalidPdf = new MockMultipartFile(
                "files",
                "source.pdf",
                "application/pdf",
                "not-a-pdf".getBytes()
        );

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.convertMergeAndOptionallyCompress(List.of(textFile, invalidPdf), 100)
        );

        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("The uploaded PDF file is invalid: source.pdf"));
    }

    @Test
    void shouldRotatePdfAntiClockwise() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "source.pdf", "application/pdf", samplePdfBytes());

        FileToPdfConversionService.ProcessedFile rotated = service.rotateFile(file, 90);

        assertEquals("source-rotated-90.pdf", rotated.filename());
        assertEquals("application/pdf", rotated.contentType());

        try (PDDocument document = PDDocument.load(rotated.bytes())) {
            assertEquals(270, document.getPage(0).getRotation());
        }
    }

    @Test
    void shouldRejectInvalidRotationTarget() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "source.pdf", "application/pdf", samplePdfBytes());

        assertThrows(ResponseStatusException.class, () -> service.rotateFile(file, 45));
    }

    @Test
    void shouldRotateJpegAndPreserveFormat() throws IOException {
        BufferedImage image = new BufferedImage(80, 40, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", outputStream);
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", outputStream.toByteArray());

        FileToPdfConversionService.ProcessedFile rotated = service.rotateFile(file, 90);

        assertEquals("photo-rotated-90.jpg", rotated.filename());
        assertEquals("image/jpeg", rotated.contentType());

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(rotated.bytes())) {
            BufferedImage rotatedImage = ImageIO.read(inputStream);
            assertEquals(40, rotatedImage.getWidth());
            assertEquals(80, rotatedImage.getHeight());
        }
    }

    @Test
    void shouldRotateTextFileByConvertingToPdf() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

        FileToPdfConversionService.ProcessedFile rotated = service.rotateFile(file, 90);

        assertEquals("notes-rotated-90.pdf", rotated.filename());
        assertEquals("application/pdf", rotated.contentType());

        try (PDDocument document = PDDocument.load(rotated.bytes())) {
            assertEquals(270, document.getPage(0).getRotation());
        }
    }

    private byte[] samplePdfBytes() throws IOException {
        return service.createPdfFromText("sample pdf");
    }

    private static class PassthroughPdfCompressionService implements PdfCompressionService {

        @Override
        public byte[] compress(byte[] originalPdf, int targetPercentage) {
            return originalPdf;
        }
    }
}
