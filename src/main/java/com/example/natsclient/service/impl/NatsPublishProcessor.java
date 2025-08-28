package com.example.natsclient.service.impl;

import com.example.natsclient.config.NatsProperties;
import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.exception.NatsRequestException;
import com.example.natsclient.model.PublishResult;
import com.example.natsclient.service.PayloadProcessor;
import com.example.natsclient.service.RequestLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.natsclient.util.NatsMessageUtils;
import com.example.natsclient.service.factory.MetricsFactory;
import com.example.natsclient.service.observer.NatsEventPublisher;
import com.example.natsclient.service.validator.RequestValidator;
import com.example.natsclient.util.NatsMessageHeaders;
import io.micrometer.core.instrument.MeterRegistry;
import io.nats.client.JetStream;
import io.nats.client.PublishOptions;
import io.nats.client.api.PublishAck;
import io.nats.client.impl.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Concrete processor for NATS publish operations using JetStream.
 * Implements the Template Method pattern for message publishing.
 */
public class NatsPublishProcessor extends AbstractNatsMessageProcessor<PublishResult> {
    
    private static final Logger logger = LoggerFactory.getLogger(NatsPublishProcessor.class);
    
    private final JetStream jetStream;
    private final NatsMessageUtils messageUtils;
    private final ObjectMapper objectMapper;
    
    public NatsPublishProcessor(
            JetStream jetStream,
            RequestLogService requestLogService,
            PayloadProcessor payloadProcessor,
            RequestValidator requestValidator,
            NatsProperties natsProperties,
            MeterRegistry meterRegistry,
            MetricsFactory metricsFactory,
            NatsMessageUtils messageUtils,
            NatsEventPublisher eventPublisher,
            ObjectMapper objectMapper) {
        
        super(requestLogService, payloadProcessor, requestValidator, 
              natsProperties, meterRegistry, metricsFactory, eventPublisher, "jetstream_publish");
        this.jetStream = jetStream;
        this.messageUtils = messageUtils;
        this.objectMapper = objectMapper;
    }
    
    @Override
    protected CompletableFuture<PublishResult> executeSpecificProcessing(
            String requestId, String subject, Object payload, Instant startTime) {
        
        try {
            // Serialize payload
            String jsonPayload = objectMapper.writeValueAsString(payload);
            
            // Publish to JetStream with request ID as message ID
            PublishAck publishAck = publishToJetStream(subject, jsonPayload, requestId);
            
            if (publishAck == null) {
                throw new RuntimeException("JetStream publish acknowledgment is null - message may not have been persisted. " +
                        "Check if stream exists for subject: " + subject);
            }
            
            // Create and save request log with success status
            NatsRequestLog requestLog = createSuccessfulRequestLog(requestId, subject, jsonPayload, publishAck);
            requestLogService.saveRequestLog(requestLog);
            
            // Update success metrics
            successCounter.increment();
            
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            logger.info("JetStream message published successfully in {}ms - Sequence: {}", 
                       duration, publishAck.getSeqno());
            
            PublishResult.Success result = new PublishResult.Success(
                requestId, publishAck.getSeqno(), subject
            );
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            return handlePublishError(requestId, subject, e, startTime);
        }
    }
    
    /**
     * Publish message to JetStream using Builder pattern for PublishOptions.
     */
    private PublishAck publishToJetStream(String subject, String jsonPayload, String requestId) throws Exception {
        logger.debug("Publishing message to JetStream with subject: {}", subject);
        
        // Use request ID as message ID for deduplication
        String messageId = requestId;
        
        // Create headers with message ID
        Headers headers = NatsMessageHeaders.createHeadersWithMessageId(messageId);
        
        // Publish without specifying stream - let JetStream route based on subject  
        PublishAck publishAck = jetStream.publish(subject, headers, jsonPayload.getBytes(StandardCharsets.UTF_8));
        
        if (publishAck == null) {
            throw new RuntimeException("JetStream publish returned null acknowledgment for subject: " + subject + 
                    ". Possible causes: stream not found, JetStream not enabled, or connection issues");
        }
        
        logger.debug("JetStream message published with ID '{}' - {}", 
                    messageId, messageUtils.formatPublishAck(publishAck));
        
        return publishAck;
    }
    
    /**
     * Create a successful request log entry with publish acknowledgment details.
     */
    private NatsRequestLog createSuccessfulRequestLog(String requestId, String subject, String jsonPayload, PublishAck publishAck) {
        NatsRequestLog requestLog = requestLogService.createRequestLog(requestId, subject, jsonPayload);
        requestLog.setStatus(NatsRequestLog.RequestStatus.SUCCESS);
        requestLog.setResponsePayload("JetStream Publish ACK - Sequence: " + publishAck.getSeqno() + 
                                    ", Stream: " + publishAck.getStream());
        return requestLog;
    }
    
    /**
     * Handle publish operation errors.
     */
    private CompletableFuture<PublishResult> handlePublishError(String requestId, String subject, Exception e, Instant startTime) {
        String errorMessage = "Error publishing JetStream message: " + e.getMessage();
        
        requestLogService.updateWithError(requestId, errorMessage);
        errorCounter.increment();
        
        long duration = Duration.between(startTime, Instant.now()).toMillis();
        logger.error("Failed to publish JetStream message after {}ms", duration, e);
        
        PublishResult.Failure result = new PublishResult.Failure(
            requestId, subject, errorMessage, e.getClass().getSimpleName()
        );
        return CompletableFuture.completedFuture(result);
    }
    
    @Override
    protected String getOperationType() {
        return "jetstream_publish";
    }
    
}