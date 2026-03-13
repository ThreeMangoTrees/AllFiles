package com.convertx.heictopdf;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Validated
@RestController
@RequestMapping
@Tag(name = "PDF Tools", description = "Convert, compress, and merge PDF files")
public class ConversionController {

    private static final Logger log = LoggerFactory.getLogger(ConversionController.class);
    private final FileToPdfConversionService conversionService;

    public ConversionController(FileToPdfConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @GetMapping("/api/health")
    @Operation(summary = "Health check", description = "Simple endpoint to confirm the service is running.")
    public String health() {
        log.debug("Health check requested.");
        return "ok";
    }

    @PostMapping(path = {"/api/convert/to-pdf", "/api/convert/heic-to-pdf"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Convert a supported file to PDF",
            description = "Accepts docx, xlsx, pptx, jpg, jpeg, tiff, gif, bmp, txt, rtf, html, htm, and heic files and returns a PDF.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "PDF generated"),
                    @ApiResponse(responseCode = "400", description = "Unsupported or invalid input")
            }
    )
    public ResponseEntity<byte[]> convert(@RequestParam("file") @NotNull MultipartFile file) {
        log.info("Convert requested for file {}", safeFilename(file));
        byte[] pdfBytes = conversionService.convert(file);
        String baseName = extractBaseName(file.getOriginalFilename());
        log.info("Convert completed for file {}", safeFilename(file));
        return pdfAttachment(pdfBytes, baseName + ".pdf");
    }

    @PostMapping(path = "/api/pdf/compress", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Compress a PDF",
            description = "Compresses a PDF using one of the supported target profiles: 25, 50, or 75.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Compressed PDF returned"),
                    @ApiResponse(responseCode = "400", description = "Invalid PDF or target percentage")
            }
    )
    public ResponseEntity<byte[]> compress(
            @RequestParam("file") @NotNull MultipartFile file,
            @RequestParam("targetPercentage") int targetPercentage
    ) {
        log.info("Compress requested for file {} with target {}", safeFilename(file), targetPercentage);
        byte[] pdfBytes = conversionService.compressPdf(file, targetPercentage);
        String baseName = extractBaseName(file.getOriginalFilename());
        log.info("Compress completed for file {} with target {}", safeFilename(file), targetPercentage);
        return pdfAttachment(pdfBytes, baseName + "-compressed-" + targetPercentage + ".pdf");
    }

    @PostMapping(path = "/api/pdf/merge", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Merge multiple PDFs",
            description = "Merges two or more uploaded PDF files in the order received.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Merged PDF returned"),
                    @ApiResponse(responseCode = "400", description = "Less than two PDFs supplied or invalid input")
            }
    )
    public ResponseEntity<byte[]> merge(@RequestParam("files") List<MultipartFile> files) {
        log.info("Merge requested for {} files", files == null ? 0 : files.size());
        byte[] pdfBytes = conversionService.mergePdfs(files);
        log.info("Merge completed for {} files", files == null ? 0 : files.size());
        return pdfAttachment(pdfBytes, "merged.pdf");
    }

    @PostMapping(path = "/api/pdf/convert-merge-compress", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Convert mixed files to PDF, merge them, and optionally compress",
            description = "Accepts two or more files in original order. Non-PDF files are converted to PDF, all PDFs are merged, and the merged result is compressed when the target is 25, 50, or 75. A target of 100 skips compression.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Combined PDF returned"),
                    @ApiResponse(responseCode = "400", description = "Invalid files or compression target")
            }
    )
    public ResponseEntity<byte[]> convertMergeAndCompress(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("targetPercentage") int targetPercentage
    ) {
        log.info("Combined workflow requested for {} files with target {}", files == null ? 0 : files.size(), targetPercentage);
        byte[] pdfBytes = conversionService.convertMergeAndOptionallyCompress(files, targetPercentage);
        log.info("Combined workflow completed for {} files with target {}", files == null ? 0 : files.size(), targetPercentage);
        return pdfAttachment(pdfBytes, "combined.pdf");
    }

    @PostMapping(path = "/api/pdf/rotate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Rotate a file anti-clockwise",
            description = "Accepts a supported file or PDF, converts non-PDF inputs to PDF, and rotates every page anti-clockwise by 90, 180, or 270 degrees.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Rotated PDF returned"),
                    @ApiResponse(responseCode = "400", description = "Invalid file or rotation angle")
            }
    )
    public ResponseEntity<byte[]> rotate(
            @RequestParam("file") @NotNull MultipartFile file,
            @RequestParam("anticlockwiseDegrees") int anticlockwiseDegrees
    ) {
        log.info("Rotate requested for file {} by {} degrees anti-clockwise", safeFilename(file), anticlockwiseDegrees);
        FileToPdfConversionService.ProcessedFile rotated = conversionService.rotateFile(file, anticlockwiseDegrees);
        log.info("Rotate completed for file {} by {} degrees anti-clockwise", safeFilename(file), anticlockwiseDegrees);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(rotated.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(rotated.filename())
                        .build()
                        .toString())
                .body(rotated.bytes());
    }

    private String safeFilename(MultipartFile file) {
        String filename = file == null ? null : file.getOriginalFilename();
        return filename == null || filename.isBlank() ? "<unnamed>" : filename;
    }

    private String extractBaseName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "converted";
        }

        int extensionIndex = filename.lastIndexOf('.');
        return extensionIndex > 0 ? filename.substring(0, extensionIndex) : filename;
    }

    private ResponseEntity<byte[]> pdfAttachment(byte[] bytes, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename)
                        .build()
                        .toString())
                .body(bytes);
    }
}
