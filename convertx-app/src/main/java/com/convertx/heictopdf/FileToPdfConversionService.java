package com.convertx.heictopdf;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileToPdfConversionService {

    record ProcessedFile(byte[] bytes, String filename, String contentType) {
    }

    byte[] convert(MultipartFile file);

    byte[] compressPdf(MultipartFile file, int targetPercentage);

    byte[] mergePdfs(List<MultipartFile> files);

    byte[] convertMergeAndOptionallyCompress(List<MultipartFile> files, int targetPercentage);

    ProcessedFile rotateFile(MultipartFile file, int anticlockwiseDegrees);
}
