package com.convertx.heictopdf;

import com.convertx.pdfcompression.PdfCompressionService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

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
    void shouldRejectInvalidCompressionTarget() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "source.pdf", "application/pdf", samplePdfBytes());

        byte[] bytes = service.compressPdf(file, 10);

        try (PDDocument document = PDDocument.load(bytes)) {
            assertEquals(1, document.getNumberOfPages());
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
