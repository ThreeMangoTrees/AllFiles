package com.convertx.pdfcompression;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GhostscriptPdfCompressionServiceTest {

    private final GhostscriptPdfCompressionService service = new TestGhostscriptPdfCompressionService();

    @Test
    void shouldReturnOriginalWhenCompressedOutputIsLarger() throws IOException, InterruptedException {
        byte[] original = "pdf-data".getBytes();

        byte[] result = service.compress(original, 50);

        assertArrayEquals(original, result);
    }

    @Test
    void shouldResolveCompressionSettings() {
        GhostscriptPdfCompressionService.CompressionSettings settings = service.resolveCompressionSettings(25);

        assertEquals("/screen", settings.profile());
        assertEquals(72, settings.imageResolution());
    }

    @Test
    void shouldRejectInvalidCompressionTarget() {
        assertThrows(IllegalArgumentException.class, () -> service.compress("pdf".getBytes(), 10));
    }

    private static class TestGhostscriptPdfCompressionService extends GhostscriptPdfCompressionService {

        @Override
        void runGhostscriptCompression(Path inputFile, Path outputFile, int targetPercentage) throws IOException {
            byte[] original = Files.readAllBytes(inputFile);
            byte[] larger = new byte[original.length + 12];
            System.arraycopy(original, 0, larger, 0, original.length);
            Files.write(outputFile, larger);
        }
    }
}
