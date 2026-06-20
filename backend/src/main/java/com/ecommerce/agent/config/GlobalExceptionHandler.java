package com.ecommerce.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.concurrent.CompletionException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CompletionException.class)
    public ResponseEntity<Map<String, Object>> handleCompletionException(CompletionException ex) {
        Throwable cause = ex.getCause();
        String message = cause != null ? cause.getMessage() : ex.getMessage();
        log.error("Async execution error: {}", message, cause);

        if (message != null && (message.contains("401") || message.contains("403") || message.contains("auth"))) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "success", false, "error", "API_KEY_NOT_CONFIGURED",
                "message", "API Key not configured. Please set DEEPSEEK_API_KEY in application-secrets.yml."
            ));
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
            "success", false, "error", "AI_SERVICE_ERROR",
            "message", message != null ? message : "AI service call failed"
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
            "success", false, "error", "BAD_REQUEST",
            "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(java.util.concurrent.TimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleTimeout(java.util.concurrent.TimeoutException ex) {
        log.error("Request timeout: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(Map.of(
            "success", false, "error", "TIMEOUT",
            "message", "Request timed out. Please try again later."
        ));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        log.error("Runtime exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
            "success", false, "error", "INTERNAL_ERROR",
            "message", ex.getMessage() != null ? ex.getMessage() : "Server internal error"
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
            "success", false, "error", "UNKNOWN_ERROR",
            "message", ex.getMessage() != null ? ex.getMessage() : "Unknown error"
        ));
    }
}
