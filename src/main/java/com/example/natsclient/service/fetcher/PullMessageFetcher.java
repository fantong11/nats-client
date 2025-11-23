package com.example.natsclient.service.fetcher;

import com.example.natsclient.config.NatsConsumerProperties;
import com.example.natsclient.model.ListenerResult;
import com.example.natsclient.service.handler.MessageProcessor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Pull Consumer message fetcher.
 * Responsible for actively pulling messages from NATS JetStream and processing
 * them.
 *
 * Core responsibilities:
 * - Continuously pull messages from subscription
 * - Control batch size and timeout
 * - Gracefully stop the pull loop
 * - Delegate message processing to MessageProcessor
 * - Handle errors with retry and backoff
 * - Record metrics
 */
@Component
public class PullMessageFetcher {

    private static final Logger logger = LoggerFactory.getLogger(PullMessageFetcher.class);

    private final MessageProcessor messageProcessor;
    private final NatsConsumerProperties properties;
    private final MeterRegistry meterRegistry;

    public PullMessageFetcher(MessageProcessor messageProcessor,
            NatsConsumerProperties properties,
            MeterRegistry meterRegistry) {
        this.messageProcessor = messageProcessor;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Starts the message fetching loop.
     * This method will run continuously until the running flag is set to false.
     *
     * @param listenerId     Listener ID for logging
     * @param subject        Subject name
     * @param idFieldName    Field name to extract ID from JSON payload
     * @param subscription   JetStream Pull subscription
     * @param messageHandler Business message handler
     * @param running        Flag to control loop execution
     */
    public void startFetchingLoop(
            String listenerId,
            String subject,
            String idFieldName,
            JetStreamSubscription subscription,
            Consumer<ListenerResult.MessageReceived> messageHandler,
            AtomicBoolean running) {

        logger.info("Starting pull message fetching loop for listener '{}' on subject '{}'",
                listenerId, subject);

        int consecutiveErrors = 0;

        try {
            while (running.get()) {
                try {
                    fetchAndProcessBatch(listenerId, subject, idFieldName, subscription, messageHandler);
                    // Reset error count on success
                    if (consecutiveErrors > 0) {
                        consecutiveErrors = 0;
                        logger.info("Listener '{}' recovered from errors", listenerId);
                    }
                } catch (Exception e) {
                    consecutiveErrors++;
                    handleFetchError(listenerId, subject, e, consecutiveErrors);
                }
            }
        } catch (FetchLoopException e) {
            logger.error("Fatal error in fetching loop for listener '{}': {}", listenerId, e.getMessage(), e);
            throw e;
        } finally {
            logger.info("Pull message fetching loop stopped for listener '{}'", listenerId);
        }
    }

    /**
     * Fetches and processes a batch of messages.
     */
    private void fetchAndProcessBatch(
            String listenerId,
            String subject,
            String idFieldName,
            JetStreamSubscription subscription,
            Consumer<ListenerResult.MessageReceived> messageHandler) {

        // Use iterate() method to pull a batch of messages
        Iterator<Message> messages = subscription.iterate(properties.getBatchSize(), properties.getMaxWait());

        int processedCount = 0;
        while (messages.hasNext()) {
            Message message = messages.next();
            processMessage(listenerId, subject, idFieldName, message, messageHandler);
            processedCount++;
        }

        recordPulledMetrics(subject, processedCount);

        if (processedCount > 0) {
            logger.debug("Processed {} messages for listener '{}'", processedCount, listenerId);
        }

        // Short sleep to avoid high CPU usage if configured
        if (!properties.getPollInterval().isZero()) {
            sleep(properties.getPollInterval());
        }
    }

    /**
     * Processes a single message.
     */
    private void processMessage(
            String listenerId,
            String subject,
            String idFieldName,
            Message message,
            Consumer<ListenerResult.MessageReceived> messageHandler) {

        try {
            messageProcessor.processMessage(listenerId, subject, idFieldName, message, messageHandler);
            recordProcessingMetrics(subject, "success");
        } catch (Exception e) {
            logger.error("Failed to process message for listener '{}' on subject '{}': {}",
                    listenerId, subject, e.getMessage(), e);
            recordProcessingMetrics(subject, "failure");
            // Don't throw exception, continue processing next message
        }
    }

    /**
     * Handles errors during fetch loop with exponential backoff.
     */
    private void handleFetchError(String listenerId, String subject, Exception e, int consecutiveErrors) {
        recordErrorMetrics(subject, e.getClass().getSimpleName());

        if (isFatal(e)) {
            throw new FetchLoopException("Fatal error encountered", e);
        }

        // Calculate backoff
        long backoffMillis = (long) (properties.getBackoffInitial().toMillis() *
                Math.pow(properties.getBackoffMultiplier(), consecutiveErrors - 1));
        backoffMillis = Math.min(backoffMillis, properties.getBackoffMax().toMillis());

        logger.warn("Error in fetch loop for listener '{}' (attempt {}). Retrying in {} ms. Error: {}",
                listenerId, consecutiveErrors, backoffMillis, e.getMessage());

        sleep(Duration.ofMillis(backoffMillis));
    }

    /**
     * Determines if an exception is fatal and should stop the loop.
     */
    private boolean isFatal(Exception e) {
        // InterruptedException is usually a signal to stop
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return true;
        }
        // Add other fatal exceptions here if needed
        return false;
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchLoopException("Interrupted during sleep", e);
        }
    }

    // Metrics helpers

    private void recordPulledMetrics(String subject, int count) {
        Counter.builder("nats.consumer.messages.pulled")
                .tag("subject", subject)
                .tag("status", count > 0 ? "success" : "empty")
                .register(meterRegistry)
                .increment(count > 0 ? count : 1);
    }

    private void recordProcessingMetrics(String subject, String status) {
        Counter.builder("nats.consumer.messages.processed")
                .tag("subject", subject)
                .tag("status", status)
                .register(meterRegistry)
                .increment();
    }

    private void recordErrorMetrics(String subject, String errorType) {
        Counter.builder("nats.consumer.errors")
                .tag("subject", subject)
                .tag("type", errorType)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Fetch loop exception.
     */
    public static class FetchLoopException extends RuntimeException {
        public FetchLoopException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
