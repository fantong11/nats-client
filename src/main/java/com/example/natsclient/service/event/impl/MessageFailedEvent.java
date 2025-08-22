package com.example.natsclient.service.event.impl;

import com.example.natsclient.service.event.NatsMessageEvent;

import java.time.Duration;
import java.util.Map;

/**
 * Event fired when message processing fails.
 */
public class MessageFailedEvent extends NatsMessageEvent {
    
    private final Exception exception;
    private final Duration processingTime;
    private final int attemptNumber;
    
    public MessageFailedEvent(String eventId, String requestId, String subject, 
                             String operationType, Exception exception, Duration processingTime,
                             int attemptNumber, Map<String, Object> metadata) {
        super(eventId, requestId, subject, operationType, metadata);
        this.exception = exception;
        this.processingTime = processingTime;
        this.attemptNumber = attemptNumber;
    }
    
    public Exception getException() { return exception; }
    public Duration getProcessingTime() { return processingTime; }
    public int getAttemptNumber() { return attemptNumber; }
    
    @Override
    public String getEventType() {
        return "MESSAGE_FAILED";
    }
    
    @Override
    public String getDescription() {
        return String.format("Failed %s processing for subject %s (attempt %d) in %dms: %s", 
                           getOperationType(), getSubject(), attemptNumber, 
                           processingTime.toMillis(), exception.getMessage());
    }
}