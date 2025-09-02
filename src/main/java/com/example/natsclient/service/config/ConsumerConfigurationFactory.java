package com.example.natsclient.service.config;

import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Factory for creating NATS consumer configurations.
 * Follows Single Responsibility Principle - only responsible for configuration creation.
 */
@Component
public class ConsumerConfigurationFactory {
    
    private static final Duration DEFAULT_ACK_WAIT = Duration.ofSeconds(30);
    private static final int DEFAULT_MAX_DELIVER = 3;
    
    /**
     * Creates a durable consumer configuration for load balancing.
     * 
     * @param subject The subject to create consumer name from
     * @return ConsumerConfiguration for durable consumer
     */
    public ConsumerConfiguration createDurableConsumerConfig(String subject) {
        String durableConsumerName = generateDurableConsumerName(subject);
        
        return ConsumerConfiguration.builder()
            .name(durableConsumerName)
            .durable(durableConsumerName)
            .deliverPolicy(DeliverPolicy.New)
            .ackWait(DEFAULT_ACK_WAIT)
            .maxDeliver(DEFAULT_MAX_DELIVER)
            .build();
    }
    
    /**
     * Generates a standardized durable consumer name from subject.
     * 
     * @param subject The subject name
     * @return Durable consumer name
     */
    public String generateDurableConsumerName(String subject) {
        return "durable-consumer-" + subject.replace(".", "-");
    }
}