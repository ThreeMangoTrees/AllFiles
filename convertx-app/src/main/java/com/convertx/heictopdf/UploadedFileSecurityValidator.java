package com.convertx.heictopdf;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharsetDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class UploadedFileSecurityValidator {

    private static final Set<String> HEIC_BRANDS = Set.of("heic", "heix", "hevc", "hevx", "mif1", "msf1");
    private final ApplicationSecurityProperties properties;

    public UploadedFileSecurityValidator(ApplicationSecurityProperties properties) {
        this.properties = properties;
    }

    public void validate(MultipartFile file, String extension) {
        byte[] bytes = readBytes(file);
        if (!matchesExpectedContent(bytes, extension)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The uploaded file content does not match its extension: " + safeFilename(file)
            );
        }
        scanForMalwareIfEnabled(bytes, extension);
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read uploaded file.", ex);
        }
    }

    private boolean matchesExpectedContent(byte[] bytes, String extension) {
        return switch (extension) {
            case "pdf" -> startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII));
            case "jpg", "jpeg" -> bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
            case "gif" -> startsWith(bytes, "GIF87a".getBytes(StandardCharsets.US_ASCII)) || startsWith(bytes, "GIF89a".getBytes(StandardCharsets.US_ASCII));
            case "bmp" -> startsWith(bytes, "BM".getBytes(StandardCharsets.US_ASCII));
            case "tiff" -> startsWith(bytes, new byte[]{0x49, 0x49, 0x2A, 0x00}) || startsWith(bytes, new byte[]{0x4D, 0x4D, 0x00, 0x2A});
            case "heic" -> isHeic(bytes);
            case "docx" -> zipContains(bytes, List.of("word/"));
            case "xlsx" -> zipContains(bytes, List.of("xl/"));
            case "pptx" -> zipContains(bytes, List.of("ppt/"));
            case "rtf" -> startsWith(bytes, "{\\rtf".getBytes(StandardCharsets.US_ASCII));
            case "html", "htm" -> looksLikeHtml(bytes);
            case "txt" -> looksLikeText(bytes);
            default -> false;
        };
    }

    private boolean zipContains(byte[] bytes, List<String> requiredPrefixes) {
        if (!isZip(bytes)) {
            return false;
        }
        try (ZipInputStream inputStream = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = inputStream.getNextEntry()) != null) {
                String name = entry.getName();
                for (String prefix : requiredPrefixes) {
                    if (name.startsWith(prefix)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (IOException ex) {
            return false;
        }
    }

    private boolean isZip(byte[] bytes) {
        return bytes.length >= 4
                && bytes[0] == 'P'
                && bytes[1] == 'K'
                && (bytes[2] == 0x03 || bytes[2] == 0x05 || bytes[2] == 0x07)
                && (bytes[3] == 0x04 || bytes[3] == 0x06 || bytes[3] == 0x08);
    }

    private boolean isHeic(byte[] bytes) {
        if (bytes.length < 12 || bytes[4] != 'f' || bytes[5] != 't' || bytes[6] != 'y' || bytes[7] != 'p') {
            return false;
        }
        String brand = new String(bytes, 8, 4, StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
        return HEIC_BRANDS.contains(brand);
    }

    private boolean looksLikeHtml(byte[] bytes) {
        if (!looksLikeText(bytes)) {
            return false;
        }
        String content = new String(bytes, 0, Math.min(bytes.length, 4096), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        return content.contains("<html") || content.contains("<!doctype html") || content.contains("<body");
    }

    private boolean looksLikeText(byte[] bytes) {
        if (bytes.length == 0) {
            return true;
        }
        for (byte current : bytes) {
            if (current == 0) {
                return false;
            }
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException ex) {
            return false;
        }
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (bytes[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private void scanForMalwareIfEnabled(byte[] bytes, String extension) {
        if (!properties.getAntivirus().isEnabled()) {
            return;
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("upload-scan-", "." + extension);
            Files.write(tempFile, bytes);
            Process process = new ProcessBuilder(properties.getAntivirus().getCommand(), "--no-summary", tempFile.toString())
                    .redirectErrorStream(true)
                    .start();
            int exitCode = process.waitFor();
            if (exitCode == 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file failed malware scanning.");
            }
            if (exitCode != 0) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Malware scanning could not be completed.");
            }
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Malware scanning could not be started.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Malware scanning was interrupted.", ex);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private String safeFilename(MultipartFile file) {
        String filename = file == null ? null : file.getOriginalFilename();
        return filename == null || filename.isBlank() ? "<unnamed>" : filename;
    }
}
