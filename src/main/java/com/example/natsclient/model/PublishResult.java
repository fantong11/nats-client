package com.example.natsclient.model;

import java.time.Instant;

/**
 * Sealed interface representing the result of a NATS publish operation.
 * Uses sealed interface for type safety and pattern matching support.
 */
public sealed interface PublishResult permits PublishResult.Success, PublishResult.Failure {
    
    /**
     * Returns the request ID associated with this publish operation.
     */
    String requestId();
    
    /**
     * Represents a successful publish operation.
     */
    record Success(
        String requestId,
        long sequence,
        String subject,
        Instant timestamp
    ) implements PublishResult {
        
        public Success(String requestId, long sequence, String subject) {
            this(requestId, sequence, subject, Instant.now());
        }
    }
    
    /**
     * Represents a failed publish operation.
     */
    record Failure(
        String requestId,
        String subject,
        String errorMessage,
        String errorType,
        Instant timestamp
    ) implements PublishResult {
        
        public Failure(String requestId, String subject, String errorMessage, String errorType) {
            this(requestId, subject, errorMessage, errorType, Instant.now());
        }
    }
}