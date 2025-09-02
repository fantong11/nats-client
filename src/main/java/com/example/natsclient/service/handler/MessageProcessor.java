package com.example.natsclient.service.handler;

import com.example.natsclient.model.ListenerResult;
import com.example.natsclient.util.JsonIdExtractor;
import io.nats.client.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Processes incoming NATS messages and transforms them into domain objects.
 * Follows Single Responsibility Principle - only responsible for message processing.
 */
@Component
public class MessageProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageProcessor.class);
    private static final String NATS_MSG_ID_HEADER = "Nats-Msg-Id";
    
    private final JsonIdExtractor jsonIdExtractor;
    
    public MessageProcessor(JsonIdExtractor jsonIdExtractor) {
        this.jsonIdExtractor = jsonIdExtractor;
    }
    
    /**
     * Processes a NATS message and invokes the message handler.
     * 
     * @param listenerId The listener ID for logging purposes
     * @param subject The subject name
     * @param idFieldName The field name to extract ID from
     * @param message The NATS message
     * @param messageHandler The consumer to handle the processed message
     * @throws MessageProcessingException if processing fails
     */
    public void processMessage(String listenerId, String subject, String idFieldName, 
                              Message message, Consumer<ListenerResult.MessageReceived> messageHandler) {
        try {
            ListenerResult.MessageReceived result = transformMessage(subject, idFieldName, message);
            
            logger.debug("Processed message on subject '{}' with extracted ID: '{}'", 
                        subject, result.extractedId());
            
            messageHandler.accept(result);
            acknowledgeMessage(message);
            
        } catch (Exception e) {
            logger.error("Failed to process message for listener '{}' on subject '{}'", 
                        listenerId, subject, e);
            throw new MessageProcessingException("Message processing failed", e);
        }
    }
    
    /**
     * Transforms a NATS message into a domain object.
     */
    private ListenerResult.MessageReceived transformMessage(String subject, String idFieldName, Message message) {
        String jsonPayload = new String(message.getData());
        String extractedId = jsonIdExtractor.extractId(jsonPayload, idFieldName);
        String messageId = generateMessageId(message);
        
        return new ListenerResult.MessageReceived(
            subject,
            messageId,
            extractedId,
            jsonPayload,
            message.metaData().streamSequence()
        );
    }
    
    /**
     * Generates a unique message ID from the NATS message.
     */
    private String generateMessageId(Message message) {
        if (message.getHeaders() != null && message.getHeaders().containsKey(NATS_MSG_ID_HEADER)) {
            return message.getHeaders().getFirst(NATS_MSG_ID_HEADER);
        }
        return "msg-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Acknowledges the message after successful processing.
     */
    private void acknowledgeMessage(Message message) {
        try {
            message.ack();
        } catch (Exception e) {
            logger.warn("Failed to acknowledge message", e);
            // Don't throw - message processing was successful
        }
    }
    
    /**
     * Exception thrown when message processing fails.
     */
    public static class MessageProcessingException extends RuntimeException {
        public MessageProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}