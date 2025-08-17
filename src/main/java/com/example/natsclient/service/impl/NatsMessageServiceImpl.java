package com.example.natsclient.service.impl;

import com.example.natsclient.config.NatsProperties;
import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.exception.NatsRequestException;
import com.example.natsclient.exception.NatsTimeoutException;
import com.example.natsclient.service.NatsMessageService;
import com.example.natsclient.service.PayloadProcessor;
import com.example.natsclient.service.RequestLogService;
import com.example.natsclient.service.validator.RequestValidator;
import io.nats.client.Connection;
import io.nats.client.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of NATS messaging service providing request-response and publish operations.
 * 
 * This service handles NATS communication with the following features:
 * - Asynchronous request-response messaging
 * - Fire-and-forget publishing
 * - Comprehensive logging and error handling
 * - Request validation and payload processing
 * - Database logging for audit trails
 * 
 * @author NATS Client Team
 * @version 1.0
 * @since 2025-08-17
 */
@Service
public class NatsMessageServiceImpl implements NatsMessageService {
    
    private static final Logger logger = LoggerFactory.getLogger(NatsMessageServiceImpl.class);
    
    private final Connection natsConnection;
    private final RequestLogService requestLogService;
    private final PayloadProcessor payloadProcessor;
    private final RequestValidator requestValidator;
    private final NatsProperties natsProperties;
    
    @Autowired
    public NatsMessageServiceImpl(
            Connection natsConnection,
            RequestLogService requestLogService,
            PayloadProcessor payloadProcessor,
            RequestValidator requestValidator,
            NatsProperties natsProperties) {
        this.natsConnection = natsConnection;
        this.requestLogService = requestLogService;
        this.payloadProcessor = payloadProcessor;
        this.requestValidator = requestValidator;
        this.natsProperties = natsProperties;
    }
    
    /**
     * Sends an asynchronous request to NATS and waits for a response.
     * 
     * This method performs the following operations:
     * 1. Validates the input parameters
     * 2. Serializes the payload to JSON
     * 3. Logs the request to the database
     * 4. Sends the request to NATS with timeout
     * 5. Processes the response or handles timeout/errors
     * 
     * @param subject The NATS subject to send the request to
     * @param requestPayload The payload object to be serialized and sent
     * @param correlationId Optional correlation ID for request tracking
     * @return CompletableFuture containing the response payload as String
     * @throws NatsRequestException if the request fails
     * @throws NatsTimeoutException if no response is received within timeout
     */
    @Override
    @Async
    public CompletableFuture<String> sendRequest(String subject, Object requestPayload, String correlationId) {
        requestValidator.validateRequest(subject, requestPayload);
        requestValidator.validateCorrelationId(correlationId);
        
        String requestId = UUID.randomUUID().toString();
        
        try {
            String jsonPayload = payloadProcessor.serialize(requestPayload);
            
            NatsRequestLog requestLog = requestLogService.createRequestLog(requestId, subject, jsonPayload, correlationId);
            requestLogService.saveRequestLog(requestLog);
            
            logger.info("Sending NATS request - ID: {}, Subject: {}, Correlation: {}", 
                       requestId, subject, correlationId);
            
            Message response = sendNatsRequest(subject, jsonPayload);
            
            if (response != null) {
                return handleSuccessfulResponse(requestId, response);
            } else {
                return handleTimeoutResponse(requestId);
            }
            
        } catch (Exception e) {
            return handleErrorResponse(requestId, e);
        }
    }
    
    /**
     * Publishes a message to NATS asynchronously without expecting a response.
     * 
     * This method performs fire-and-forget messaging:
     * 1. Validates the input parameters
     * 2. Serializes the payload to JSON
     * 3. Publishes the message to NATS
     * 4. Logs the operation to the database
     * 
     * @param subject The NATS subject to publish the message to
     * @param messagePayload The payload object to be serialized and published
     * @return CompletableFuture<Void> that completes when the message is published
     * @throws NatsRequestException if the publish operation fails
     */
    @Override
    @Async
    public CompletableFuture<Void> publishMessage(String subject, Object messagePayload) {
        requestValidator.validateRequest(subject, messagePayload);
        
        String requestId = UUID.randomUUID().toString();
        
        try {
            String jsonPayload = payloadProcessor.serialize(messagePayload);
            
            natsConnection.publish(subject, payloadProcessor.toBytes(jsonPayload));
            
            NatsRequestLog requestLog = requestLogService.createRequestLog(requestId, subject, jsonPayload, null);
            requestLog.setStatus(NatsRequestLog.RequestStatus.SUCCESS);
            requestLogService.saveRequestLog(requestLog);
            
            logger.info("Published NATS message - ID: {}, Subject: {}", requestId, subject);
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            logger.error("Error publishing NATS message - ID: {}", requestId, e);
            requestLogService.updateWithError(requestId, "Error publishing message: " + e.getMessage());
            throw new NatsRequestException("Failed to publish NATS message", requestId, e);
        }
    }
    
    /**
     * Sends a synchronous request to NATS with configured timeout.
     * 
     * @param subject The NATS subject for the request
     * @param jsonPayload The serialized JSON payload
     * @return Message response from NATS, or null if timeout occurs
     * @throws Exception if NATS communication fails
     */
    private Message sendNatsRequest(String subject, String jsonPayload) throws Exception {
        return natsConnection.request(
            subject,
            payloadProcessor.toBytes(jsonPayload),
            Duration.ofMillis(natsProperties.getRequest().getTimeout())
        );
    }
    
    /**
     * Processes a successful NATS response.
     * 
     * @param requestId The unique request identifier
     * @param response The NATS response message
     * @return CompletableFuture containing the response payload
     */
    private CompletableFuture<String> handleSuccessfulResponse(String requestId, Message response) {
        String responsePayload = payloadProcessor.fromBytes(response.getData());
        
        requestLogService.updateWithSuccess(requestId, responsePayload);
        
        logger.info("Received NATS response - ID: {}, Response length: {}", 
                   requestId, responsePayload.length());
        
        return CompletableFuture.completedFuture(responsePayload);
    }
    
    /**
     * Handles NATS request timeout scenarios.
     * 
     * @param requestId The unique request identifier
     * @return CompletableFuture that throws NatsTimeoutException
     */
    private CompletableFuture<String> handleTimeoutResponse(String requestId) {
        String errorMessage = "No response received within timeout period";
        logger.warn("No response received for request ID: {}", requestId);
        
        requestLogService.updateWithTimeout(requestId, errorMessage);
        
        throw new NatsTimeoutException(errorMessage, requestId);
    }
    
    /**
     * Handles NATS request errors and exceptions.
     * 
     * @param requestId The unique request identifier
     * @param e The exception that occurred
     * @return CompletableFuture that throws NatsRequestException
     */
    private CompletableFuture<String> handleErrorResponse(String requestId, Exception e) {
        logger.error("Error sending NATS request - ID: {}", requestId, e);
        
        requestLogService.updateWithError(requestId, "Error sending request: " + e.getMessage());
        
        throw new NatsRequestException("Failed to send NATS request", requestId, e);
    }
}