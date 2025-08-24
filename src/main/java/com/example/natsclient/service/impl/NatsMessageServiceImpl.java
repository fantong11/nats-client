package com.example.natsclient.service.impl;

import com.example.natsclient.config.NatsProperties;
import com.example.natsclient.dto.NatsRequestLogDto;
import com.example.natsclient.exception.NatsRequestException;
import com.example.natsclient.exception.NatsTimeoutException;
import com.example.natsclient.service.NatsMessageService;
import com.example.natsclient.service.NatsOperations;
import com.example.natsclient.service.PayloadProcessor;
import com.example.natsclient.service.RequestLogService;
import com.example.natsclient.service.ResponseHandler;
import com.example.natsclient.service.validator.RequestValidator;
import io.nats.client.Message;
import io.nats.client.api.PublishAck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * SOLID-compliant implementation of NATS messaging service.
 * 
 * This implementation follows SOLID principles:
 * - SRP: Each dependency has a single responsibility
 * - OCP: Open for extension via ResponseHandler and NatsOperations interfaces
 * - LSP: Can be substituted by any NatsMessageService implementation
 * - ISP: Depends on focused interfaces rather than large ones
 * - DIP: Depends on abstractions (NatsOperations, ResponseHandler) not concrete classes
 * 
 * @author NATS Client Team
 * @version 2.0
 * @since 2025-08-22
 */
@Service
public class NatsMessageServiceImpl implements NatsMessageService {
    
    private static final Logger logger = LoggerFactory.getLogger(NatsMessageServiceImpl.class);
    
    private final NatsOperations natsOperations;
    private final ResponseHandler<String> responseHandler;
    private final RequestLogService requestLogService;
    private final PayloadProcessor payloadProcessor;
    private final RequestValidator requestValidator;
    private final NatsProperties natsProperties;
    
    @Autowired
    public NatsMessageServiceImpl(
            NatsOperations natsOperations,
            ResponseHandler<String> responseHandler,
            RequestLogService requestLogService,
            PayloadProcessor payloadProcessor,
            RequestValidator requestValidator,
            NatsProperties natsProperties) {
        this.natsOperations = natsOperations;
        this.responseHandler = responseHandler;
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
            
            NatsRequestLogDto requestLog = requestLogService.createRequestLog(requestId, subject, jsonPayload, correlationId);
            requestLogService.saveRequestLog(requestLog);
            
            logger.info("Sending NATS request - ID: {}, Subject: {}, Correlation: {}", 
                       requestId, subject, correlationId);
            
            byte[] payloadBytes = payloadProcessor.toBytes(jsonPayload);
            Duration timeout = Duration.ofMillis(natsProperties.getRequest().getTimeout());
            Message response = natsOperations.sendRequest(subject, payloadBytes, timeout);
            
            if (response != null) {
                return responseHandler.handleSuccess(requestId, response);
            } else {
                return responseHandler.handleTimeout(requestId);
            }
            
        } catch (NatsTimeoutException e) {
            // Re-throw timeout exceptions directly without wrapping
            throw e;
        } catch (Exception e) {
            return responseHandler.handleError(requestId, e);
        }
    }
    
    /**
     * Publishes a message to JetStream with acknowledgment.
     * 
     * This method provides reliable messaging with JetStream:
     * 1. Validates the input parameters
     * 2. Serializes the payload to JSON
     * 3. Publishes the message to JetStream with ACK
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
            byte[] payloadBytes = payloadProcessor.toBytes(jsonPayload);
            
            // Use NatsOperations abstraction for publishing
            CompletableFuture<PublishAck> publishFuture = natsOperations.publishMessage(subject, payloadBytes);
            
            return publishFuture.thenApply(publishAck -> {
                NatsRequestLogDto requestLog = requestLogService.createRequestLog(requestId, subject, jsonPayload, null);
                requestLog.setStatus(NatsRequestLogDto.RequestStatus.SUCCESS);
                requestLog.setResponsePayload("JetStream Publish ACK - Sequence: " + publishAck.getSeqno() + 
                                            ", Stream: " + publishAck.getStream());
                requestLogService.saveRequestLog(requestLog);
                
                logger.info("Published JetStream message - ID: {}, Subject: {}, Sequence: {}", 
                           requestId, subject, publishAck.getSeqno());
                
                return null; // CompletableFuture<Void>
            });
            
        } catch (Exception e) {
            logger.error("Error publishing JetStream message - ID: {}", requestId, e);
            requestLogService.updateWithError(requestId, "Error publishing message: " + e.getMessage());
            throw new NatsRequestException("Failed to publish JetStream message", requestId, e);
        }
    }
    
}