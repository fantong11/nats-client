package com.example.natsclient.service.event.impl;

import com.example.natsclient.service.event.NatsMessageEvent;

import java.time.Duration;
import java.util.Map;

/**
 * Event fired when message processing completes successfully.
 */
public class MessageCompletedEvent extends NatsMessageEvent {
    
    private final Object result;
    private final Duration processingTime;
    
    public MessageCompletedEvent(String eventId, String requestId, String subject, 
                                String operationType, Object result, Duration processingTime,
                                Map<String, Object> metadata) {
        super(eventId, requestId, subject, operationType, metadata);
        this.result = result;
        this.processingTime = processingTime;
    }
    
    public Object getResult() { return result; }
    public Duration getProcessingTime() { return processingTime; }
    
    @Override
    public String getEventType() {
        return "MESSAGE_COMPLETED";
    }
    
    @Override
    public String getDescription() {
        return String.format("Completed %s processing for subject %s in %dms", 
                           getOperationType(), getSubject(), processingTime.toMillis());
    }
}