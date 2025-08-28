package com.example.natsclient.service.impl;

import com.example.natsclient.config.NatsProperties;
import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.exception.NatsRequestException;
import com.example.natsclient.service.PayloadProcessor;
import com.example.natsclient.service.RequestLogService;
import com.example.natsclient.service.event.impl.MessageCompletedEvent;
import com.example.natsclient.service.event.impl.MessageFailedEvent;
import com.example.natsclient.service.event.impl.MessageStartedEvent;
import com.example.natsclient.service.factory.MetricsFactory;
import com.example.natsclient.service.observer.NatsEventPublisher;
import com.example.natsclient.service.validator.RequestValidator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract base class implementing Template Method pattern for NATS message processing.
 * 
 * This class defines the common workflow template:
 * 1. Initialize request (ID, timing, MDC)
 * 2. Validate input parameters
 * 3. Increment request metrics
 * 4. Execute specific processing logic (implemented by subclasses)
 * 5. Handle success/error cases
 * 6. Cleanup resources
 */
public abstract class AbstractNatsMessageProcessor<T> {
    
    private static final Logger logger = LoggerFactory.getLogger(AbstractNatsMessageProcessor.class);
    
    // Dependencies
    protected final RequestLogService requestLogService;
    protected final PayloadProcessor payloadProcessor;
    protected final RequestValidator requestValidator;
    protected final NatsProperties natsProperties;
    protected final NatsEventPublisher eventPublisher;
    
    // Metrics
    protected final Counter requestCounter;
    protected final Counter successCounter;
    protected final Counter errorCounter;
    protected final Timer requestTimer;
    
    protected AbstractNatsMessageProcessor(
            RequestLogService requestLogService,
            PayloadProcessor payloadProcessor,
            RequestValidator requestValidator,
            NatsProperties natsProperties,
            MeterRegistry meterRegistry,
            MetricsFactory metricsFactory,
            NatsEventPublisher eventPublisher,
            String operationType) {
        
        this.requestLogService = requestLogService;
        this.payloadProcessor = payloadProcessor;
        this.requestValidator = requestValidator;
        this.natsProperties = natsProperties;
        this.eventPublisher = eventPublisher;
        
        // Use MetricsFactory to create metrics with consistent naming and fallback handling
        MetricsFactory.NatsMetricsSet metricsSet = metricsFactory.createNatsMetricsSet(
                operationType, meterRegistry);
        
        this.requestCounter = metricsSet.getRequestCounter();
        this.successCounter = metricsSet.getSuccessCounter();
        this.errorCounter = metricsSet.getErrorCounter();
        this.requestTimer = metricsSet.getRequestTimer();
    }
    
    /**
     * Template method with pre-generated request ID.
     */
    public final CompletableFuture<T> processMessage(String requestId, String subject, Object payload) {
        String eventId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();
        
        // Step 2: Setup structured logging context
        setupMDC(requestId, subject, getOperationType());
        
        try {
            // Step 3: Validate input parameters
            validateInput(subject, payload);
            
            // Step 4: Increment request metrics
            requestCounter.increment();
            
            // Step 5: Publish start event
            publishStartEvent(eventId, requestId, subject, payload);
            
            // Step 6: Log processing start
            logger.info("Starting {} processing", getOperationType());
            
            // Step 7: Execute specific processing logic and wrap with event publishing
            return executeSpecificProcessing(requestId, subject, payload, startTime)
                    .whenComplete((result, throwable) -> {
                        Duration processingTime = Duration.between(startTime, Instant.now());
                        
                        if (throwable != null) {
                            publishFailedEvent(eventId, requestId, subject, throwable, processingTime, 1);
                        } else {
                            publishCompletedEvent(eventId, requestId, subject, result, processingTime);
                        }
                    });
                    
        } catch (Exception e) {
            // Step 8: Handle synchronous errors
            errorCounter.increment();
            Duration processingTime = Duration.between(startTime, Instant.now());
            publishFailedEvent(eventId, requestId, subject, e, processingTime, 1);
            return handleSynchronousError(requestId, subject, e, startTime);
        } finally {
            // Step 9: Cleanup MDC context
            cleanupMDC();
        }
    }
    
    // Template method steps - some with default implementations, others abstract
    
    /**
     * Setup MDC context for structured logging.
     */
    protected void setupMDC(String requestId, String subject, String operationType) {
        MDC.put("requestId", requestId);
        MDC.put("subject", subject);
        MDC.put("operation", operationType);
    }
    
    /**
     * Validate input parameters.
     */
    protected void validateInput(String subject, Object payload) {
        requestValidator.validateRequest(subject, payload);
    }
    
    /**
     * Cleanup MDC context.
     */
    protected void cleanupMDC() {
        MDC.clear();
    }
    
    /**
     * Handle synchronous errors that occur before async processing starts.
     */
    protected CompletableFuture<T> handleSynchronousError(String requestId, String subject, Exception e, Instant startTime) {
        String errorMessage = String.format("Error in %s processing: %s", getOperationType(), e.getMessage());
        
        requestLogService.updateWithError(requestId, errorMessage);
        
        long duration = Duration.between(startTime, Instant.now()).toMillis();
        logger.error("{} processing failed after {}ms", getOperationType(), duration, e);
        
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(new NatsRequestException(errorMessage, requestId, e));
        return future;
    }
    
    // Abstract methods that subclasses must implement
    
    /**
     * Execute the specific processing logic for this type of operation.
     * This is the main variation point in the template method.
     */
    protected abstract CompletableFuture<T> executeSpecificProcessing(
            String requestId, String subject, Object payload, Instant startTime);
    
    /**
     * Get the operation type for logging and metrics.
     */
    protected abstract String getOperationType();
    
    
    // Event publishing helper methods for Observer Pattern
    
    /**
     * Publishes a message started event.
     */
    protected void publishStartEvent(String eventId, String requestId, String subject, Object payload) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("timestamp", Instant.now());
            
            MessageStartedEvent event = new MessageStartedEvent(
                    eventId, requestId, subject, getOperationType(), payload, metadata);
            
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            logger.warn("Failed to publish start event: {}", e.getMessage());
        }
    }
    
    /**
     * Publishes a message completed event.
     */
    protected void publishCompletedEvent(String eventId, String requestId, String subject, Object result, Duration processingTime) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("processingTime", processingTime.toMillis());
            metadata.put("timestamp", Instant.now());
            
            MessageCompletedEvent event = new MessageCompletedEvent(
                    eventId, requestId, subject, getOperationType(), result, processingTime, metadata);
            
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            logger.warn("Failed to publish completed event: {}", e.getMessage());
        }
    }
    
    /**
     * Publishes a message failed event.
     */
    protected void publishFailedEvent(String eventId, String requestId, String subject, Throwable throwable, Duration processingTime, int attemptNumber) {
        try {
            Exception exception = (throwable instanceof Exception) ? (Exception) throwable : new Exception(throwable);
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("processingTime", processingTime.toMillis());
            metadata.put("timestamp", Instant.now());
            metadata.put("exceptionType", exception.getClass().getSimpleName());
            
            MessageFailedEvent event = new MessageFailedEvent(
                    eventId, requestId, subject, getOperationType(), exception, processingTime, attemptNumber, metadata);
            
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            logger.warn("Failed to publish failed event: {}", e.getMessage());
        }
    }
    
}