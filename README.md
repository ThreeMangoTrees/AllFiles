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

## Before launching to GCP

Use this checklist before deploying the project to Google Cloud.

1. Replace or disable the current HEIC implementation.

- The current HEIC conversion and rotation flow depends on the macOS `sips` command.
- GCP services such as Cloud Run and Compute Engine Linux VMs do not provide `sips`.
- Before launching to GCP, either:
  - replace `sips` with a Linux-compatible approach such as ImageMagick or `libheif`, or
  - temporarily disable HEIC conversion and rotation in the deployed build

2. Make the application listen on the GCP-provided port.

- For Cloud Run, the application must listen on the `PORT` environment variable.
- Update [application.properties](/Users/vinitkumar/Documents/ConvertX/convertx-app/src/main/resources/application.properties) so:

```properties
server.port=${PORT:8081}
```

3. Prepare a Linux runtime with the required native tools.

- `soffice` is required for `docx`, `xlsx`, and `pptx`
- `gs` is required for PDF compression
- Your Docker image or VM startup process must install:
  - LibreOffice
  - Ghostscript

4. Prefer a container-based deployment.

- Because this app depends on native binaries, deploy it as a custom container rather than as source-only Java code.
- For GCP, Cloud Run is the recommended target once the HEIC dependency is made Linux-compatible.

5. Adjust logging for GCP operations.

- Local file logging works for local development, but Cloud Run instances are ephemeral.
- For GCP, keep console logging enabled so logs are collected by Cloud Logging.
- The current file-based logs are configured in [logback-spring.xml](/Users/vinitkumar/Documents/ConvertX/convertx-app/src/main/resources/logback-spring.xml).

6. Verify memory and request settings.

- LibreOffice and Ghostscript can use significant memory during conversion and compression.
- Choose a Cloud Run or VM size that gives enough headroom for:
  - large uploads
  - Office file conversion
  - PDF merge/compression

7. Build and test locally before deployment.

```bash
mvn test
mvn -pl convertx-app -am package
```

8. If deploying to Cloud Run, be ready with GCP services enabled.

- `run.googleapis.com`
- `cloudbuild.googleapis.com`
- `artifactregistry.googleapis.com`

9. Recommended next implementation steps before deployment.

- Add a `Dockerfile`
- Change `server.port` to use `PORT`
- Replace or disable `sips`-based HEIC handling
- Decide whether to keep file logging, console logging, or both for GCP
- Run an end-to-end test in a Linux environment before public launch

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
