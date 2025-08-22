package com.example.natsclient.service.builder;

import com.example.natsclient.config.NatsProperties;
import io.nats.client.PublishOptions;
import io.nats.client.api.PublishAck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Builder class for creating PublishOptions with different configurations.
 * Implements Builder Pattern to provide flexible and readable configuration of NATS JetStream publish options.
 */
@Component
public class NatsPublishOptionsBuilder {
    
    private static final Logger logger = LoggerFactory.getLogger(NatsPublishOptionsBuilder.class);
    
    private final NatsProperties natsProperties;
    
    public NatsPublishOptionsBuilder(NatsProperties natsProperties) {
        this.natsProperties = natsProperties;
    }
    
    /**
     * Creates a new builder instance for fluent configuration.
     */
    public Builder newBuilder() {
        return new Builder();
    }
    
    /**
     * Creates default PublishOptions for standard operations.
     */
    public PublishOptions createDefault() {
        return newBuilder()
                .withDefaultStream()
                .withDefaultTimeout()
                .build();
    }
    
    /**
     * Creates PublishOptions optimized for high-throughput operations.
     */
    public PublishOptions createHighThroughput() {
        return newBuilder()
                .withDefaultStream()
                .withTimeout(Duration.ofSeconds(5))
                .withExpectedLastSequence(null) // Allow concurrent writes
                .build();
    }
    
    /**
     * Creates PublishOptions for critical operations requiring delivery confirmation.
     */
    public PublishOptions createCritical() {
        return newBuilder()
                .withDefaultStream()
                .withTimeout(Duration.ofSeconds(30))
                .withExpectedLastSequence(null)
                .build();
    }
    
    /**
     * Creates PublishOptions for retry operations.
     */
    public PublishOptions createForRetry(long expectedSequence) {
        return newBuilder()
                .withDefaultStream()
                .withTimeout(Duration.ofSeconds(10))
                .withExpectedLastSequence(expectedSequence)
                .build();
    }
    
    /**
     * Fluent builder for PublishOptions configuration.
     */
    public class Builder {
        private String expectedStream;
        private Duration timeout;
        private Long expectedLastSequence;
        private String messageId;
        
        private Builder() {
            // Package-private constructor
        }
        
        /**
         * Sets the expected stream name using default from configuration.
         */
        public Builder withDefaultStream() {
            this.expectedStream = natsProperties.getJetStream().getStream().getDefaultName();
            return this;
        }
        
        /**
         * Sets a custom expected stream name.
         */
        public Builder withStream(String streamName) {
            this.expectedStream = streamName;
            return this;
        }
        
        /**
         * Sets the timeout using default from configuration.
         */
        public Builder withDefaultTimeout() {
            this.timeout = Duration.ofMillis(natsProperties.getRequest().getTimeout());
            return this;
        }
        
        /**
         * Sets a custom timeout.
         */
        public Builder withTimeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }
        
        /**
         * Sets the expected last sequence number for optimistic concurrency.
         */
        public Builder withExpectedLastSequence(Long sequence) {
            this.expectedLastSequence = sequence;
            return this;
        }
        
        /**
         * Sets a message ID for deduplication.
         */
        public Builder withMessageId(String messageId) {
            this.messageId = messageId;
            return this;
        }
        
        /**
         * Builds the PublishOptions with current configuration.
         */
        public PublishOptions build() {
            PublishOptions.Builder optionsBuilder = PublishOptions.builder();
            
            // Set expected stream (required)
            if (expectedStream != null && !expectedStream.trim().isEmpty()) {
                optionsBuilder.expectedStream(expectedStream);
                logger.debug("Building PublishOptions with stream: {}", expectedStream);
            } else {
                logger.warn("No stream specified, using default stream name");
                optionsBuilder.expectedStream(natsProperties.getJetStream().getStream().getDefaultName());
            }
            
            // Set expected last sequence if specified
            if (expectedLastSequence != null) {
                optionsBuilder.expectedLastSequence(expectedLastSequence);
                logger.debug("Building PublishOptions with expected last sequence: {}", expectedLastSequence);
            }
            
            // Set message ID if specified
            if (messageId != null && !messageId.trim().isEmpty()) {
                optionsBuilder.messageId(messageId);
                logger.debug("Building PublishOptions with message ID: {}", messageId);
            }
            
            PublishOptions options = optionsBuilder.build();
            
            logger.debug("Successfully built PublishOptions for stream: {}, timeout: {}", 
                        expectedStream, timeout);
            
            return options;
        }
        
        /**
         * Builds the PublishOptions and logs the configuration for debugging.
         */
        public PublishOptions buildWithLogging() {
            PublishOptions options = build();
            
            logger.info("Built PublishOptions - Stream: {}, Timeout: {}ms, MessageId: {}, ExpectedLastSeq: {}",
                       expectedStream, 
                       timeout != null ? timeout.toMillis() : "default",
                       messageId != null ? messageId : "none",
                       expectedLastSequence != null ? expectedLastSequence : "none");
            
            return options;
        }
    }
    
    /**
     * Utility method to extract key information from PublishAck for logging.
     */
    public String formatPublishAck(PublishAck ack) {
        if (ack == null) {
            return "null";
        }
        
        return String.format("PublishAck{stream='%s', sequence=%d, duplicate=%s}", 
                           ack.getStream(), ack.getSeqno(), ack.isDuplicate());
    }
}