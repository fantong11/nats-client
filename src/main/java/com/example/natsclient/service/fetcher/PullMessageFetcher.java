package com.example.natsclient.service.fetcher;

import com.example.natsclient.model.ListenerResult;
import com.example.natsclient.service.handler.MessageProcessor;
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
 * Responsible for actively pulling messages from NATS JetStream and processing them.
 *
 * Core responsibilities:
 * - Continuously pull messages from subscription
 * - Control batch size and timeout
 * - Gracefully stop the pull loop
 * - Delegate message processing to MessageProcessor
 *
 * Follows Single Responsibility Principle (SRP)
 */
@Component
public class PullMessageFetcher {

    private static final Logger logger = LoggerFactory.getLogger(PullMessageFetcher.class);

    // Pull Consumer default configuration
    private static final int DEFAULT_BATCH_SIZE = 10;           // Pull 10 messages per batch
    private static final Duration DEFAULT_MAX_WAIT = Duration.ofSeconds(1);  // Wait up to 1 second
    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);    // Poll interval

    private final MessageProcessor messageProcessor;

    public PullMessageFetcher(MessageProcessor messageProcessor) {
        this.messageProcessor = messageProcessor;
    }

    /**
     * Starts the message fetching loop.
     * This method will run continuously until the running flag is set to false.
     *
     * @param listenerId Listener ID for logging
     * @param subject Subject name
     * @param idFieldName Field name to extract ID from JSON payload
     * @param subscription JetStream Pull subscription
     * @param messageHandler Business message handler
     * @param running Flag to control loop execution
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

        try {
            while (running.get()) {
                fetchAndProcessBatch(listenerId, subject, idFieldName, subscription, messageHandler);
            }
        } catch (Exception e) {
            logger.error("Fatal error in fetching loop for listener '{}': {}", listenerId, e.getMessage(), e);
            throw new FetchLoopException("Fetching loop terminated due to error", e);
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

        try {
            // Use iterate() method to pull a batch of messages
            // Parameters: batch size and maximum wait time
            Iterator<Message> messages = subscription.iterate(DEFAULT_BATCH_SIZE, DEFAULT_MAX_WAIT);

            int processedCount = 0;
            while (messages.hasNext()) {
                Message message = messages.next();
                processMessage(listenerId, subject, idFieldName, message, messageHandler);
                processedCount++;
            }

            if (processedCount > 0) {
                logger.debug("Processed {} messages for listener '{}'", processedCount, listenerId);
            }

            // Short sleep to avoid high CPU usage
            sleepBetweenBatches();

        } catch (Exception e) {
            logger.error("Error fetching batch for listener '{}': {}", listenerId, e.getMessage());
            sleepBetweenBatches(); // Sleep on error too, to avoid rapid retries
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
        } catch (Exception e) {
            logger.error("Failed to process message for listener '{}' on subject '{}': {}",
                    listenerId, subject, e.getMessage(), e);
            // Don't throw exception, continue processing next message
        }
    }

    /**
     * Short sleep between batches.
     */
    private void sleepBetweenBatches() {
        try {
            Thread.sleep(POLL_INTERVAL.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
