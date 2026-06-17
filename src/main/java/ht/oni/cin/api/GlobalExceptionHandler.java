package ht.oni.cin.api;

import ht.oni.cin.application.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SessionService.SessionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSessionNotFound(SessionService.SessionNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(SessionService.SessionExpiredException.class)
    public ResponseEntity<Map<String, Object>> handleSessionExpired(SessionService.SessionExpiredException e) {
        return error(HttpStatus.GONE, "SESSION_EXPIRED", e.getMessage());
    }

    @ExceptionHandler(SessionService.SessionLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleSessionLimit(SessionService.SessionLimitExceededException e) {
        return error(HttpStatus.TOO_MANY_REQUESTS, "SESSION_LIMIT_EXCEEDED", e.getMessage());
    }

    @ExceptionHandler(IdentiteService.DuplicateNinException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateNin(IdentiteService.DuplicateNinException e) {
        return error(HttpStatus.CONFLICT, "DUPLICATE_NIN", e.getMessage());
    }

    @ExceptionHandler(ExportService.ExportDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleExportDenied(ExportService.ExportDeniedException e) {
        return error(HttpStatus.FORBIDDEN, "EXPORT_DENIED", e.getMessage());
    }

    @ExceptionHandler(ScanService.IdempotencyException.class)
    public ResponseEntity<Map<String, Object>> handleIdempotency(ScanService.IdempotencyException e) {
        return error(HttpStatus.CONFLICT, "IDEMPOTENCY_VIOLATION", e.getMessage());
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "error", code,
                "message", message,
                "timestamp", Instant.now().toString()
        ));
    }
}
