package com.convertx.heictopdf;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.rtf.RTFEditorKit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class FileToPdfConversionServiceImpl implements FileToPdfConversionService {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("heic", "jpg", "jpeg", "tiff", "gif", "bmp");
    private static final Set<String> HTML_EXTENSIONS = Set.of("html", "htm");
    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "rtf");
    private static final Set<String> LIBREOFFICE_EXTENSIONS = Set.of("docx", "xlsx", "pptx");
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "docx", "xlsx", "jpg", "jpeg", "pptx", "tiff", "gif", "bmp", "txt", "rtf", "html", "htm", "heic"
    );
    private static final float PAGE_MARGIN = 40f;
    private static final float FONT_SIZE = 11f;
    private static final float LEADING = 14f;
    private static final PDType1Font BODY_FONT = PDType1Font.HELVETICA;

    @Override
    public byte[] convert(MultipartFile file) {
        String extension = validateAndGetExtension(file);

        try {
            if (IMAGE_EXTENSIONS.contains(extension)) {
                return convertImageLikeFile(file, extension);
            }
            if (HTML_EXTENSIONS.contains(extension)) {
                return convertHtmlToPdf(file);
            }
            if (TEXT_EXTENSIONS.contains(extension)) {
                return convertTextLikeFile(file, extension);
            }
            if (LIBREOFFICE_EXTENSIONS.contains(extension)) {
                return convertWithLibreOffice(file, extension);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Conversion was interrupted.", ex);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Conversion failed: " + ex.getMessage(), ex);
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported file type: ." + extension);
    }

    @Override
    public byte[] compressPdf(MultipartFile file, int targetPercentage) {
        validatePdfFile(file);
        validateCompressionTarget(targetPercentage);

        Path inputFile = null;
        Path outputFile = null;
        try {
            inputFile = Files.createTempFile("convertx-compress-input-", ".pdf");
            outputFile = Files.createTempFile("convertx-compress-output-", ".pdf");
            file.transferTo(inputFile);
            byte[] originalBytes = Files.readAllBytes(inputFile);
            runGhostscriptCompression(inputFile, outputFile, targetPercentage);
            byte[] compressedBytes = Files.readAllBytes(outputFile);
            return compressedBytes.length < originalBytes.length ? compressedBytes : originalBytes;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Compression was interrupted.", ex);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Compression failed: " + ex.getMessage(), ex);
        } finally {
            deleteFile(inputFile);
            deleteFile(outputFile);
        }
    }

    @Override
    public byte[] mergePdfs(List<MultipartFile> files) {
        if (files == null || files.size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least two PDF files are required for merging.");
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDFMergerUtility merger = new PDFMergerUtility();
            merger.setDestinationStream(outputStream);

            for (MultipartFile file : files) {
                validatePdfFile(file);
                merger.addSource(new ByteArrayInputStream(file.getBytes()));
            }

            merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly());
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Merge failed: " + ex.getMessage(), ex);
        }
    }

    byte[] createPdfFromImage(BufferedImage image) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(new PDRectangle(image.getWidth(), image.getHeight()));
            document.addPage(page);

            PDImageXObject pdfImage = LosslessFactory.createFromImage(document, image);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.drawImage(pdfImage, 0, 0, image.getWidth(), image.getHeight());
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    byte[] createPdfFromText(String text) throws IOException {
        List<String> paragraphs = text == null || text.isBlank() ? List.of("") : List.of(text.split("\\R", -1));
        float pageWidth = PDRectangle.LETTER.getWidth();
        float pageHeight = PDRectangle.LETTER.getHeight();
        float usableWidth = pageWidth - (2 * PAGE_MARGIN);
        int maxCharsPerLine = Math.max(20, (int) (usableWidth / 6.2f));

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            PDPageContentStream contentStream = new PDPageContentStream(document, page);
            contentStream.setFont(BODY_FONT, FONT_SIZE);
            contentStream.beginText();
            contentStream.newLineAtOffset(PAGE_MARGIN, pageHeight - PAGE_MARGIN);
            contentStream.setLeading(LEADING);

            float y = pageHeight - PAGE_MARGIN;
            for (String paragraph : paragraphs) {
                List<String> wrappedLines = wrapLine(paragraph, maxCharsPerLine);
                for (String line : wrappedLines) {
                    if (y - LEADING <= PAGE_MARGIN) {
                        contentStream.endText();
                        contentStream.close();
                        page = new PDPage(PDRectangle.LETTER);
                        document.addPage(page);
                        contentStream = new PDPageContentStream(document, page);
                        contentStream.setFont(BODY_FONT, FONT_SIZE);
                        contentStream.beginText();
                        contentStream.newLineAtOffset(PAGE_MARGIN, pageHeight - PAGE_MARGIN);
                        contentStream.setLeading(LEADING);
                        y = pageHeight - PAGE_MARGIN;
                    }
                    contentStream.showText(line);
                    contentStream.newLine();
                    y -= LEADING;
                }
            }

            contentStream.endText();
            contentStream.close();
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] convertImageLikeFile(MultipartFile file, String extension) throws IOException, InterruptedException {
        if ("heic".equals(extension)) {
            return convertHeicToPdf(file);
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(file.getBytes())) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The image file could not be decoded.");
            }
            return createPdfFromImage(image);
        }
    }

    private byte[] convertHeicToPdf(MultipartFile file) throws IOException, InterruptedException {
        Path inputFile = Files.createTempFile("convertx-upload-", ".heic");
        Path pngFile = Files.createTempFile("convertx-output-", ".png");

        try {
            file.transferTo(inputFile);
            convertHeicToPng(inputFile, pngFile);
            BufferedImage image = ImageIO.read(pngFile.toFile());
            if (image == null) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The HEIC file could not be decoded.");
            }
            return createPdfFromImage(image);
        } finally {
            Files.deleteIfExists(inputFile);
            Files.deleteIfExists(pngFile);
        }
    }

    private byte[] convertHtmlToPdf(MultipartFile file) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            String html = new String(file.getBytes(), StandardCharsets.UTF_8);
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(outputStream);
            builder.run();
            return outputStream.toByteArray();
        }
    }

    private byte[] convertTextLikeFile(MultipartFile file, String extension) throws IOException {
        String content = "rtf".equals(extension) ? extractTextFromRtf(file.getBytes()) : readUtf8Text(file.getBytes());
        return createPdfFromText(content);
    }

    private byte[] convertWithLibreOffice(MultipartFile file, String extension) throws IOException, InterruptedException {
        Path inputDir = Files.createTempDirectory("convertx-office-input-");
        Path outputDir = Files.createTempDirectory("convertx-office-output-");
        Path inputFile = inputDir.resolve("source." + extension);
        Path outputFile = outputDir.resolve("source.pdf");

        try {
            file.transferTo(inputFile);
            runLibreOfficeConversion(inputFile, outputDir, extension);
            if (!Files.exists(outputFile)) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "LibreOffice did not produce a PDF output.");
            }
            return Files.readAllBytes(outputFile);
        } finally {
            deleteDirectory(inputDir);
            deleteDirectory(outputDir);
        }
    }

    private void convertHeicToPng(Path inputFile, Path outputFile) throws IOException, InterruptedException {
        runCommand(
                new ProcessBuilder("sips", "-s", "format", "png", inputFile.toString(), "--out", outputFile.toString()),
                "Unable to convert HEIC input with sips.",
                "HEIC conversion requires the macOS 'sips' command."
        );
    }

    private void runLibreOfficeConversion(Path inputFile, Path outputDir, String extension) throws IOException, InterruptedException {
        runCommand(
                new ProcessBuilder("soffice", "--headless", "--convert-to", "pdf", "--outdir", outputDir.toString(), inputFile.toString()),
                "Unable to convert ." + extension + " with LibreOffice.",
                "Converting ." + extension + " requires LibreOffice with the 'soffice' command on the PATH."
        );
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
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Compression target must be one of: 25, 50, 75."
            );
        };
    }

    private void runCommand(ProcessBuilder processBuilder, String failureMessage, String missingCommandMessage)
            throws IOException, InterruptedException {
        Process process;
        try {
            process = processBuilder.redirectErrorStream(true).start();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, missingCommandMessage, ex);
        }

        String output;
        try (Reader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8);
             StringWriter writer = new StringWriter()) {
            reader.transferTo(writer);
            output = writer.toString();
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, failureMessage + " Output: " + output.trim());
        }
    }

    private String extractTextFromRtf(byte[] bytes) throws IOException {
        RTFEditorKit editorKit = new RTFEditorKit();
        Document document = editorKit.createDefaultDocument();
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
            editorKit.read(inputStream, document, 0);
            return document.getText(0, document.getLength());
        } catch (BadLocationException ex) {
            throw new IOException("Unable to read RTF content.", ex);
        }
    }

    private String readUtf8Text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void validateCompressionTarget(int targetPercentage) {
        if (targetPercentage != 25 && targetPercentage != 50 && targetPercentage != 75) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Compression target must be one of: 25, 50, 75.");
        }
    }

    private void validatePdfFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A PDF file is required.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only .pdf files are supported for this operation.");
        }
    }

    private List<String> wrapLine(String input, int maxCharsPerLine) {
        if (input == null || input.isEmpty()) {
            return List.of("");
        }

        String[] words = input.split("\\s+");
        StringBuilder currentLine = new StringBuilder();
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();

        for (String word : words) {
            if (currentLine.isEmpty()) {
                currentLine.append(word);
                continue;
            }

            if (currentLine.length() + 1 + word.length() <= maxCharsPerLine) {
                currentLine.append(' ').append(word);
                continue;
            }

            lines.add(currentLine.toString());
            currentLine = new StringBuilder(word);
        }

        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }

        try (var paths = Files.walk(directory)) {
            paths.sorted((left, right) -> right.compareTo(left))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
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

    private String validateAndGetExtension(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A supported file is required.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank() || !filename.contains(".")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file must include a valid extension.");
        }

        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported file type. Supported extensions: " + String.join(", ", SUPPORTED_EXTENSIONS)
            );
        }

        return extension;
    }
}
