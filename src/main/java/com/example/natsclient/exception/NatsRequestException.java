package com.example.natsclient.exception;

public class NatsRequestException extends RuntimeException {
    
    private final String requestId;
    
    public NatsRequestException(String message, String requestId) {
        super(message);
        this.requestId = requestId;
    }
    
    public NatsRequestException(String message, String requestId, Throwable cause) {
        super(message, cause);
        this.requestId = requestId;
    }
    
    public String getRequestId() {
        return requestId;
    }
}