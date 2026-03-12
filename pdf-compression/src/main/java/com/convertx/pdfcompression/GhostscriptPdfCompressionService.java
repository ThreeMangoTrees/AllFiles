package com.convertx.pdfcompression;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class GhostscriptPdfCompressionService implements PdfCompressionService {

    @Override
    public byte[] compress(byte[] originalPdf, int targetPercentage) throws IOException, InterruptedException {
        Objects.requireNonNull(originalPdf, "originalPdf must not be null");
        validateCompressionTarget(targetPercentage);

        Path inputFile = null;
        Path outputFile = null;
        try {
            inputFile = Files.createTempFile("convertx-compress-input-", ".pdf");
            outputFile = Files.createTempFile("convertx-compress-output-", ".pdf");
            Files.write(inputFile, originalPdf);
            runGhostscriptCompression(inputFile, outputFile, targetPercentage);
            byte[] compressedBytes = Files.readAllBytes(outputFile);
            return compressedBytes.length < originalPdf.length ? compressedBytes : originalPdf;
        } finally {
            deleteFile(inputFile);
            deleteFile(outputFile);
        }
    }

    void runGhostscriptCompression(Path inputFile, Path outputFile, int targetPercentage) throws IOException, InterruptedException {
        CompressionSettings settings = resolveCompressionSettings(targetPercentage);
        runCommand(
                new ProcessBuilder(
                        "gs",
                        "-sDEVICE=pdfwrite",
                        "-dCompatibilityLevel=1.4",
                        "-dPDFSETTINGS=" + settings.profile(),
                        "-dDownsampleColorImages=true",
                        "-dDownsampleGrayImages=true",
                        "-dDownsampleMonoImages=true",
                        "-dColorImageDownsampleType=/Bicubic",
                        "-dGrayImageDownsampleType=/Bicubic",
                        "-dMonoImageDownsampleType=/Subsample",
                        "-dColorImageResolution=" + settings.imageResolution(),
                        "-dGrayImageResolution=" + settings.imageResolution(),
                        "-dMonoImageResolution=" + settings.monoResolution(),
                        "-dDetectDuplicateImages=true",
                        "-dCompressFonts=true",
                        "-dSubsetFonts=true",
                        "-dNOPAUSE",
                        "-dQUIET",
                        "-dBATCH",
                        "-sOutputFile=" + outputFile,
                        inputFile.toString()
                ),
                "Unable to compress PDF with Ghostscript.",
                "PDF compression requires Ghostscript with the 'gs' command on the PATH."
        );
    }

    CompressionSettings resolveCompressionSettings(int targetPercentage) {
        return switch (targetPercentage) {
            case 25 -> new CompressionSettings("/screen", 72, 150);
            case 50 -> new CompressionSettings("/ebook", 110, 200);
            case 75 -> new CompressionSettings("/printer", 150, 300);
            default -> throw new IllegalArgumentException("Compression target must be one of: 25, 50, 75.");
        };
    }

    private void validateCompressionTarget(int targetPercentage) {
        if (targetPercentage != 25 && targetPercentage != 50 && targetPercentage != 75) {
            throw new IllegalArgumentException("Compression target must be one of: 25, 50, 75.");
        }
    }

    private void runCommand(ProcessBuilder processBuilder, String failureMessage, String missingCommandMessage)
            throws IOException, InterruptedException {
        Process process;
        try {
            process = processBuilder.redirectErrorStream(true).start();
        } catch (IOException ex) {
            throw new IOException(missingCommandMessage, ex);
        }

        String output;
        try (Reader reader = new InputStreamReader(process.getInputStream());
             StringWriter writer = new StringWriter()) {
            reader.transferTo(writer);
            output = writer.toString();
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException(failureMessage + " Output: " + output.trim());
        }
    }

    private void deleteFile(Path file) {
        if (file == null) {
            return;
        }

        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }

    record CompressionSettings(String profile, int imageResolution, int monoResolution) {
    }
}
