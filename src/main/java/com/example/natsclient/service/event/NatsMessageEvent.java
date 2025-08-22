package com.example.natsclient.service.event;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Base class for NATS message processing events.
 * Part of Observer Pattern implementation for event notification system.
 */
public abstract class NatsMessageEvent {
    
    private final String eventId;
    private final String requestId;
    private final String subject;
    private final LocalDateTime timestamp;
    private final String operationType;
    private final Map<String, Object> metadata;
    
    protected NatsMessageEvent(String eventId, String requestId, String subject, 
                              String operationType, Map<String, Object> metadata) {
        this.eventId = eventId;
        this.requestId = requestId;
        this.subject = subject;
        this.operationType = operationType;
        this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        this.timestamp = LocalDateTime.now();
    }
    
    public String getEventId() { return eventId; }
    public String getRequestId() { return requestId; }
    public String getSubject() { return subject; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getOperationType() { return operationType; }
    public Map<String, Object> getMetadata() { return metadata; }
    
    /**
     * Gets the event type for routing and filtering purposes.
     */
    public abstract String getEventType();
    
    /**
     * Gets a human-readable description of the event.
     */
    public abstract String getDescription();
    
    @Override
    public String toString() {
        return String.format("%s{eventId='%s', requestId='%s', subject='%s', timestamp=%s}", 
                           getEventType(), eventId, requestId, subject, timestamp);
    }
}