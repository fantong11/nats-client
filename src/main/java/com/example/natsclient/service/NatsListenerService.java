package com.example.natsclient.service;

import com.example.natsclient.model.ListenerResult;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Service interface for NATS message listening operations.
 * Supports JetStream-based message consumption with ID extraction from JSON payloads.
 */
public interface NatsListenerService {
    
    /**
     * Start listening to a specific subject.
     * 
     * @param subject The NATS subject to listen to
     * @param idFieldName The JSON field name to extract as ID (e.g., "id", "userId", "orderId")
     * @param messageHandler Callback function to handle received messages
     * @return CompletableFuture that completes when listener is started
     */
    CompletableFuture<String> startListener(String subject, String idFieldName, Consumer<ListenerResult.MessageReceived> messageHandler);
    
    /**
     * Stop listening to a specific subject.
     * 
     * @param listenerId The listener ID returned from startListener
     * @return CompletableFuture that completes when listener is stopped
     */
    CompletableFuture<Void> stopListener(String listenerId);
    
    /**
     * Stop all active listeners.
     * 
     * @return CompletableFuture that completes when all listeners are stopped
     */
    CompletableFuture<Void> stopAllListeners();
    
    /**
     * Get the status of all active listeners.
     * 
     * @return CompletableFuture containing listener status information
     */
    CompletableFuture<java.util.List<ListenerStatus>> getListenerStatus();
    
    /**
     * Check if a listener is active for a specific subject.
     * 
     * @param subject The subject to check
     * @return true if listener is active, false otherwise
     */
    boolean isListenerActive(String subject);
    
    /**
     * Data class representing listener status information.
     */
    record ListenerStatus(
        String listenerId,
        String subject,
        String idFieldName,
        String status,
        long messagesReceived,
        java.time.Instant startTime,
        java.time.Instant lastMessageTime
    ) {}
}