package com.convertx.heictopdf;

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
public class ConversionController {

    private final FileToPdfConversionService conversionService;

    public ConversionController(FileToPdfConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @GetMapping("/api/health")
    public String health() {
        return "ok";
    }

    @PostMapping(path = {"/api/convert/to-pdf", "/api/convert/heic-to-pdf"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> convert(@RequestParam("file") @NotNull MultipartFile file) {
        byte[] pdfBytes = conversionService.convert(file);
        String baseName = extractBaseName(file.getOriginalFilename());

        return pdfAttachment(pdfBytes, baseName + ".pdf");
    }

    @PostMapping(path = "/api/pdf/compress", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> compress(
            @RequestParam("file") @NotNull MultipartFile file,
            @RequestParam("targetPercentage") int targetPercentage
    ) {
        byte[] pdfBytes = conversionService.compressPdf(file, targetPercentage);
        String baseName = extractBaseName(file.getOriginalFilename());
        return pdfAttachment(pdfBytes, baseName + "-compressed-" + targetPercentage + ".pdf");
    }

    @PostMapping(path = "/api/pdf/merge", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
