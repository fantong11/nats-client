package com.example.natsclient.service.config;

import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Factory for creating NATS consumer configurations.
 * Follows Single Responsibility Principle - only responsible for configuration creation.
 *
 * Supports Pull Consumer mode:
 * - Pull Consumer is suitable for scenarios requiring better flow control
 * - Client actively pulls messages, avoiding pressure from passive message reception
 * - Supports batch pulling to improve processing efficiency
 */
@Component
public class ConsumerConfigurationFactory {

    private static final Duration DEFAULT_ACK_WAIT = Duration.ofSeconds(30);
    private static final int DEFAULT_MAX_DELIVER = 3;
    private static final int DEFAULT_MAX_ACK_PENDING = 1000;

    /**
     * Creates a durable consumer configuration for Pull Consumer.
     * In Pull Consumer mode, the client actively pulls messages, providing better flow control.
     *
     * @param subject The subject to create consumer name from
     * @return ConsumerConfiguration for Pull Consumer
     */
    public ConsumerConfiguration createPullConsumerConfig(String subject) {
        String durableConsumerName = generateDurableConsumerName(subject);

        return ConsumerConfiguration.builder()
            .name(durableConsumerName)
            .durable(durableConsumerName)
            .deliverPolicy(DeliverPolicy.New)          // Only deliver new messages
            .ackPolicy(AckPolicy.Explicit)             // Explicit acknowledgment policy
            .ackWait(DEFAULT_ACK_WAIT)                 // Wait 30 seconds for ack
            .maxDeliver(DEFAULT_MAX_DELIVER)           // Maximum 3 delivery attempts
            .maxAckPending(DEFAULT_MAX_ACK_PENDING)    // Maximum unacknowledged messages
            .build();
    }

    /**
     * Generates a standardized durable consumer name from subject.
     *
     * @param subject The subject name
     * @return Durable consumer name
     */
    public String generateDurableConsumerName(String subject) {
        return "pull-consumer-" + subject.replace(".", "-");
    }
}