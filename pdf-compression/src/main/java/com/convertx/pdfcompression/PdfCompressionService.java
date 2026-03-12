package com.convertx.pdfcompression;

import java.io.IOException;

public interface PdfCompressionService {

    byte[] compress(byte[] originalPdf, int targetPercentage) throws IOException, InterruptedException;
}
