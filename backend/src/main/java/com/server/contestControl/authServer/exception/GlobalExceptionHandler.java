package com.server.contestControl.authServer.exception;


import com.server.contestControl.authServer.exception.api.base.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.IOException;
import java.util.Locale;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        HttpStatus status = ex.getStatus();
        ErrorResponse errorResponse = ErrorResponse.of(
                status,
                ex.getClass().getSimpleName(),
                ex.getMessage(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(errorResponse, status);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Validation error");
        ErrorResponse response = ErrorResponse.of(
                HttpStatus.BAD_REQUEST,
                "ValidationError",
                message,
                request.getRequestURI()
        );
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<Void> handleAsyncRequestNotUsable(AsyncRequestNotUsableException ex, HttpServletRequest request) {
        log.debug("Async response is no longer usable for {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<?> handleIOException(IOException ex, HttpServletRequest request) {
        if (isClientDisconnect(ex) || isSseRequest(request)) {
            log.debug("Client disconnected from {}: {}", request.getRequestURI(), ex.getMessage());
            return ResponseEntity.noContent().build();
        }

        return handleGeneralException(ex, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneralException(Exception ex, HttpServletRequest request) {
        if (isSseRequest(request) && isClientDisconnect(ex)) {
            log.debug("Client disconnected from {}: {}", request.getRequestURI(), ex.getMessage());
            return ResponseEntity.noContent().build();
        }

        log.error("Unhandled exception for {}", request.getRequestURI(), ex);
        ErrorResponse errorResponse = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "InternalServerError",
                ex.getMessage(),
                request.getRequestURI()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private boolean isSseRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String accept = request.getHeader("Accept");

        return (uri != null && uri.endsWith("/stream"))
                || (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE));
    }

    private boolean isClientDisconnect(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String className = current.getClass().getName();
            String message = current.getMessage();
            if (className.endsWith("ClientAbortException") || containsDisconnectMessage(message)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean containsDisconnectMessage(String message) {
        if (message == null) {
            return false;
        }

        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("broken pipe")
                || normalized.contains("connection reset")
                || normalized.contains("connection aborted");
    }
}
