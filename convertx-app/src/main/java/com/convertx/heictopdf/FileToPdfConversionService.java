package com.convertx.heictopdf;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileToPdfConversionService {

    byte[] convert(MultipartFile file);

    byte[] compressPdf(MultipartFile file, int targetPercentage);

    byte[] mergePdfs(List<MultipartFile> files);
}
