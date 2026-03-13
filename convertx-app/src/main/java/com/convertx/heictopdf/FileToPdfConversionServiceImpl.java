package com.convertx.heictopdf;

import com.convertx.pdfcompression.GhostscriptPdfCompressionService;
import com.convertx.pdfcompression.PdfCompressionService;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class FileToPdfConversionServiceImpl implements FileToPdfConversionService {

    private static final Logger log = LoggerFactory.getLogger(FileToPdfConversionServiceImpl.class);
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("heic", "jpg", "jpeg", "tiff", "gif", "bmp");
    private static final Set<String> HTML_EXTENSIONS = Set.of("html", "htm");
    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "rtf");
    private static final Set<String> LIBREOFFICE_EXTENSIONS = Set.of("docx", "xlsx", "pptx");
    private static final Set<String> ROTATABLE_IMAGE_EXTENSIONS = Set.of("heic", "jpg", "jpeg", "tiff", "gif", "bmp");
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "docx", "xlsx", "jpg", "jpeg", "pptx", "tiff", "gif", "bmp", "txt", "rtf", "html", "htm", "heic"
    );
    private static final Set<Integer> COMPRESSION_TARGETS = Set.of(25, 50, 75, 100);
    private static final Set<Integer> ROTATION_TARGETS = Set.of(90, 180, 270);
    private static final float PAGE_MARGIN = 40f;
    private static final float FONT_SIZE = 11f;
    private static final float LEADING = 14f;
    private static final PDType1Font BODY_FONT = PDType1Font.HELVETICA;
    private final PdfCompressionService pdfCompressionService;
    private final UploadedFileSecurityValidator uploadedFileSecurityValidator;

    public FileToPdfConversionServiceImpl() {
        this(new GhostscriptPdfCompressionService(), new UploadedFileSecurityValidator(new ApplicationSecurityProperties()));
    }

    FileToPdfConversionServiceImpl(PdfCompressionService pdfCompressionService) {
        this(pdfCompressionService, new UploadedFileSecurityValidator(new ApplicationSecurityProperties()));
    }

    FileToPdfConversionServiceImpl(
            PdfCompressionService pdfCompressionService,
            UploadedFileSecurityValidator uploadedFileSecurityValidator
    ) {
        this.pdfCompressionService = pdfCompressionService;
        this.uploadedFileSecurityValidator = uploadedFileSecurityValidator;
    }

    @Override
    public byte[] convert(MultipartFile file) {
        String extension = validateAndGetExtension(file);
        log.info("Starting conversion for file {} with extension {}", safeFilename(file), extension);

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
            log.error("Conversion interrupted for file {}", safeFilename(file), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Conversion was interrupted.", ex);
        } catch (IOException ex) {
            log.error("Conversion failed for file {}", safeFilename(file), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Conversion failed: " + ex.getMessage(), ex);
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported file type: ." + extension);
    }

    @Override
    public byte[] compressPdf(MultipartFile file, int targetPercentage) {
        validatePdfFile(file);
        validateCompressionTarget(targetPercentage);
        log.info("Starting compression for file {} with target {}", safeFilename(file), targetPercentage);
        try {
            if (targetPercentage == 100) {
                log.info("Skipping compression for file {} because target is 100", safeFilename(file));
                return file.getBytes();
            }
            return pdfCompressionService.compress(file.getBytes(), targetPercentage);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Compression interrupted for file {}", safeFilename(file), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Compression was interrupted.", ex);
        } catch (IOException ex) {
            log.error("Compression failed for file {}", safeFilename(file), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Compression failed: " + ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            log.warn("Compression rejected for file {}: {}", safeFilename(file), ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @Override
    public byte[] mergePdfs(List<MultipartFile> files) {
        if (files == null || files.size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least two PDF files are required for merging.");
        }

        log.info("Starting merge for {} files", files.size());
        try {
            List<byte[]> pdfBytes = new ArrayList<>(files.size());
            for (MultipartFile file : files) {
                validatePdfFile(file);
                pdfBytes.add(file.getBytes());
            }
            return mergePdfBytes(pdfBytes);
        } catch (IOException ex) {
            log.error("Merge failed for {} files", files.size(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Merge failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public byte[] convertMergeAndOptionallyCompress(List<MultipartFile> files, int targetPercentage) {
        if (files == null || files.size() < 2) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "At least two files are required for the combined convert, merge, and compress operation."
            );
        }

        validateCompressionTarget(targetPercentage);
        log.info("Starting combined workflow for {} files with target {}", files.size(), targetPercentage);

        List<byte[]> pdfBytes = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            pdfBytes.add(asPdfBytes(file));
        }

        byte[] mergedPdf;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDFMergerUtility merger = new PDFMergerUtility();
            merger.setDestinationStream(outputStream);

            for (byte[] pdf : pdfBytes) {
                merger.addSource(new ByteArrayInputStream(pdf));
            }
            merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly());
            mergedPdf = outputStream.toByteArray();
        } catch (IOException ex) {
            log.error("Merge step failed in combined workflow for {} files", files.size(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Merge failed: " + ex.getMessage(), ex);
        }

        if (targetPercentage == 100) {
            log.info("Skipping compression in combined workflow because target is 100");
            return mergedPdf;
        }

        try {
            return pdfCompressionService.compress(mergedPdf, targetPercentage);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Compression interrupted in combined workflow", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Compression was interrupted.", ex);
        } catch (IOException ex) {
            log.error("Compression failed in combined workflow", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Compression failed: " + ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            log.warn("Combined workflow rejected compression target {}: {}", targetPercentage, ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @Override
    public ProcessedFile rotateFile(MultipartFile file, int anticlockwiseDegrees) {
        validateRotationTarget(anticlockwiseDegrees);
        log.info("Starting rotation for file {} by {} degrees anti-clockwise", safeFilename(file), anticlockwiseDegrees);

        String extension = getExtension(file);
        String baseName = extractBaseName(file.getOriginalFilename());

        if ("pdf".equals(extension)) {
            return rotatePdfFile(file, anticlockwiseDegrees, baseName);
        }

        if (ROTATABLE_IMAGE_EXTENSIONS.contains(extension)) {
            return rotateImageFile(file, extension, anticlockwiseDegrees, baseName);
        }

        if (TEXT_EXTENSIONS.contains(extension) || HTML_EXTENSIONS.contains(extension)) {
            return rotateConvertedPdf(file, anticlockwiseDegrees, baseName);
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Rotation is supported for PDFs, images, and text files. Text files are converted to PDF before rotation."
        );
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
        runFirstSuccessfulCommand(
                List.of(
                        List.of("magick", inputFile.toString(), outputFile.toString()),
                        List.of("convert", inputFile.toString(), outputFile.toString()),
                        List.of("heif-convert", inputFile.toString(), outputFile.toString())
                ),
                "Unable to convert HEIC input.",
                "HEIC conversion requires one of these commands on the PATH: 'magick', 'convert', or 'heif-convert'."
        );
    }

    private ProcessedFile rotatePdfFile(MultipartFile file, int anticlockwiseDegrees, String baseName) {
        try {
            return rotatePdfBytes(file.getBytes(), anticlockwiseDegrees, baseName);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Rotation failed: " + ex.getMessage(), ex);
        }
    }

    private ProcessedFile rotateConvertedPdf(MultipartFile file, int anticlockwiseDegrees, String baseName) {
        byte[] pdfBytes = convert(file);
        return rotatePdfBytes(pdfBytes, anticlockwiseDegrees, baseName);
    }

    private ProcessedFile rotatePdfBytes(byte[] pdfBytes, int anticlockwiseDegrees, String baseName) {
        try (PDDocument document = PDDocument.load(pdfBytes);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            int clockwiseRotation = normalizeClockwiseRotation(anticlockwiseDegrees);
            for (PDPage page : document.getPages()) {
                int currentRotation = page.getRotation();
                page.setRotation((currentRotation + clockwiseRotation) % 360);
            }
            document.save(outputStream);
            return new ProcessedFile(outputStream.toByteArray(), baseName + "-rotated-" + anticlockwiseDegrees + ".pdf", "application/pdf");
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Rotation failed: " + ex.getMessage(), ex);
        }
    }

    private ProcessedFile rotateImageFile(MultipartFile file, String extension, int anticlockwiseDegrees, String baseName) {
        try {
            byte[] rotatedBytes = "heic".equals(extension)
                    ? rotateHeic(file, anticlockwiseDegrees)
                    : rotateRasterImage(file, extension, anticlockwiseDegrees);
            String outputExtension = rotatedImageOutputExtension(extension);
            return new ProcessedFile(
                    rotatedBytes,
                    baseName + "-rotated-" + anticlockwiseDegrees + "." + outputExtension,
                    contentTypeForExtension(outputExtension)
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Rotation was interrupted.", ex);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Rotation failed: " + ex.getMessage(), ex);
        }
    }

    private byte[] rotateRasterImage(MultipartFile file, String extension, int anticlockwiseDegrees) throws IOException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(file.getBytes());
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The image file could not be decoded.");
            }

            BufferedImage rotated = rotateBufferedImage(image, anticlockwiseDegrees, "jpg".equals(extension) || "jpeg".equals(extension));
            if (!ImageIO.write(rotated, extension, outputStream)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to write the rotated image as ." + extension + ".");
            }
            return outputStream.toByteArray();
        }
    }

    private byte[] rotateHeic(MultipartFile file, int anticlockwiseDegrees) throws IOException, InterruptedException {
        Path inputFile = Files.createTempFile("convertx-rotate-input-", ".heic");
        Path outputFile = Files.createTempFile("convertx-rotate-output-", ".jpg");

        try {
            file.transferTo(inputFile);
            int clockwiseRotation = normalizeClockwiseRotation(anticlockwiseDegrees);
            runFirstSuccessfulCommand(
                    List.of(
                            List.of("magick", inputFile.toString(), "-rotate", String.valueOf(clockwiseRotation), outputFile.toString()),
                            List.of("convert", inputFile.toString(), "-rotate", String.valueOf(clockwiseRotation), outputFile.toString())
                    ),
                    "Unable to rotate HEIC input.",
                    "HEIC rotation requires ImageMagick with either the 'magick' or 'convert' command on the PATH."
            );
            return Files.readAllBytes(outputFile);
        } finally {
            Files.deleteIfExists(inputFile);
            Files.deleteIfExists(outputFile);
        }
    }

    private void runLibreOfficeConversion(Path inputFile, Path outputDir, String extension) throws IOException, InterruptedException {
        runCommand(
                new ProcessBuilder("soffice", "--headless", "--convert-to", "pdf", "--outdir", outputDir.toString(), inputFile.toString()),
                "Unable to convert ." + extension + " with LibreOffice.",
                "Converting ." + extension + " requires LibreOffice with the 'soffice' command on the PATH."
        );
    }

    private void runFirstSuccessfulCommand(List<List<String>> commands, String failureMessage, String missingCommandMessage)
            throws IOException, InterruptedException {
        List<String> commandFailures = new ArrayList<>();
        IOException lastStartException = null;

        for (List<String> command : commands) {
            try {
                CommandResult result = executeCommand(new ProcessBuilder(command));
                if (result.exitCode() == 0) {
                    return;
                }
                commandFailures.add(command.get(0) + ": " + formatCommandOutput(result.output(), result.exitCode()));
            } catch (IOException ex) {
                lastStartException = ex;
            }
        }

        if (commandFailures.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, missingCommandMessage, lastStartException);
        }

        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                failureMessage + " Output: " + String.join(" | ", commandFailures)
        );
    }

    private void runCommand(ProcessBuilder processBuilder, String failureMessage, String missingCommandMessage)
            throws IOException, InterruptedException {
        try {
            CommandResult result = executeCommand(processBuilder);
            if (result.exitCode() != 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        failureMessage + " Output: " + formatCommandOutput(result.output(), result.exitCode())
                );
            }
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, missingCommandMessage, ex);
        }
    }

    private CommandResult executeCommand(ProcessBuilder processBuilder) throws IOException, InterruptedException {
        Process process = processBuilder.redirectErrorStream(true).start();
        String output;
        try (Reader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8);
             StringWriter writer = new StringWriter()) {
            reader.transferTo(writer);
            output = writer.toString();
        }

        int exitCode = process.waitFor();
        return new CommandResult(exitCode, output);
    }

    private String formatCommandOutput(String output, int exitCode) {
        String trimmedOutput = output == null ? "" : output.trim();
        return trimmedOutput.isEmpty() ? "exit code " + exitCode : trimmedOutput;
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

    private byte[] asPdfBytes(MultipartFile file) {
        String extension = getExtension(file);
        if ("pdf".equals(extension)) {
            return validateAndReadPdfBytes(file);
        }
        return convert(file);
    }

    private void validatePdfFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A PDF file is required.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only .pdf files are supported for this operation.");
        }

        validateAndReadPdfBytes(file);
    }

    private byte[] validateAndReadPdfBytes(MultipartFile file) {
        uploadedFileSecurityValidator.validate(file, "pdf");
        byte[] pdfBytes;
        try {
            pdfBytes = file.getBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read PDF input.", ex);
        }

        try (PDDocument ignored = PDDocument.load(pdfBytes)) {
            return pdfBytes;
        } catch (IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The uploaded PDF file is invalid: " + safeFilename(file),
                    ex
            );
        }
    }

    private void validateCompressionTarget(int targetPercentage) {
        if (!COMPRESSION_TARGETS.contains(targetPercentage)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Compression target must be one of: 25, 50, 75, 100."
            );
        }
    }

    private void validateRotationTarget(int anticlockwiseDegrees) {
        if (!ROTATION_TARGETS.contains(anticlockwiseDegrees)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Rotation must be one of: 90, 180, 270 degrees anti-clockwise."
            );
        }
    }

    private int normalizeClockwiseRotation(int anticlockwiseDegrees) {
        return Math.floorMod(360 - anticlockwiseDegrees, 360);
    }

    private BufferedImage rotateBufferedImage(BufferedImage source, int anticlockwiseDegrees, boolean forceRgb) {
        int width = source.getWidth();
        int height = source.getHeight();
        int resultWidth = anticlockwiseDegrees == 180 ? width : height;
        int resultHeight = anticlockwiseDegrees == 180 ? height : width;
        int imageType = forceRgb ? BufferedImage.TYPE_INT_RGB
                : source.getType() == BufferedImage.TYPE_CUSTOM ? BufferedImage.TYPE_INT_ARGB : source.getType();

        BufferedImage rotated = new BufferedImage(resultWidth, resultHeight, imageType);
        Graphics2D graphics = rotated.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            AffineTransform transform = new AffineTransform();
            switch (anticlockwiseDegrees) {
                case 90 -> {
                    transform.translate(0, width);
                    transform.rotate(Math.toRadians(-90));
                }
                case 180 -> {
                    transform.translate(width, height);
                    transform.rotate(Math.toRadians(180));
                }
                case 270 -> {
                    transform.translate(height, 0);
                    transform.rotate(Math.toRadians(90));
                }
                default -> throw new IllegalArgumentException("Unsupported rotation.");
            }
            graphics.drawImage(source, transform, null);
        } finally {
            graphics.dispose();
        }
        return rotated;
    }

    private byte[] mergePdfBytes(List<byte[]> pdfBytes) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDFMergerUtility merger = new PDFMergerUtility();
            merger.setDestinationStream(outputStream);

            for (byte[] pdf : pdfBytes) {
                merger.addSource(new ByteArrayInputStream(pdf));
            }

            merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly());
            return outputStream.toByteArray();
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

    private String validateAndGetExtension(MultipartFile file) {
        String extension = getExtension(file);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported file type. Supported extensions: " + String.join(", ", SUPPORTED_EXTENSIONS)
            );
        }

        uploadedFileSecurityValidator.validate(file, extension);
        return extension;
    }

    private String getExtension(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A supported file is required.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank() || !filename.contains(".")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file must include a valid extension.");
        }

        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String extractBaseName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "file";
        }

        int extensionIndex = filename.lastIndexOf('.');
        return extensionIndex > 0 ? filename.substring(0, extensionIndex) : filename;
    }

    private String contentTypeForExtension(String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "jpg", "jpeg" -> "image/jpeg";
            case "tiff" -> "image/tiff";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "heic" -> "image/heic";
            default -> "application/octet-stream";
        };
    }

    String rotatedImageOutputExtension(String extension) {
        return "heic".equals(extension) ? "jpg" : extension;
    }

    private String safeFilename(MultipartFile file) {
        String filename = file == null ? null : file.getOriginalFilename();
        return filename == null || filename.isBlank() ? "<unnamed>" : filename;
    }

    private record CommandResult(int exitCode, String output) {
    }
}
