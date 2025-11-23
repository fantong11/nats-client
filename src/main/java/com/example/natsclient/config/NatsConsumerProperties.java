package com.example.natsclient.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "nats.consumer")
public class NatsConsumerProperties {

    /**
     * Number of messages to pull in each batch.
     */
    private int batchSize = 10;

    /**
     * Maximum time to wait for a batch to be filled.
     */
    private Duration maxWait = Duration.ofSeconds(1);

    /**
     * Interval between pull attempts.
     */
    private Duration pollInterval = Duration.ofMillis(100);

    /**
     * Maximum number of retries for transient errors.
     */
    private int maxRetries = 3;

    /**
     * Initial backoff duration for retries.
     */
    private Duration backoffInitial = Duration.ofSeconds(1);

    /**
     * Multiplier for exponential backoff.
     */
    private double backoffMultiplier = 2.0;

    /**
     * Maximum backoff duration.
     */
    private Duration backoffMax = Duration.ofSeconds(30);
}
