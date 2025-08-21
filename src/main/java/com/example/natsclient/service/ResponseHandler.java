package com.example.natsclient.service;

import io.nats.client.Message;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for handling NATS response processing.
 * 
 * This abstraction follows the Single Responsibility Principle by separating
 * response handling logic from the main messaging service.
 * 
 * @param <T> The type of response to be handled
 */
public interface ResponseHandler<T> {
    
    /**
     * Handles a successful NATS response.
     * 
     * @param requestId The unique request identifier
     * @param response The NATS response message
     * @return CompletableFuture containing the processed response
     */
    CompletableFuture<T> handleSuccess(String requestId, Message response);
    
    /**
     * Handles a timeout scenario when no response is received.
     * 
     * @param requestId The unique request identifier
     * @return CompletableFuture containing the timeout response
     */
    CompletableFuture<T> handleTimeout(String requestId);
    
    /**
     * Handles error scenarios during request processing.
     * 
     * @param requestId The unique request identifier
     * @param error The exception that occurred
     * @return CompletableFuture containing the error response
     */
    CompletableFuture<T> handleError(String requestId, Throwable error);
}