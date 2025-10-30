package com.example.natsclient.service.impl;

import com.example.natsclient.model.ListenerResult;
import com.example.natsclient.service.NatsListenerService;
import com.example.natsclient.service.config.ConsumerConfigurationFactory;
import com.example.natsclient.service.fetcher.PullMessageFetcher;
import com.example.natsclient.service.registry.ListenerRegistry;
import io.nats.client.*;
import io.nats.client.api.ConsumerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Clean Code implementation of NatsListenerService following SOLID principles.
 *
 * Pull Consumer mode implementation:
 * - Client actively pulls messages, providing better flow control
 * - Uses dedicated thread pool to manage pull tasks
 * - Supports graceful shutdown and resource cleanup
 *
 * - SRP: Delegates specific responsibilities to specialized components
 * - OCP: Extensible through dependency injection and configuration
 * - LSP: Properly implements the NatsListenerService interface
 * - ISP: Uses focused interfaces and dependencies
 * - DIP: Depends on abstractions, not concrete implementations
 */
@Service
public class NatsListenerServiceImpl implements NatsListenerService {

    private static final Logger logger = LoggerFactory.getLogger(NatsListenerServiceImpl.class);

    private final JetStream jetStream;
    private final ConsumerConfigurationFactory configFactory;
    private final PullMessageFetcher pullMessageFetcher;
    private final ListenerRegistry listenerRegistry;

    // Dedicated thread pool for Pull Consumer
    private final ExecutorService fetcherExecutorService;

