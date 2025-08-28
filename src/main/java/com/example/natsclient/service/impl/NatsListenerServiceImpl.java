package com.example.natsclient.service.impl;

import com.example.natsclient.config.NatsProperties;
import com.example.natsclient.model.ListenerResult;
import com.example.natsclient.service.NatsListenerService;
import com.example.natsclient.util.JsonIdExtractor;
import io.nats.client.*;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * Implementation of NatsListenerService using JetStream for reliable message consumption.
 */
@Service
public class NatsListenerServiceImpl implements NatsListenerService {
    
    private static final Logger logger = LoggerFactory.getLogger(NatsListenerServiceImpl.class);
    
    private final JetStream jetStream;
    private final JsonIdExtractor jsonIdExtractor;
    private final NatsProperties natsProperties;
    
    // Map to store active listeners
    private final ConcurrentMap<String, ActiveListener> activeListeners = new ConcurrentHashMap<>();
    
    @Autowired
    public NatsListenerServiceImpl(JetStream jetStream, JsonIdExtractor jsonIdExtractor, NatsProperties natsProperties) {
        this.jetStream = jetStream;
        this.jsonIdExtractor = jsonIdExtractor;
        this.natsProperties = natsProperties;
    }
    
    @Override
    public CompletableFuture<String> startListener(String subject, String idFieldName, Consumer<ListenerResult.MessageReceived> messageHandler) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String listenerId = "listener-" + UUID.randomUUID().toString();
                String consumerName = "consumer-" + subject.replace(".", "-") + "-" + System.currentTimeMillis();
                
                logger.info("Starting listener for subject '{}' with ID field '{}' and consumer '{}'", 
                           subject, idFieldName, consumerName);
                
                // Create consumer configuration
                ConsumerConfiguration consumerConfig = ConsumerConfiguration.builder()
                    .name(consumerName)
                    .deliverPolicy(DeliverPolicy.New) // Only receive new messages
                    .ackWait(Duration.ofSeconds(30))
                    .maxDeliver(3)
                    .build();
                
                // Create message handler
                MessageHandler natsMessageHandler = (message) -> {
                    handleIncomingMessage(listenerId, subject, idFieldName, message, messageHandler);
                };
                
                // Subscribe to the subject
                JetStreamSubscription subscription = jetStream.subscribe(
                    subject, 
                    PullSubscribeOptions.builder()
                        .configuration(consumerConfig)
                        .build()
                );
                
                // Create and store active listener info
                ActiveListener activeListener = new ActiveListener(
                    listenerId,
                    subject,
                    idFieldName,
                    subscription,
                    messageHandler,
                    Instant.now()
                );
                
                activeListeners.put(listenerId, activeListener);
                
                // Start message pulling in background
                startMessagePulling(activeListener);
                
