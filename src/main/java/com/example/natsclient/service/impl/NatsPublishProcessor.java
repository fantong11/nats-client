package com.example.natsclient.service.impl;

import com.example.natsclient.config.NatsProperties;
import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.exception.NatsRequestException;
import com.example.natsclient.service.PayloadProcessor;
import com.example.natsclient.service.RequestLogService;
import com.example.natsclient.service.validator.RequestValidator;
import io.micrometer.core.instrument.MeterRegistry;
import io.nats.client.JetStream;
import io.nats.client.PublishOptions;
import io.nats.client.api.PublishAck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Concrete processor for NATS publish operations using JetStream.
 * Implements the Template Method pattern for message publishing.
 */
public class NatsPublishProcessor extends AbstractNatsMessageProcessor<Void> {
    
    private static final Logger logger = LoggerFactory.getLogger(NatsPublishProcessor.class);
    
    private final JetStream jetStream;
    
    public NatsPublishProcessor(
            JetStream jetStream,
            RequestLogService requestLogService,
            PayloadProcessor payloadProcessor,
            RequestValidator requestValidator,
            NatsProperties natsProperties,
            MeterRegistry meterRegistry) {
        
        super(requestLogService, payloadProcessor, requestValidator, natsProperties, meterRegistry);
        this.jetStream = jetStream;
    }
    
    @Override
    protected CompletableFuture<Void> executeSpecificProcessing(
            String requestId, String subject, Object payload, String correlationId, Instant startTime) {
        
        try {
            // Serialize payload
            String jsonPayload = payloadProcessor.serialize(payload);
            
            // Publish to JetStream
            PublishAck publishAck = publishToJetStream(subject, jsonPayload);
            
            // Create and save request log with success status
            NatsRequestLog requestLog = createSuccessfulRequestLog(requestId, subject, jsonPayload, publishAck);
            requestLogService.saveRequestLog(requestLog);
            
            // Update success metrics
            successCounter.increment();
            
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            logger.info("JetStream message published successfully in {}ms - Sequence: {}", 
                       duration, publishAck.getSeqno());
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            return handlePublishError(requestId, subject, e, startTime);
        }
    }
    
    /**
     * Publish message to JetStream.
     */
    private PublishAck publishToJetStream(String subject, String jsonPayload) throws Exception {
        logger.debug("Publishing message to JetStream with subject: {}", subject);
        
        // Use JetStream for reliable message publishing
        PublishOptions publishOptions = PublishOptions.builder()
                .expectedStream(natsProperties.getJetStream().getStream().getDefaultName())
                .build();
        
        PublishAck publishAck = jetStream.publish(subject, payloadProcessor.toBytes(jsonPayload), publishOptions);
        
        logger.debug("JetStream message published - Sequence: {}, Stream: {}", 
                    publishAck.getSeqno(), publishAck.getStream());
        
        return publishAck;
    }
    
    /**
     * Create a successful request log entry with publish acknowledgment details.
     */
    private NatsRequestLog createSuccessfulRequestLog(String requestId, String subject, String jsonPayload, PublishAck publishAck) {
        NatsRequestLog requestLog = requestLogService.createRequestLog(requestId, subject, jsonPayload, null);
        requestLog.setStatus(NatsRequestLog.RequestStatus.SUCCESS);
        requestLog.setResponsePayload("JetStream Publish ACK - Sequence: " + publishAck.getSeqno() + 
                                    ", Stream: " + publishAck.getStream());
        return requestLog;
    }
    
    /**
     * Handle publish operation errors.
     */
    private CompletableFuture<Void> handlePublishError(String requestId, String subject, Exception e, Instant startTime) {
        String errorMessage = "Error publishing JetStream message: " + e.getMessage();
        
        requestLogService.updateWithError(requestId, errorMessage);
        errorCounter.increment();
        
        long duration = Duration.between(startTime, Instant.now()).toMillis();
        logger.error("Failed to publish JetStream message after {}ms", duration, e);
        
        CompletableFuture<Void> future = new CompletableFuture<>();
        future.completeExceptionally(new NatsRequestException("Failed to publish JetStream message", requestId, e));
        return future;
    }
    
    @Override
    protected String getOperationType() {
        return "jetstream_publish";
    }
    
    @Override
    protected boolean requiresCorrelationIdValidation() {
        return false; // Publish operations don't require correlation ID validation
    }
}