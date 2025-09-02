package com.example.natsclient.service.impl;

import com.example.natsclient.model.ListenerResult;
import com.example.natsclient.service.NatsListenerService;
import com.example.natsclient.service.config.ConsumerConfigurationFactory;
import com.example.natsclient.service.handler.MessageProcessor;
import com.example.natsclient.service.registry.ListenerRegistry;
import io.nats.client.*;
import io.nats.client.api.ConsumerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Clean Code implementation of NatsListenerService following SOLID principles.
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
    
    private final Connection natsConnection;
    private final JetStream jetStream;
    private final ConsumerConfigurationFactory configFactory;
    private final MessageProcessor messageProcessor;
    private final ListenerRegistry listenerRegistry;
    
    public NatsListenerServiceImpl(Connection natsConnection, 
                                  JetStream jetStream,
                                  ConsumerConfigurationFactory configFactory,
                                  MessageProcessor messageProcessor,
                                  ListenerRegistry listenerRegistry) {
        this.natsConnection = natsConnection;
        this.jetStream = jetStream;
        this.configFactory = configFactory;
        this.messageProcessor = messageProcessor;
        this.listenerRegistry = listenerRegistry;
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
    
    private String doStartListener(String subject, String idFieldName, 
                                  Consumer<ListenerResult.MessageReceived> messageHandler) throws Exception {
        ConsumerConfiguration config = configFactory.createDurableConsumerConfig(subject);
        String consumerName = configFactory.generateDurableConsumerName(subject);
        
        logger.info("Starting durable listener for subject '{}' with ID field '{}' and consumer '{}'", 
                   subject, idFieldName, consumerName);
        
        JetStreamSubscription subscription = createSubscription(subject, idFieldName, config, messageHandler);
        String listenerId = listenerRegistry.registerListener(subject, idFieldName, subscription, messageHandler);
        
        logger.info("Successfully started listener '{}' for subject '{}'", listenerId, subject);
        return listenerId;
    }
    
    private JetStreamSubscription createSubscription(String subject, String idFieldName,
                                                    ConsumerConfiguration config,
                                                    Consumer<ListenerResult.MessageReceived> messageHandler) throws Exception {
        Dispatcher dispatcher = natsConnection.createDispatcher();
        
        MessageHandler natsMessageHandler = message -> {
            try {
                messageProcessor.processMessage("temp-id", subject, idFieldName, message, messageHandler);
            } catch (Exception e) {
                logger.error("Message processing failed for subject '{}'", subject, e);
                // Don't ack on error - let NATS retry
            }
        };
        
        return jetStream.subscribe(
            subject,
            dispatcher,
            natsMessageHandler,
            false, // manual ack
            PushSubscribeOptions.builder().configuration(config).build()
        );
    }
    
    private void doStopListener(String listenerId) {
        ListenerRegistry.ListenerInfo listener = listenerRegistry.unregisterListener(listenerId);
        
        if (listener != null) {
            unsubscribeGracefully(listener.subscription());
            logger.info("Successfully stopped listener '{}' for subject '{}'", 
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