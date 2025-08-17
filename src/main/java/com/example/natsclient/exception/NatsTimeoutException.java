package com.example.natsclient.exception;

public class NatsTimeoutException extends NatsRequestException {
    
    public NatsTimeoutException(String message, String requestId) {
        super(message, requestId);
    }
    
    public NatsTimeoutException(String message, String requestId, Throwable cause) {
        super(message, requestId, cause);
    }
}