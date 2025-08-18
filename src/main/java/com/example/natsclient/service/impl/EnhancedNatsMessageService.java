package com.example.natsclient.service.impl;

import com.example.natsclient.config.NatsProperties;
import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.exception.NatsRequestException;
import com.example.natsclient.exception.NatsTimeoutException;
import com.example.natsclient.service.NatsMessageService;
import com.example.natsclient.service.PayloadProcessor;
import com.example.natsclient.service.RequestLogService;
import com.example.natsclient.service.validator.RequestValidator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.nats.client.Connection;
import io.nats.client.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Enhanced NATS Message Service with improved error handling, metrics, and logging.
 * 
 * Features:
 * - Structured logging with MDC
 * - Metrics collection with Micrometer
 * - Retry mechanism for resilience
 * - Better exception handling
 * - Performance monitoring
 */
@Service
@Primary
public class EnhancedNatsMessageService implements NatsMessageService {
    
    private static final Logger logger = LoggerFactory.getLogger(EnhancedNatsMessageService.class);
    
    // Metrics
    private final Counter requestCounter;
    private final Counter successCounter;
    private final Counter errorCounter;
    private final Timer requestTimer;
    
    private final Connection natsConnection;
    private final RequestLogService requestLogService;
    private final PayloadProcessor payloadProcessor;
    private final RequestValidator requestValidator;
    private final NatsProperties natsProperties;
    
    @Autowired
    public EnhancedNatsMessageService(
            Connection natsConnection,
            RequestLogService requestLogService,
            PayloadProcessor payloadProcessor,
            RequestValidator requestValidator,
            NatsProperties natsProperties,
            MeterRegistry meterRegistry) {
        this.natsConnection = natsConnection;
        this.requestLogService = requestLogService;
        this.payloadProcessor = payloadProcessor;
        this.requestValidator = requestValidator;
        this.natsProperties = natsProperties;
        
        // Initialize metrics
        this.requestCounter = Counter.builder("nats.requests.total")
                .description("Total number of NATS requests")
                .register(meterRegistry);
        this.successCounter = Counter.builder("nats.requests.success")
                .description("Number of successful NATS requests")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("nats.requests.error")
                .description("Number of failed NATS requests")
                .register(meterRegistry);
        this.requestTimer = Timer.builder("nats.request.duration")
                .description("NATS request duration")
                .register(meterRegistry);
    }
    
    @Override
    @Async
    @Retryable(
        value = {NatsRequestException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public CompletableFuture<String> sendRequest(String subject, Object requestPayload, String correlationId) {
        String requestId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();
        
        // Set up MDC for structured logging
        MDC.put("requestId", requestId);
        MDC.put("subject", subject);
        MDC.put("correlationId", correlationId);
        
        try {
            requestCounter.increment();
            
            // Input validation
            requestValidator.validateRequest(subject, requestPayload);
            requestValidator.validateCorrelationId(correlationId);
            
            logger.info("Starting NATS request processing");
            
            return processRequest(requestId, subject, requestPayload, correlationId, startTime);
                    
        } catch (Exception e) {
            errorCounter.increment();
            return handleRequestError(requestId, subject, e, startTime);
        } finally {
            MDC.clear();
        }
    }
    
    private CompletableFuture<String> processRequest(String requestId, String subject, 
            Object requestPayload, String correlationId, Instant startTime) {
        
        try {
            String jsonPayload = payloadProcessor.serialize(requestPayload);
            
            NatsRequestLog requestLog = requestLogService.createRequestLog(requestId, subject, jsonPayload, correlationId);
            requestLogService.saveRequestLog(requestLog);
            
            logger.debug("Request logged to database, sending to NATS");
            
            Message response = sendNatsRequest(subject, jsonPayload);
            
            if (response != null) {
                return handleSuccessfulResponse(requestId, response, startTime);
            } else {
                return handleTimeoutResponse(requestId, startTime);
            }
            
        } catch (Exception e) {
            return handleRequestError(requestId, subject, e, startTime);
        }
    }
    
    private Message sendNatsRequest(String subject, String jsonPayload) throws Exception {
        byte[] payloadBytes = payloadProcessor.toBytes(jsonPayload);
        Duration timeout = Duration.ofMillis(natsProperties.getRequest().getTimeout());
        
        logger.debug("Sending NATS request with timeout: {}ms", timeout.toMillis());
        
        return natsConnection.request(subject, payloadBytes, timeout);
    }
    
    private CompletableFuture<String> handleSuccessfulResponse(String requestId, Message response, Instant startTime) {
        try {
            String responsePayload = payloadProcessor.fromBytes(response.getData());
            
            requestLogService.updateWithSuccess(requestId, responsePayload);
            successCounter.increment();
            
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            logger.info("NATS request completed successfully in {}ms, response length: {}", 
                       duration, responsePayload.length());
            
            return CompletableFuture.completedFuture(responsePayload);
            
        } catch (Exception e) {
            logger.error("Error processing successful response", e);
            return handleRequestError(requestId, "response_processing", e, startTime);
        }
    }
    
    private CompletableFuture<String> handleTimeoutResponse(String requestId, Instant startTime) {
        String errorMessage = "No response received within timeout period";
        
        requestLogService.updateWithTimeout(requestId, errorMessage);
        errorCounter.increment();
        
        long duration = Duration.between(startTime, Instant.now()).toMillis();
        logger.warn("NATS request timed out after {}ms", duration);
        
        CompletableFuture<String> future = new CompletableFuture<>();
        future.completeExceptionally(new NatsTimeoutException(errorMessage, requestId));
        return future;
    }
    
    private CompletableFuture<String> handleRequestError(String requestId, String context, Exception e, Instant startTime) {
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
    @Async
    public CompletableFuture<Void> publishMessage(String subject, Object messagePayload) {
        String requestId = UUID.randomUUID().toString();
        Instant startTime = Instant.now();
        
        MDC.put("requestId", requestId);
        MDC.put("subject", subject);
        MDC.put("operation", "publish");
        
        try {
            requestValidator.validateRequest(subject, messagePayload);
            
            String jsonPayload = payloadProcessor.serialize(messagePayload);
            byte[] payloadBytes = payloadProcessor.toBytes(jsonPayload);
            
            natsConnection.publish(subject, payloadBytes);
            
            NatsRequestLog requestLog = requestLogService.createRequestLog(requestId, subject, jsonPayload, null);
            requestLog.setStatus(NatsRequestLog.RequestStatus.SUCCESS);
            requestLogService.saveRequestLog(requestLog);
            
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            logger.info("Message published successfully in {}ms", duration);
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            long duration = Duration.between(startTime, Instant.now()).toMillis();
            logger.error("Failed to publish message after {}ms", duration, e);
            
            requestLogService.updateWithError(requestId, "Error publishing message: " + e.getMessage());
            
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(new NatsRequestException("Failed to publish NATS message", requestId, e));
            return future;
        } finally {
            MDC.clear();
        }
    }
}