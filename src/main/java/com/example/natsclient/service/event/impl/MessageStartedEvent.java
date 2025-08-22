package com.example.natsclient.service.event.impl;

import com.example.natsclient.service.event.NatsMessageEvent;

import java.util.Map;

/**
 * Event fired when message processing starts.
 */
public class MessageStartedEvent extends NatsMessageEvent {
    
    private final Object payload;
    private final String correlationId;
    
    public MessageStartedEvent(String eventId, String requestId, String subject, 
                              String operationType, Object payload, String correlationId,
                              Map<String, Object> metadata) {
        super(eventId, requestId, subject, operationType, metadata);
        this.payload = payload;
        this.correlationId = correlationId;
    }
    
    public Object getPayload() { return payload; }
    public String getCorrelationId() { return correlationId; }
    
    @Override
    public String getEventType() {
        return "MESSAGE_STARTED";
    }
    
    @Override
    public String getDescription() {
        return String.format("Started %s processing for subject %s", getOperationType(), getSubject());
    }
}