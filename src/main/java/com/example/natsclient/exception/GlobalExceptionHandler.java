package com.example.natsclient.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NatsClientException.class)
    public ResponseEntity<Map<String, Object>> handleNatsClientException(NatsClientException ex) {
        logger.error("NATS client error - RequestID: {}, Subject: {}, Type: {}", 
                    ex.getRequestId(), ex.getSubject(), ex.getErrorType(), ex);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("error", "NATS Client Error");
        errorResponse.put("message", ex.getMessage());
        errorResponse.put("requestId", ex.getRequestId());
        errorResponse.put("subject", ex.getSubject());
        errorResponse.put("errorType", ex.getErrorType());

        HttpStatus status = mapErrorTypeToHttpStatus(ex.getErrorType());
        return ResponseEntity.status(status).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        logger.error("Validation error", ex);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("error", "Validation Error");
        errorResponse.put("message", "Invalid request parameters");
        
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            fieldErrors.put(error.getField(), error.getDefaultMessage())
        );
        errorResponse.put("fieldErrors", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        logger.error("Unexpected error", ex);

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("error", "Internal Server Error");
        errorResponse.put("message", "An unexpected error occurred");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    private HttpStatus mapErrorTypeToHttpStatus(NatsClientException.ErrorType errorType) {
        return switch (errorType) {
            case BAD_REQUEST, VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case TIMEOUT, NO_RESPONSE -> HttpStatus.REQUEST_TIMEOUT;
            case CONNECTION_ERROR -> HttpStatus.SERVICE_UNAVAILABLE;
            case SERIALIZATION_ERROR -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}