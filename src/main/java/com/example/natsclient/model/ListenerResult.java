package com.example.natsclient.model;

import java.time.Instant;

/**
 * Sealed interface representing the result of a NATS message listener operation.
 */
public sealed interface ListenerResult permits ListenerResult.MessageReceived, ListenerResult.ListenerError {
    
    /**
     * Returns the subject that was being listened to.
     */
    String subject();
    
    /**
     * Returns the timestamp when this result was created.
     */
    Instant timestamp();
    
    /**
     * Represents a successfully received message.
     */
    record MessageReceived(
        String subject,
        String messageId,
        String extractedId,
        String jsonPayload,
        Instant timestamp,
        long sequence
    ) implements ListenerResult {
        
        public MessageReceived(String subject, String messageId, String extractedId, String jsonPayload, long sequence) {
            this(subject, messageId, extractedId, jsonPayload, Instant.now(), sequence);
        }
    }
    
    /**
     * Represents an error during message listening.
     */
    record ListenerError(
        String subject,
        String errorMessage,
        String errorType,
        Instant timestamp
    ) implements ListenerResult {
        
        public ListenerError(String subject, String errorMessage, String errorType) {
            this(subject, errorMessage, errorType, Instant.now());
        }
    }
}