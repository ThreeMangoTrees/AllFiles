package com.convertx.heictopdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileToPdfConversionServiceImplTest {

    private final FileToPdfConversionServiceImpl service = new TestFileToPdfConversionServiceImpl();

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
    void shouldReturnOriginalPdfWhenCompressionGetsLarger() throws IOException {
        FileToPdfConversionServiceImpl service = new LargerOutputCompressionService();
        byte[] original = samplePdfBytes();
        MockMultipartFile file = new MockMultipartFile("file", "source.pdf", "application/pdf", original);

        byte[] bytes = service.compressPdf(file, 50);

        assertEquals(original.length, bytes.length);
    }

    @Test
    void shouldRejectInvalidCompressionTarget() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "source.pdf", "application/pdf", samplePdfBytes());

        assertThrows(ResponseStatusException.class, () -> service.compressPdf(file, 10));
    }

    private byte[] samplePdfBytes() throws IOException {
        return service.createPdfFromText("sample pdf");
    }

    private static class TestFileToPdfConversionServiceImpl extends FileToPdfConversionServiceImpl {

        @Override
        void runGhostscriptCompression(Path inputFile, Path outputFile, int targetPercentage) throws IOException {
            Files.copy(inputFile, outputFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static class LargerOutputCompressionService extends FileToPdfConversionServiceImpl {

        @Override
        void runGhostscriptCompression(Path inputFile, Path outputFile, int targetPercentage) throws IOException {
            byte[] original = Files.readAllBytes(inputFile);
            byte[] larger = new byte[original.length + 32];
            System.arraycopy(original, 0, larger, 0, original.length);
            Files.write(outputFile, larger);
        }
    }
}
