package com.example.natsclient.service;

import io.nats.client.api.PublishAck;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for NATS operations abstraction.
 * 
 * This interface follows the Dependency Inversion Principle by abstracting
 * the concrete NATS implementations using JetStream.
 * 
 * All NATS communication in this application uses JetStream for durability and reliability.
 */
public interface NatsOperations {
    
    /**
     * Publishes a message reliably.
     * 
     * @param subject The NATS subject
     * @param payload The message payload as bytes
     * @return CompletableFuture containing the publish acknowledgment
     * @throws Exception if the publish fails
     */
    CompletableFuture<PublishAck> publishMessage(String subject, byte[] payload) throws Exception;
}