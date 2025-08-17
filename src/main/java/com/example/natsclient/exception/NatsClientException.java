package com.example.natsclient.exception;

public class NatsClientException extends RuntimeException {
    
    private final String requestId;
    private final String subject;
    private final ErrorType errorType;

    public enum ErrorType {
        CONNECTION_ERROR,
        TIMEOUT,
        SERIALIZATION_ERROR,
        VALIDATION_ERROR,
        NO_RESPONSE,
        BAD_REQUEST,
        UNKNOWN_ERROR
    }

    public NatsClientException(String message, String requestId, String subject, ErrorType errorType) {
        super(message);
        this.requestId = requestId;
        this.subject = subject;
        this.errorType = errorType;
    }

    public NatsClientException(String message, Throwable cause, String requestId, String subject, ErrorType errorType) {
        super(message, cause);
        this.requestId = requestId;
        this.subject = subject;
        this.errorType = errorType;
    }

    public String getRequestId() { return requestId; }
    public String getSubject() { return subject; }
    public ErrorType getErrorType() { return errorType; }
}