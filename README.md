# FileOperationsXXX PDF Tools

Spring Boot application for converting supported files to PDF, compressing PDFs, and merging multiple PDFs.

## Project layout

- `convertx-app`: Spring Boot web application for FileOperationsXXX
- `pdf-compression`: extracted Ghostscript-based PDF compression subproject

## Requirements

- Java 25
- Maven 3.9+
- Linux server with these commands available on the `PATH`
- LibreOffice installed and `soffice` available on the `PATH` for `docx`, `xlsx`, and `pptx`
- Ghostscript installed and `gs` available on the `PATH` for PDF compression
- HEIC support installed through ImageMagick (`magick` or `convert`) or `libheif` (`heif-convert`)

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
export JAVA_HOME=/path/to/jdk-25
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
which magick || which convert || which heif-convert
which soffice
which gs
```

OpenAPI docs:

- Swagger UI: `http://localhost:8081/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8081/api/docs`

## Docker

Build the image from the project root:

```bash
docker build -t fileoperationsxxx .
```

Run it locally:

```bash
docker run --rm -p 8081:8081 fileoperationsxxx
```

Run it with an explicit platform port:

```bash
docker run --rm -e PORT=8081 -p 8081:8081 fileoperationsxxx
```

The container image includes the native Linux dependencies required by the application:

- LibreOffice for `docx`, `xlsx`, and `pptx`
- Ghostscript for PDF compression
- ImageMagick and `libheif` tools for HEIC conversion and rotation

## Linux deployment

Use this checklist before deploying the project to a Linux server.

1. Install the required native packages.

- `soffice` is required for `docx`, `xlsx`, and `pptx`
- `gs` is required for PDF compression
- HEIC conversion requires one of:
  - ImageMagick via `magick` or `convert`
  - `libheif` via `heif-convert`
- HEIC rotation requires ImageMagick with HEIC delegate support

On Debian or Ubuntu, a typical install looks like:

```bash
sudo apt-get update
sudo apt-get install -y libreoffice ghostscript imagemagick libheif-examples
```

2. Use the `PORT` environment variable when the server platform assigns ports dynamically.

- The application now reads `server.port` from `PORT` and falls back to `8081`.
- This works for systemd services, VMs, containers, and platforms like Cloud Run.

3. Verify the Linux runtime before launch.

```bash
java -version
mvn -version
which soffice
which gs
which magick || which convert || which heif-convert
```

4. Package and run the application.

```bash
mvn test
mvn -pl convertx-app -am package
java -jar convertx-app/target/convertx-app-0.0.1-SNAPSHOT.jar
```

5. For systemd-managed Linux servers, set `PORT`, `JAVA_HOME`, and `PATH` explicitly in the unit file if they are not already present in the service environment.

6. Size the server with enough memory for LibreOffice and Ghostscript, especially for large Office files and large PDF merge/compression requests.

7. If the server is container-based, prefer baking the native binaries into the image instead of installing them at container start.

8. The current file-based logs are configured in [logback-spring.xml](/Users/vinitkumar/Documents/AllFiles/convertx-app/src/main/resources/logback-spring.xml). Keep console logging enabled if your Linux environment ships logs through journald or a container runtime.

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
- `heic` conversion uses ImageMagick (`magick` or `convert`) or `heif-convert`.
- `heic` rotation uses ImageMagick and therefore requires HEIC delegate support in the installed ImageMagick package.
- Image formats currently convert the first image frame/page into a single-page PDF.
- PDF compression uses Ghostscript profiles that roughly target 25%, 50%, or 75% size classes; exact output size depends on the source PDF, and the original PDF is returned if recompression would make it larger.
- PDF merge preserves the input order of the uploaded files.