                logger.info("Successfully started listener '{}' for subject '{}'", listenerId, subject);
                return listenerId;
                
            } catch (Exception e) {
                logger.error("Failed to start listener for subject '{}'", subject, e);
                throw new RuntimeException("Failed to start listener: " + e.getMessage(), e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> stopListener(String listenerId) {
        return CompletableFuture.runAsync(() -> {
            ActiveListener listener = activeListeners.remove(listenerId);
            if (listener != null) {
                try {
                    listener.subscription.unsubscribe();
                    listener.status = "STOPPED";
                    logger.info("Successfully stopped listener '{}' for subject '{}'", 
                              listenerId, listener.subject);
                } catch (Exception e) {
                    logger.error("Error stopping listener '{}'", listenerId, e);
                    throw new RuntimeException("Failed to stop listener: " + e.getMessage(), e);
                }
            } else {
                logger.warn("Listener '{}' not found or already stopped", listenerId);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> stopAllListeners() {
        return CompletableFuture.runAsync(() -> {
            logger.info("Stopping all {} active listeners", activeListeners.size());
            
            for (String listenerId : activeListeners.keySet()) {
                try {
                    stopListener(listenerId).join();
                } catch (Exception e) {
                    logger.error("Error stopping listener '{}'", listenerId, e);
                }
            }
            
            activeListeners.clear();
            logger.info("All listeners stopped");
        });
    }
    
    @Override
    public CompletableFuture<List<ListenerStatus>> getListenerStatus() {
        return CompletableFuture.supplyAsync(() -> {
            return activeListeners.values().stream()
                .map(listener -> new ListenerStatus(
                    listener.listenerId,
                    listener.subject,
                    listener.idFieldName,
                    listener.status,
                    listener.messagesReceived,
                    listener.startTime,
                    listener.lastMessageTime
                ))
                .toList();
        });
    }
    
    @Override
    public boolean isListenerActive(String subject) {
        return activeListeners.values().stream()
            .anyMatch(listener -> listener.subject.equals(subject) && "ACTIVE".equals(listener.status));
    }
    
    /**
     * Start pulling messages from JetStream subscription in a background thread.
     */
    private void startMessagePulling(ActiveListener listener) {
        Thread messageThread = new Thread(() -> {
            logger.info("Starting message pulling for listener '{}'", listener.listenerId);
            
            while ("ACTIVE".equals(listener.status) && activeListeners.containsKey(listener.listenerId)) {
                try {
                    // Pull messages from subscription
                    List<Message> messages = listener.subscription.fetch(10, Duration.ofSeconds(1));
                    
                    for (Message message : messages) {
                        handleIncomingMessage(
                            listener.listenerId,
                            listener.subject,
                            listener.idFieldName,
                            message,
                            listener.messageHandler
                        );
                        
                        // Acknowledge the message
                        message.ack();
                    }
                    
                } catch (Exception e) {
                    if ("ACTIVE".equals(listener.status)) {
                        logger.error("Error pulling messages for listener '{}'", listener.listenerId, e);
                        try {
                            Thread.sleep(1000); // Brief pause before retrying
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            
            logger.info("Message pulling stopped for listener '{}'", listener.listenerId);
        });
        
        messageThread.setDaemon(true);
        messageThread.start();
    }
    
    /**
     * Handle incoming NATS message.
     */
    private void handleIncomingMessage(String listenerId, String subject, String idFieldName, 
                                     Message message, Consumer<ListenerResult.MessageReceived> messageHandler) {
        try {
            String jsonPayload = new String(message.getData());
            String extractedId = jsonIdExtractor.extractId(jsonPayload, idFieldName);
            
            // Update listener statistics
            ActiveListener listener = activeListeners.get(listenerId);
            if (listener != null) {
                listener.messagesReceived++;
                listener.lastMessageTime = Instant.now();
            }
            
            // Create result
            ListenerResult.MessageReceived result = new ListenerResult.MessageReceived(
                subject,
                generateMessageId(message),
                extractedId,
                jsonPayload,
                message.metaData().streamSequence()
            );
            
            logger.debug("Received message on subject '{}' with extracted ID: '{}'", subject, extractedId);
            
            // Call the message handler
            messageHandler.accept(result);
            
        } catch (Exception e) {
            logger.error("Error handling message for listener '{}'", listenerId, e);
        }
    }
    
    /**
     * Generate a message ID from the NATS message.
     */
    private String generateMessageId(Message message) {
        if (message.getHeaders() != null && message.getHeaders().containsKey("Nats-Msg-Id")) {
            return message.getHeaders().getFirst("Nats-Msg-Id");
        }
        return "msg-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Internal class to track active listener information.
     */
    private static class ActiveListener {
        final String listenerId;
        final String subject;
        final String idFieldName;
        final JetStreamSubscription subscription;
        final Consumer<ListenerResult.MessageReceived> messageHandler;
        final Instant startTime;
        
        volatile String status = "ACTIVE";
        volatile long messagesReceived = 0;
        volatile Instant lastMessageTime;
        
        ActiveListener(String listenerId, String subject, String idFieldName, 
                      JetStreamSubscription subscription, Consumer<ListenerResult.MessageReceived> messageHandler,
                      Instant startTime) {
            this.listenerId = listenerId;
            this.subject = subject;
            this.idFieldName = idFieldName;
            this.subscription = subscription;
            this.messageHandler = messageHandler;
            this.startTime = startTime;
        }
    }
}