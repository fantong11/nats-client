package com.example.natsclient.service.impl;

import com.example.natsclient.config.NatsProperties;
import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.exception.NatsRequestException;
import com.example.natsclient.service.PayloadProcessor;
import com.example.natsclient.service.RequestLogService;
import com.example.natsclient.service.validator.RequestValidator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Duration;
import java.time.Instant;
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
            MeterRegistry meterRegistry) {
        
        this.requestLogService = requestLogService;
        this.payloadProcessor = payloadProcessor;
        this.requestValidator = requestValidator;
        this.natsProperties = natsProperties;
        
        // Initialize metrics with fallback handling
        this.requestCounter = createCounter("nats.requests.total", "Total number of NATS requests", meterRegistry);
        this.successCounter = createCounter("nats.requests.success", "Number of successful NATS requests", meterRegistry);
        this.errorCounter = createCounter("nats.requests.error", "Number of failed NATS requests", meterRegistry);
        this.requestTimer = createTimer("nats.request.duration", "NATS request duration", meterRegistry);
    }
    
    /**
     * Template method defining the complete message processing workflow.
     * This method should not be overridden by subclasses.
     */
    public final CompletableFuture<T> processMessage(String subject, Object payload, String correlationId) {
        // Step 1: Initialize request
        String requestId = initializeRequest();
        Instant startTime = Instant.now();
        
        // Step 2: Setup structured logging context
        setupMDC(requestId, subject, correlationId, getOperationType());
        
        try {
            // Step 3: Validate input parameters
            validateInput(subject, payload, correlationId);
            
            // Step 4: Increment request metrics
            requestCounter.increment();
            
            // Step 5: Log processing start
            logger.info("Starting {} processing", getOperationType());
            
            // Step 6: Execute specific processing logic (implemented by subclasses)
            return executeSpecificProcessing(requestId, subject, payload, correlationId, startTime);
                    
        } catch (Exception e) {
            // Step 7: Handle synchronous errors
            errorCounter.increment();
            return handleSynchronousError(requestId, subject, e, startTime);
        } finally {
            // Step 8: Cleanup MDC context
            cleanupMDC();
        }
    }
    
    // Template method steps - some with default implementations, others abstract
    
    /**
     * Initialize request with unique ID.
     */
    protected String initializeRequest() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Setup MDC context for structured logging.
     */
    protected void setupMDC(String requestId, String subject, String correlationId, String operationType) {
        MDC.put("requestId", requestId);
        MDC.put("subject", subject);
        MDC.put("correlationId", correlationId);
        MDC.put("operation", operationType);
    }
    
    /**
     * Validate input parameters.
     */
    protected void validateInput(String subject, Object payload, String correlationId) {
        requestValidator.validateRequest(subject, payload);
        if (requiresCorrelationIdValidation()) {
            requestValidator.validateCorrelationId(correlationId);
        }
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
            String requestId, String subject, Object payload, String correlationId, Instant startTime);
    
    /**
     * Get the operation type for logging and metrics.
     */
    protected abstract String getOperationType();
    
    /**
     * Determine if correlation ID validation is required for this operation type.
     */
    protected abstract boolean requiresCorrelationIdValidation();
    
    // Helper methods for metrics creation
    
    private Counter createCounter(String name, String description, MeterRegistry registry) {
        try {
            return Counter.builder(name)
                    .description(description)
                    .register(registry);
        } catch (Exception e) {
            logger.warn("Failed to register counter {}: {}. Using no-op counter.", name, e.getMessage());
            return Counter.builder(name)
                    .description(description)
                    .register(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        }
    }
    
    private Timer createTimer(String name, String description, MeterRegistry registry) {
        try {
            return Timer.builder(name)
                    .description(description)
                    .register(registry);
        } catch (Exception e) {
            logger.warn("Failed to register timer {}: {}. Using no-op timer.", name, e.getMessage());
            return Timer.builder(name)
                    .description(description)
                    .register(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        }
    }
}