    public NatsListenerServiceImpl(JetStream jetStream,
                                  ConsumerConfigurationFactory configFactory,
                                  PullMessageFetcher pullMessageFetcher,
                                  ListenerRegistry listenerRegistry) {
        this.jetStream = jetStream;
        this.configFactory = configFactory;
        this.pullMessageFetcher = pullMessageFetcher;
        this.listenerRegistry = listenerRegistry;

        // Create thread pool for message fetching
        this.fetcherExecutorService = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r);
            thread.setName("nats-pull-fetcher-" + System.currentTimeMillis());
            thread.setDaemon(true);
            return thread;
        });

        logger.info("NatsListenerService initialized with Pull Consumer mode");
    }

    @Override
    public CompletableFuture<String> startListener(String subject, String idFieldName, 
                                                  Consumer<ListenerResult.MessageReceived> messageHandler) {
        return CompletableFuture.supplyAsync(() -> {
            validateInputs(subject, idFieldName, messageHandler);
            
            try {
                return doStartListener(subject, idFieldName, messageHandler);
            } catch (Exception e) {
                logger.error("Failed to start listener for subject '{}'", subject, e);
                throw new ListenerStartupException("Failed to start listener for subject: " + subject, e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> stopListener(String listenerId) {
        return CompletableFuture.runAsync(() -> {
            validateListenerId(listenerId);
            
            try {
                doStopListener(listenerId);
            } catch (Exception e) {
                logger.error("Failed to stop listener '{}'", listenerId, e);
                throw new ListenerStopException("Failed to stop listener: " + listenerId, e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> stopAllListeners() {
        return CompletableFuture.runAsync(() -> {
            int listenerCount = listenerRegistry.getActiveListenerCount();
            logger.info("Stopping all {} active listeners", listenerCount);
            
            List<String> listenerIds = listenerRegistry.getAllListenerIds();
            stopListenersGracefully(listenerIds);
            
            listenerRegistry.clearAll();
            logger.info("All listeners stopped successfully");
        });
    }
    
    @Override
    public CompletableFuture<List<ListenerStatus>> getListenerStatus() {
        return CompletableFuture.supplyAsync(listenerRegistry::getAllListenerStatuses);
    }
    
    @Override
    public boolean isListenerActive(String subject) {
        validateSubject(subject);
        return listenerRegistry.hasActiveListenerFor(subject);
    }
    
    // Private helper methods following Clean Code principles

    /**
     * Core logic for starting a Pull Consumer listener.
     */
    private String doStartListener(String subject, String idFieldName,
                                  Consumer<ListenerResult.MessageReceived> messageHandler) throws Exception {
        // 1. Create Pull Consumer configuration
        ConsumerConfiguration config = configFactory.createPullConsumerConfig(subject);
        String consumerName = configFactory.generateDurableConsumerName(subject);

        logger.info("Starting Pull Consumer listener for subject '{}' with ID field '{}' and consumer '{}'",
                   subject, idFieldName, consumerName);

        // 2. Create Pull subscription
        JetStreamSubscription subscription = createPullSubscription(subject, config);

        // 3. Create running flag (to control the pull loop)
        AtomicBoolean running = new AtomicBoolean(true);

        // 4. Start message fetching loop in a separate thread
        String tempListenerId = "listener-" + System.currentTimeMillis();
        Future<?> fetcherFuture = fetcherExecutorService.submit(() -> {
            pullMessageFetcher.startFetchingLoop(
                tempListenerId, subject, idFieldName, subscription, messageHandler, running
            );
        });

        // 5. Register the listener
        String listenerId = listenerRegistry.registerListener(
            subject, idFieldName, subscription, messageHandler, fetcherFuture, running
        );

        logger.info("Successfully started Pull Consumer listener '{}' for subject '{}'", listenerId, subject);
        return listenerId;
    }

    /**
     * Creates a Pull Consumer subscription.
     * No Dispatcher needed, directly uses PullSubscribeOptions.
     */
    private JetStreamSubscription createPullSubscription(String subject, ConsumerConfiguration config) throws Exception {
        PullSubscribeOptions pullOptions = PullSubscribeOptions.builder()
            .configuration(config)
            .build();

        return jetStream.subscribe(subject, pullOptions);
    }
    
    /**
     * Stops a Pull Consumer listener.
     * Gracefully stops the fetcher thread and cleans up resources.
     */
    private void doStopListener(String listenerId) {
        ListenerRegistry.ListenerInfo listener = listenerRegistry.unregisterListener(listenerId);

        if (listener != null) {
            // 1. Set running flag to false to stop the pull loop
            listener.running().set(false);

            // 2. Cancel the fetcher task
            if (listener.fetcherFuture() != null) {
                listener.fetcherFuture().cancel(true);
            }

            // 3. Unsubscribe
            unsubscribeGracefully(listener.subscription());

            logger.info("Successfully stopped Pull Consumer listener '{}' for subject '{}'",
                       listenerId, listener.subject());
        } else {
            logger.warn("Listener '{}' not found or already stopped", listenerId);
        }
    }
    
    private void stopListenersGracefully(List<String> listenerIds) {
        for (String listenerId : listenerIds) {
            try {
                doStopListener(listenerId);
            } catch (Exception e) {
                logger.error("Error stopping listener '{}'", listenerId, e);
                // Continue with other listeners
            }
        }
    }
    
    private void unsubscribeGracefully(JetStreamSubscription subscription) {
        try {
            subscription.unsubscribe();
        } catch (Exception e) {
            logger.warn("Error during unsubscription", e);
        }
    }
    
    // Input validation methods
    
    private void validateInputs(String subject, String idFieldName, Consumer<ListenerResult.MessageReceived> messageHandler) {
        validateSubject(subject);
        validateIdFieldName(idFieldName);
        validateMessageHandler(messageHandler);
    }
    
    private void validateSubject(String subject) {
        if (subject == null || subject.trim().isEmpty()) {
            throw new IllegalArgumentException("Subject cannot be null or empty");
        }
    }
    
    private void validateIdFieldName(String idFieldName) {
        if (idFieldName == null || idFieldName.trim().isEmpty()) {
            throw new IllegalArgumentException("ID field name cannot be null or empty");
        }
    }
    
    private void validateMessageHandler(Consumer<ListenerResult.MessageReceived> messageHandler) {
        if (messageHandler == null) {
            throw new IllegalArgumentException("Message handler cannot be null");
        }
    }
    
    private void validateListenerId(String listenerId) {
        if (listenerId == null || listenerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Listener ID cannot be null or empty");
        }
    }

    /**
     * Gracefully shuts down the thread pool when application closes.
     */
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down NatsListenerService and fetcher thread pool");

        // Stop all listeners first
        try {
            stopAllListeners().join();
        } catch (Exception e) {
            logger.error("Error stopping listeners during shutdown", e);
        }

        // Shutdown thread pool
        fetcherExecutorService.shutdown();
        try {
            if (!fetcherExecutorService.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warn("Fetcher thread pool did not terminate in time, forcing shutdown");
                fetcherExecutorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fetcherExecutorService.shutdownNow();
        }

        logger.info("NatsListenerService shutdown complete");
    }

    // Custom exceptions for better error handling

    public static class ListenerStartupException extends RuntimeException {
        public ListenerStartupException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class ListenerStopException extends RuntimeException {
        public ListenerStopException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}