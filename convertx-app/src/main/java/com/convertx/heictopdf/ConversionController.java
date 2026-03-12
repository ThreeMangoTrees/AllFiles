package com.convertx.heictopdf;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
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

    private final FileToPdfConversionService conversionService;

    public ConversionController(FileToPdfConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @GetMapping("/api/health")
    @Operation(summary = "Health check", description = "Simple endpoint to confirm the service is running.")
    public String health() {
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
        byte[] pdfBytes = conversionService.convert(file);
        String baseName = extractBaseName(file.getOriginalFilename());

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
        byte[] pdfBytes = conversionService.compressPdf(file, targetPercentage);
        String baseName = extractBaseName(file.getOriginalFilename());
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
        byte[] pdfBytes = conversionService.mergePdfs(files);
        return pdfAttachment(pdfBytes, "merged.pdf");
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
