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
import com.example.natsclient.util.NatsMessageHeaders;
import io.micrometer.core.instrument.MeterRegistry;
import io.nats.client.JetStream;
import io.nats.client.PublishOptions;
import io.nats.client.api.PublishAck;
import io.nats.client.impl.Headers;
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
    private final NatsPublishOptionsBuilder publishOptionsBuilder;
    
    public NatsPublishProcessor(
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
              natsProperties, meterRegistry, metricsFactory, eventPublisher, "jetstream_publish");
        this.jetStream = jetStream;
        this.publishOptionsBuilder = publishOptionsBuilder;
    }
    
    @Override
    protected CompletableFuture<Void> executeSpecificProcessing(
            String requestId, String subject, Object payload, String correlationId, Instant startTime) {
        
        try {
            // Serialize payload
            String jsonPayload = payloadProcessor.serialize(payload);
            
            // Publish to JetStream with correlation ID as message ID
            PublishAck publishAck = publishToJetStream(subject, jsonPayload, correlationId);
            
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
     * Publish message to JetStream using Builder pattern for PublishOptions.
     */
    private PublishAck publishToJetStream(String subject, String jsonPayload, String correlationId) throws Exception {
        logger.debug("Publishing message to JetStream with subject: {}", subject);
        
        // Use correlation ID as message ID for deduplication
        String messageId = correlationId != null ? correlationId : NatsMessageHeaders.generateMessageId("pub");
        
        // Create headers with message ID
        Headers headers = NatsMessageHeaders.createHeadersWithMessageId(messageId);
        
        // Use Builder pattern for flexible PublishOptions configuration
        PublishOptions publishOptions = publishOptionsBuilder.createDefault();
        
        PublishAck publishAck = jetStream.publish(subject, headers, payloadProcessor.toBytes(jsonPayload), publishOptions);
        
        logger.debug("JetStream message published with ID '{}' - {}", 
                    messageId, publishOptionsBuilder.formatPublishAck(publishAck));
        
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