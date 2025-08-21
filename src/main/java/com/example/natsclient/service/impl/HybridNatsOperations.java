package com.example.natsclient.service.impl;

import com.example.natsclient.config.NatsProperties;
import com.example.natsclient.service.NatsOperations;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.PublishOptions;
import io.nats.client.api.PublishAck;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Hybrid NATS operations implementation.
 * 
 * This implementation uses:
 * - NATS Core for request-reply operations (better performance, standard practice)
 * - JetStream for publish operations (reliability, persistence)
 * 
 * This follows the Single Responsibility Principle by focusing only on NATS operations.
 */
@Component
public class HybridNatsOperations implements NatsOperations {
    
    private final Connection natsConnection;
    private final JetStream jetStream;
    private final NatsProperties natsProperties;
    
    public HybridNatsOperations(Connection natsConnection, 
                               JetStream jetStream, 
                               NatsProperties natsProperties) {
        this.natsConnection = natsConnection;
        this.jetStream = jetStream;
        this.natsProperties = natsProperties;
    }
    
    /**
     * Uses NATS Core for request-reply operations.
     * This is the standard approach for synchronous request-response patterns.
     */
    @Override
    public Message sendRequest(String subject, byte[] payload, Duration timeout) throws Exception {
        return natsConnection.request(subject, payload, timeout);
    }
    
    /**
     * Uses JetStream for publish operations.
     * This provides at-least-once delivery guarantee and persistence.
     */
    @Override
    public CompletableFuture<PublishAck> publishMessage(String subject, byte[] payload) throws Exception {
        PublishOptions options = PublishOptions.builder()
                .expectedStream(natsProperties.getJetStream().getStream().getDefaultName())
                .build();
        
        PublishAck ack = jetStream.publish(subject, payload, options);
        return CompletableFuture.completedFuture(ack);
    }
}