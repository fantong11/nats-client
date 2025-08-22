package com.example.natsclient.service.observer.impl;

import com.example.natsclient.service.event.NatsMessageEvent;
import com.example.natsclient.service.event.impl.MessageCompletedEvent;
import com.example.natsclient.service.event.impl.MessageFailedEvent;
import com.example.natsclient.service.observer.NatsMessageEventObserver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Observer that collects metrics from NATS message processing events.
 * Provides detailed metrics for monitoring and alerting.
 */
@Component
public class MetricsEventObserver implements NatsMessageEventObserver {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsEventObserver.class);
    
    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, Counter> eventCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> processingTimers = new ConcurrentHashMap<>();
    
    public MetricsEventObserver(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    @Override
    public void onEvent(NatsMessageEvent event) {
        try {
            // Count all event types
            getEventCounter(event.getEventType(), event.getOperationType()).increment();
            
            // Record processing times for completed and failed events
            if (event instanceof MessageCompletedEvent) {
                MessageCompletedEvent completedEvent = (MessageCompletedEvent) event;
                getProcessingTimer(event.getOperationType(), "completed")
                        .record(completedEvent.getProcessingTime());
                        
            } else if (event instanceof MessageFailedEvent) {
                MessageFailedEvent failedEvent = (MessageFailedEvent) event;
                getProcessingTimer(event.getOperationType(), "failed")
                        .record(failedEvent.getProcessingTime());
            }
            
            // Record subject-specific metrics
            getSubjectCounter(event.getSubject(), event.getEventType()).increment();
            
        } catch (Exception e) {
            logger.error("Error recording metrics for event {}: {}", event.getEventType(), e.getMessage());
        }
    }
    
    @Override
    public String getObserverName() {
        return "MetricsEventObserver";
    }
    
    @Override
    public boolean isInterestedIn(String eventType) {
        // We're interested in all events for comprehensive metrics
        return true;
    }
    
    @Override
    public void onRegistered() {
        logger.info("📈 Metrics event observer registered - will collect detailed processing metrics");
    }
    
    @Override
    public void onUnregistered() {
        logger.info("📈 Metrics event observer unregistered");
    }
    
    /**
     * Gets or creates a counter for event types.
     */
    private Counter getEventCounter(String eventType, String operationType) {
        String key = "event." + eventType.toLowerCase() + "." + operationType.toLowerCase();
        return eventCounters.computeIfAbsent(key, k -> 
                Counter.builder("nats.events")
                      .description("Count of NATS message processing events")
                      .tag("event.type", eventType)
                      .tag("operation.type", operationType)
                      .register(meterRegistry));
    }
    
    /**
     * Gets or creates a timer for processing durations.
     */
    private Timer getProcessingTimer(String operationType, String result) {
        String key = "processing." + operationType.toLowerCase() + "." + result;
        return processingTimers.computeIfAbsent(key, k ->
                Timer.builder("nats.processing.duration")
                    .description("Duration of NATS message processing")
                    .tag("operation.type", operationType)
                    .tag("result", result)
                    .register(meterRegistry));
    }
    
    /**
     * Gets or creates a counter for subject-specific events.
     */
    private Counter getSubjectCounter(String subject, String eventType) {
        String key = "subject." + subject + "." + eventType.toLowerCase();
        return eventCounters.computeIfAbsent(key, k ->
                Counter.builder("nats.subject.events")
                      .description("Count of NATS events by subject")
                      .tag("subject", subject)
                      .tag("event.type", eventType)
                      .register(meterRegistry));
    }
}