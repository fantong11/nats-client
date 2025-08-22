package com.example.natsclient.service.event.impl;

import com.example.natsclient.service.event.NatsMessageEvent;

import java.time.Duration;
import java.util.Map;

/**
 * Event fired when message processing is being retried.
 */
public class MessageRetryEvent extends NatsMessageEvent {
    
    private final Exception lastException;
    private final int attemptNumber;
    private final Duration retryDelay;
    private final String retryStrategy;
    
    public MessageRetryEvent(String eventId, String requestId, String subject, 
                            String operationType, Exception lastException, int attemptNumber,
                            Duration retryDelay, String retryStrategy, Map<String, Object> metadata) {
        super(eventId, requestId, subject, operationType, metadata);
        this.lastException = lastException;
        this.attemptNumber = attemptNumber;
        this.retryDelay = retryDelay;
        this.retryStrategy = retryStrategy;
    }
    
    public Exception getLastException() { return lastException; }
    public int getAttemptNumber() { return attemptNumber; }
    public Duration getRetryDelay() { return retryDelay; }
    public String getRetryStrategy() { return retryStrategy; }
    
    @Override
    public String getEventType() {
        return "MESSAGE_RETRY";
    }
    
    @Override
    public String getDescription() {
        return String.format("Retrying %s processing for subject %s (attempt %d) in %dms using %s strategy", 
                           getOperationType(), getSubject(), attemptNumber, 
                           retryDelay.toMillis(), retryStrategy);
    }
}