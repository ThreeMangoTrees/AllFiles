FROM maven:3.9.11-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY convertx-app/pom.xml convertx-app/pom.xml
COPY pdf-compression/pom.xml pdf-compression/pom.xml
COPY convertx-app/src convertx-app/src
COPY pdf-compression/src pdf-compression/src

RUN mvn -pl convertx-app -am package -DskipTests

FROM eclipse-temurin:25-jre

ENV DEBIAN_FRONTEND=noninteractive
ENV PORT=8081

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        libreoffice \
        ghostscript \
        imagemagick \
        libheif-examples \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build /workspace/convertx-app/target/convertx-app-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
