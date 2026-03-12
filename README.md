# ConvertX PDF Tools

Spring Boot application for converting supported files to PDF, compressing PDFs, and merging multiple PDFs.

## Project layout

- `convertx-app`: Spring Boot web application
- `pdf-compression`: extracted Ghostscript-based PDF compression subproject

## Requirements

- Java 25
- Maven 3.9+
- macOS with the built-in `sips` command available
- LibreOffice installed and `soffice` available on the `PATH` for `docx`, `xlsx`, and `pptx`
- Ghostscript installed and `gs` available on the `PATH` for PDF compression

## Run

```bash
mvn -pl convertx-app -am spring-boot:run
```

Open `http://localhost:8081` to use the web UI for convert, compress, and merge operations.

## Local setup

1. Install JDK 25 and make sure both `java` and `mvn` use it.
2. Verify with:

```bash
java -version
mvn -version
```

3. If Maven is using a different JDK than your shell, export `JAVA_HOME` before running Maven:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
export PATH="$JAVA_HOME/bin:$PATH"
```

4. Build from the project root:

```bash
mvn test
```

5. Run the app module:

```bash
mvn -pl convertx-app -am spring-boot:run
```

6. Confirm required native tools are available:

```bash
which sips
which soffice
which gs
```

OpenAPI docs:

- Swagger UI: `http://localhost:8081/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8081/api/docs`

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
