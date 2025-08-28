package com.example.natsclient.service.impl;

import com.example.natsclient.config.NatsProperties;
import com.example.natsclient.exception.NatsRequestException;
import com.example.natsclient.model.PublishResult;
import com.example.natsclient.service.NatsMessageService;
import com.example.natsclient.service.PayloadProcessor;
import com.example.natsclient.service.RequestLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.natsclient.util.NatsMessageUtils;
import com.example.natsclient.service.factory.MetricsFactory;
import com.example.natsclient.service.observer.NatsEventPublisher;
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
    
    private final NatsPublishProcessor publishProcessor;
    
    @Autowired
    public EnhancedNatsMessageService(
            Connection natsConnection,
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
        
        // Initialize publish processor using Template Method, Factory, Builder, and Observer patterns
        this.publishProcessor = new NatsPublishProcessor(
                jetStream, requestLogService, payloadProcessor,
                requestValidator, natsProperties, meterRegistry, metricsFactory, messageUtils, eventPublisher, objectMapper);
    }
    
    @Override
    @Async  
    public CompletableFuture<PublishResult> publishMessage(String requestId, String subject, Object messagePayload) {
        logger.info("Delegating publish processing to specialized processor - RequestID: {}, Subject: {}", requestId, subject);
        return publishProcessor.processMessage(requestId, subject, messagePayload);
    }
}