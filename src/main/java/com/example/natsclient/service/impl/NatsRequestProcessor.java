package com.example.natsclient.service.impl;

import com.example.natsclient.config.NatsProperties;
import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.exception.NatsRequestException;
import com.example.natsclient.service.PayloadProcessor;
import com.example.natsclient.service.RequestLogService;
import com.example.natsclient.service.builder.NatsPublishOptionsBuilder;
import com.example.natsclient.service.factory.MetricsFactory;
import com.example.natsclient.service.observer.NatsEventPublisher;
import com.example.natsclient.service.validator.RequestValidator;
import io.micrometer.core.instrument.MeterRegistry;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.PublishOptions;
import io.nats.client.api.PublishAck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Concrete processor for NATS request-response operations using JetStream.
 * Implements the Template Method pattern for request processing.
 */
public class NatsRequestProcessor extends AbstractNatsMessageProcessor<String> {
    
    private static final Logger logger = LoggerFactory.getLogger(NatsRequestProcessor.class);
    
    private final JetStream jetStream;
    private final NatsPublishOptionsBuilder publishOptionsBuilder;
    
    public NatsRequestProcessor(
            JetStream jetStream,
            RequestLogService requestLogService,
            PayloadProcessor payloadProcessor,
            RequestValidator requestValidator,
            NatsProperties natsProperties,
            MeterRegistry meterRegistry,
            MetricsFactory metricsFactory,
            NatsPublishOptionsBuilder publishOptionsBuilder,
            NatsEventPublisher eventPublisher) {
        
        super(requestLogService, payloadProcessor, requestValidator, 
              natsProperties, meterRegistry, metricsFactory, eventPublisher, "jetstream_request");
        this.jetStream = jetStream;
        this.publishOptionsBuilder = publishOptionsBuilder;
    }
    
    @Override
    protected CompletableFuture<String> executeSpecificProcessing(
            String requestId, String subject, Object payload, String correlationId, Instant startTime) {
        
        try {
            // Serialize payload
            String jsonPayload = payloadProcessor.serialize(payload);
            
            // Create and save request log
            NatsRequestLog requestLog = requestLogService.createRequestLog(requestId, subject, jsonPayload, correlationId);
            requestLogService.saveRequestLog(requestLog);
            
            logger.debug("Request logged to database, sending to JetStream");
            
            // Send to JetStream
            Message response = sendJetStreamRequest(subject, jsonPayload);
            
            if (response != null) {
                return handleSynchronousResponse(requestId, response, startTime);
            } else {
                // JetStream async processing - message published successfully
                return handleAsyncJetStreamProcessing(requestId, startTime);
            }
            
        } catch (Exception e) {
            return handleAsyncError(requestId, subject, e, startTime);
        }
    }
    
    /**
     * Send request to JetStream for reliable, durable processing using Builder pattern.
     */
    private Message sendJetStreamRequest(String subject, String jsonPayload) throws Exception {
        byte[] payloadBytes = payloadProcessor.toBytes(jsonPayload);
        
        logger.debug("Publishing request message to JetStream with subject: {}", subject);
        
        // Use Builder pattern for critical request operations
        PublishOptions publishOptions = publishOptionsBuilder.createCritical();
        
        PublishAck publishAck = jetStream.publish(subject, payloadBytes, publishOptions);
        logger.debug("JetStream request message published - {}", 
                    publishOptionsBuilder.formatPublishAck(publishAck));
        
        // In JetStream architecture, responses come asynchronously through consumers
        // Return null to indicate async processing
        logger.info("Request message published to JetStream successfully - transitioning to async processing model");
        return null;
    }
    
    /**
     * Handle synchronous response (for backward compatibility).
     */
    private CompletableFuture<String> handleSynchronousResponse(String requestId, Message response, Instant startTime) {
        try {
            String responsePayload = payloadProcessor.fromBytes(response.getData());
            
            requestLogService.updateWithSuccess(requestId, responsePayload);
            successCounter.increment();
            
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            logger.info("NATS request completed successfully in {}ms, response length: {}", 
                       duration, responsePayload.length());
            
            return CompletableFuture.completedFuture(responsePayload);
            
        } catch (Exception e) {
            logger.error("Error processing synchronous response", e);
            return handleAsyncError(requestId, "response_processing", e, startTime);
        }
    }
    
    /**
     * Handle JetStream async processing success.
     */
    private CompletableFuture<String> handleAsyncJetStreamProcessing(String requestId, Instant startTime) {
        String successMessage = "Message published to JetStream successfully - processing asynchronously";
        
        requestLogService.updateWithSuccess(requestId, successMessage);
        successCounter.increment();
        
        long duration = Duration.between(startTime, Instant.now()).toMillis();
        logger.info("JetStream request message published successfully in {}ms - async processing initiated", duration);
        
        return CompletableFuture.completedFuture(successMessage);
    }
    
    /**
     * Handle async processing errors.
     */
    private CompletableFuture<String> handleAsyncError(String requestId, String context, Exception e, Instant startTime) {
        String errorMessage = String.format("Error in %s: %s", context, e.getMessage());
        
        requestLogService.updateWithError(requestId, errorMessage);
        errorCounter.increment();
        
        long duration = Duration.between(startTime, Instant.now()).toMillis();
        logger.error("NATS request failed after {}ms in context: {}", duration, context, e);
        
        CompletableFuture<String> future = new CompletableFuture<>();
        future.completeExceptionally(new NatsRequestException(errorMessage, requestId, e));
        return future;
    }
    
    @Override
    protected String getOperationType() {
        return "jetstream_request";
    }
    
    @Override
    protected boolean requiresCorrelationIdValidation() {
        return true; // Request operations require correlation ID validation
    }
}