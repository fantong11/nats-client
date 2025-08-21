package com.example.natsclient.service.impl;

import com.example.natsclient.exception.NatsClientException;
import com.example.natsclient.exception.NatsRequestException;
import com.example.natsclient.exception.NatsTimeoutException;
import com.example.natsclient.service.PayloadProcessor;
import com.example.natsclient.service.RequestLogService;
import com.example.natsclient.service.ResponseHandler;
import io.nats.client.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * String response handler implementation.
 * 
 * This class follows the Single Responsibility Principle by focusing only on
 * response processing and logging operations.
 */
@Component
public class StringResponseHandler implements ResponseHandler<String> {
    
    private static final Logger logger = LoggerFactory.getLogger(StringResponseHandler.class);
    
    private final RequestLogService requestLogService;
    private final PayloadProcessor payloadProcessor;
    
    public StringResponseHandler(RequestLogService requestLogService, 
                               PayloadProcessor payloadProcessor) {
        this.requestLogService = requestLogService;
        this.payloadProcessor = payloadProcessor;
    }
    
    @Override
    public CompletableFuture<String> handleSuccess(String requestId, Message response) {
        try {
            String responsePayload = payloadProcessor.fromBytes(response.getData());
            requestLogService.updateWithSuccess(requestId, responsePayload);
            
            logger.debug("Successfully processed NATS response for request: {}", requestId);
            return CompletableFuture.completedFuture(responsePayload);
            
        } catch (Exception e) {
            logger.error("Failed to process successful response for request: {}", requestId, e);
            return handleError(requestId, e);
        }
    }
    
    @Override
    public CompletableFuture<String> handleTimeout(String requestId) {
        String errorMessage = "No response received within timeout period";
        requestLogService.updateWithTimeout(requestId, errorMessage);
        
        logger.warn("NATS request timeout for request: {}", requestId);
        
        CompletableFuture<String> future = new CompletableFuture<>();
        future.completeExceptionally(new NatsTimeoutException(errorMessage, requestId));
        return future;
    }
    
    @Override
    public CompletableFuture<String> handleError(String requestId, Throwable error) {
        String errorMessage = "Request failed: " + error.getMessage();
        requestLogService.updateWithError(requestId, errorMessage);
        
        logger.error("NATS request failed for request: {}", requestId, error);
        
        CompletableFuture<String> future = new CompletableFuture<>();
        future.completeExceptionally(new NatsClientException(errorMessage, error, requestId, null, 
                NatsClientException.ErrorType.UNKNOWN_ERROR));
        return future;
    }
}