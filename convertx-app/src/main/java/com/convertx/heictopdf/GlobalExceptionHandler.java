package com.convertx.heictopdf;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = status.is4xxClientError()
                ? messageOrFallback(ex.getReason(), "The request could not be completed.")
                : "Something went wrong while processing the file. Check the application logs and try again.";
        logAtLevel(status, request, message, ex);
        return plainText(status, message);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<String> handleBadRequest(Exception ex, HttpServletRequest request) {
        String message = "The request is invalid. Check the selected files and options, then try again.";
        log.warn("Request validation failed for {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return plainText(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        String message = "The uploaded file is too large. Choose a file under the configured upload limit and try again.";
        log.warn("Upload exceeded size limit for {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return plainText(HttpStatus.PAYLOAD_TOO_LARGE, message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleUnexpectedException(Exception ex, HttpServletRequest request) {
        String message = "Something went wrong while processing the file. Check the application logs and try again.";
        log.error("Unexpected failure for {} {}", request.getMethod(), request.getRequestURI(), ex);
        return plainText(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    private void logAtLevel(HttpStatus status, HttpServletRequest request, String message, Exception ex) {
        if (status.is4xxClientError()) {
            log.warn("Request failed for {} {}: {}", request.getMethod(), request.getRequestURI(), message);
            return;
        }
        log.error("Request failed for {} {}: {}", request.getMethod(), request.getRequestURI(), message, ex);
    }

    private ResponseEntity<String> plainText(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_PLAIN)
                .body(message);
    }

    private String messageOrFallback(String message, String fallback) {
        return message == null || message.isBlank() ? fallback : message;
    }
}
