# ConvertX PDF Tools

Spring Boot application for converting supported files to PDF, compressing PDFs, and merging multiple PDFs.

## Requirements

- Java 17+
- Maven 3.9+
- macOS with the built-in `sips` command available
- LibreOffice installed and `soffice` available on the `PATH` for `docx`, `xlsx`, and `pptx`
- Ghostscript installed and `gs` available on the `PATH` for PDF compression

## Run

```bash
mvn spring-boot:run
```

Open `http://localhost:8081` to use the web UI for convert, compress, and merge operations.

## API

Convert a supported file to PDF:

```bash
curl -X POST \
  -F "file=@/path/to/file.docx" \
  http://localhost:8081/api/convert/to-pdf \
  --output converted.pdf
```

Compress a PDF with a 25%, 50%, or 75% profile:

```bash
curl -X POST \
  -F "file=@/path/to/file.pdf" \
  -F "targetPercentage=50" \
  http://localhost:8081/api/pdf/compress \
  --output compressed.pdf
```

Merge two or more PDFs:

```bash
curl -X POST \
  -F "files=@/path/to/part-1.pdf" \
  -F "files=@/path/to/part-2.pdf" \
  http://localhost:8081/api/pdf/merge \
  --output merged.pdf
```

## Supported formats

- `docx`, `xlsx`, `pptx`
- `jpg`, `jpeg`, `tiff`, `gif`, `bmp`, `heic`
- `txt`, `rtf`, `html`, `htm`

## Notes

- `docx`, `xlsx`, and `pptx` are converted through LibreOffice in headless mode.
- `heic` is converted with the macOS `sips` command before writing the PDF.
- Image formats currently convert the first image frame/page into a single-page PDF.
- PDF compression uses Ghostscript profiles that roughly target 25%, 50%, or 75% size classes; exact output size depends on the source PDF, and the original PDF is returned if recompression would make it larger.
- PDF merge preserves the input order of the uploaded files.
