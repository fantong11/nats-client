package com.example.natsclient.service;

import io.nats.client.Message;
import io.nats.client.api.PublishAck;
import java.time.Duration;
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
     * Sends a request and waits for a response.
     * 
     * @param subject The NATS subject
     * @param payload The message payload as bytes
     * @param timeout The request timeout
     * @return The response message, or null if timeout
     * @throws Exception if the request fails
     */
    Message sendRequest(String subject, byte[] payload, Duration timeout) throws Exception;
    
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