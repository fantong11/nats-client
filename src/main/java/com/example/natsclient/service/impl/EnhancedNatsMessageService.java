package com.example.natsclient.service.impl;

import com.example.natsclient.config.NatsProperties;
import com.example.natsclient.exception.NatsRequestException;
import com.example.natsclient.service.NatsMessageService;
import com.example.natsclient.service.PayloadProcessor;
import com.example.natsclient.service.RequestLogService;
import com.example.natsclient.service.validator.RequestValidator;
import io.micrometer.core.instrument.MeterRegistry;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Enhanced NATS Message Service using Template Method pattern for improved maintainability.
 * 
 * Features:
 * - JetStream-based messaging for durability and reliability
 * - Template Method pattern for consistent processing workflow
 * - Delegation to specialized processors
 * - Retry mechanism for resilience
 * - Async processing model with JetStream
 */
@Service
@Primary
public class EnhancedNatsMessageService implements NatsMessageService {
    
    private static final Logger logger = LoggerFactory.getLogger(EnhancedNatsMessageService.class);
    
    private final NatsRequestProcessor requestProcessor;
    private final NatsPublishProcessor publishProcessor;
    
    @Autowired
    public EnhancedNatsMessageService(
            Connection natsConnection,
            JetStream jetStream,
            RequestLogService requestLogService,
            PayloadProcessor payloadProcessor,
            RequestValidator requestValidator,
            NatsProperties natsProperties,
            MeterRegistry meterRegistry) {
        
        // Initialize specialized processors using Template Method pattern
        this.requestProcessor = new NatsRequestProcessor(
                jetStream, requestLogService, payloadProcessor, 
                requestValidator, natsProperties, meterRegistry);
        
        this.publishProcessor = new NatsPublishProcessor(
                jetStream, requestLogService, payloadProcessor,
                requestValidator, natsProperties, meterRegistry);
    }
    
    
    @Override
    @Async
    @Retryable(
        value = {NatsRequestException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public CompletableFuture<String> sendRequest(String subject, Object requestPayload, String correlationId) {
        logger.info("Delegating request processing to specialized processor - Subject: {}", subject);
        return requestProcessor.processMessage(subject, requestPayload, correlationId);
    }
    
    
    @Override
    @Async
    public CompletableFuture<Void> publishMessage(String subject, Object messagePayload) {
        logger.info("Delegating publish processing to specialized processor - Subject: {}", subject);
        return publishProcessor.processMessage(subject, messagePayload, null);
    }
}