package com.example.natsclient.util;

import io.nats.client.Message;
import io.nats.client.impl.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Utility class for handling NATS message headers, particularly for deduplication.
 * 
 * Provides convenient methods for:
 * - Extracting and setting the "Nats-Msg-Id" header
 * - Generating unique message IDs
 * - Handling other standard NATS headers
 */
public final class NatsMessageHeaders {
    
    private static final Logger logger = LoggerFactory.getLogger(NatsMessageHeaders.class);
    
    // Standard NATS headers
    public static final String NATS_MSG_ID = "Nats-Msg-Id";
    public static final String NATS_CORRELATION_ID = "Nats-Correlation-Id";
    public static final String NATS_REPLY_TO = "Nats-Reply-To";
    public static final String NATS_TIMESTAMP = "Nats-Timestamp";
    public static final String NATS_SOURCE = "Nats-Source";
    public static final String NATS_VERSION = "Nats-Version";
    
    // Custom headers for enhanced functionality
    public static final String CUSTOM_RETRY_COUNT = "X-Retry-Count";
    public static final String CUSTOM_PROCESSING_TIMEOUT = "X-Processing-Timeout";
    public static final String CUSTOM_DEDUP_TTL = "X-Dedup-TTL";
    public static final String CUSTOM_SOURCE_SERVICE = "X-Source-Service";
    
    private NatsMessageHeaders() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Extracts the message ID from NATS message headers.
     * 
     * @param message The NATS message
     * @return Optional containing the message ID if present
     */
    public static Optional<String> getMessageId(Message message) {
        if (message == null || !message.hasHeaders()) {
            return Optional.empty();
        }
        
        try {
            List<String> messageIds = message.getHeaders().get(NATS_MSG_ID);
            if (messageIds != null && !messageIds.isEmpty()) {
                String messageId = messageIds.get(0);
                logger.debug("Extracted message ID from headers: '{}'", messageId);
                return Optional.of(messageId);
            }
        } catch (Exception e) {
            logger.warn("Error extracting message ID from headers: {}", e.getMessage());
        }
        
        return Optional.empty();
    }
    
    /**
     * Extracts the message ID from NATS message headers or generates a new one.
     * 
     * @param message The NATS message
     * @return The message ID (existing or newly generated)
     */
    public static String getOrGenerateMessageId(Message message) {
        return getMessageId(message).orElseGet(() -> {
            String generatedId = generateMessageId();
            logger.debug("Generated new message ID: '{}'", generatedId);
            return generatedId;
        });
    }
    
    /**
     * Generates a unique message ID using UUID.
     * 
     * @return A unique message ID
     */
    public static String generateMessageId() {
        return "msg-" + UUID.randomUUID().toString();
    }
    
    /**
     * Generates a unique message ID with a custom prefix.
     * 
     * @param prefix The prefix for the message ID
     * @return A unique message ID with the specified prefix
     */
    public static String generateMessageId(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return generateMessageId();
        }
        return prefix + "-" + UUID.randomUUID().toString();
    }
    
    /**
     * Creates Headers object with message ID for outgoing messages.
     * 
     * @param messageId The message ID to set
     * @return Headers object with the message ID
     */
    public static Headers createHeadersWithMessageId(String messageId) {
        Headers headers = new Headers();
        if (messageId != null && !messageId.trim().isEmpty()) {
            headers.add(NATS_MSG_ID, messageId);
            logger.debug("Created headers with message ID: '{}'", messageId);
        }
        return headers;
    }
    
    /**
     * Creates Headers object with message ID and correlation ID.
     * 
     * @param messageId The message ID to set
     * @param correlationId The correlation ID to set
     * @return Headers object with both IDs
     */
    public static Headers createHeadersWithIds(String messageId, String correlationId) {
        Headers headers = createHeadersWithMessageId(messageId);
        if (correlationId != null && !correlationId.trim().isEmpty()) {
            headers.add(NATS_CORRELATION_ID, correlationId);
            logger.debug("Added correlation ID to headers: '{}'", correlationId);
        }
        return headers;
    }
    
    /**
     * Adds or updates the message ID in existing headers.
     * 
     * @param headers The existing headers (will be modified)
     * @param messageId The message ID to set
     */
    public static void setMessageId(Headers headers, String messageId) {
        if (headers != null && messageId != null && !messageId.trim().isEmpty()) {
            headers.put(NATS_MSG_ID, messageId);
            logger.debug("Set message ID in headers: '{}'", messageId);
        }
    }
    
    /**
     * Extracts the correlation ID from NATS message headers.
     * 
     * @param message The NATS message
     * @return Optional containing the correlation ID if present
     */
    public static Optional<String> getCorrelationId(Message message) {
        if (message == null || !message.hasHeaders()) {
            return Optional.empty();
        }
        
        try {
            List<String> correlationIds = message.getHeaders().get(NATS_CORRELATION_ID);
            if (correlationIds != null && !correlationIds.isEmpty()) {
                return Optional.of(correlationIds.get(0));
            }
        } catch (Exception e) {
            logger.warn("Error extracting correlation ID from headers: {}", e.getMessage());
        }
        
        return Optional.empty();
    }
    
    /**
     * Extracts a custom header value from NATS message headers.
     * 
     * @param message The NATS message
     * @param headerName The header name to extract
     * @return Optional containing the header value if present
     */
    public static Optional<String> getCustomHeader(Message message, String headerName) {
        if (message == null || !message.hasHeaders() || headerName == null) {
            return Optional.empty();
        }
        
        try {
            List<String> values = message.getHeaders().get(headerName);
            if (values != null && !values.isEmpty()) {
                return Optional.of(values.get(0));
            }
        } catch (Exception e) {
            logger.warn("Error extracting header '{}' from message: {}", headerName, e.getMessage());
        }
        
        return Optional.empty();
    }
    
    /**
     * Extracts the source service from custom headers.
     * 
     * @param message The NATS message
     * @return Optional containing the source service if present
     */
    public static Optional<String> getSourceService(Message message) {
        return getCustomHeader(message, CUSTOM_SOURCE_SERVICE);
    }
    
    /**
     * Extracts the retry count from custom headers.
     * 
     * @param message The NATS message
     * @return The retry count, or 0 if not present or invalid
     */
    public static int getRetryCount(Message message) {
        return getCustomHeader(message, CUSTOM_RETRY_COUNT)
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid retry count header value: '{}'", value);
                        return 0;
                    }
                })
                .orElse(0);
    }
    
    /**
     * Creates comprehensive headers for outgoing messages with all standard fields.
     * 
     * @param messageId The message ID
     * @param correlationId The correlation ID (optional)
     * @param sourceService The source service name (optional)
     * @return Headers object with all specified fields
     */
    public static Headers createComprehensiveHeaders(String messageId, String correlationId, String sourceService) {
        Headers headers = new Headers();
        
        if (messageId != null && !messageId.trim().isEmpty()) {
            headers.add(NATS_MSG_ID, messageId);
        }
        
        if (correlationId != null && !correlationId.trim().isEmpty()) {
            headers.add(NATS_CORRELATION_ID, correlationId);
        }
        
        if (sourceService != null && !sourceService.trim().isEmpty()) {
            headers.add(CUSTOM_SOURCE_SERVICE, sourceService);
        }
        
        // Add timestamp
        headers.add(NATS_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
        
        logger.debug("Created comprehensive headers with messageId='{}', correlationId='{}', sourceService='{}'", 
                    messageId, correlationId, sourceService);
        
        return headers;
    }
    
    /**
     * Checks if a message has the required headers for deduplication.
     * 
     * @param message The NATS message
     * @return true if the message has a message ID header
     */
    public static boolean hasDeduplicationHeaders(Message message) {
        return getMessageId(message).isPresent();
    }
    
    /**
     * Logs all headers for debugging purposes.
     * 
     * @param message The NATS message
     * @param logPrefix Prefix for log messages
     */
    public static void logHeaders(Message message, String logPrefix) {
        if (message == null || !message.hasHeaders()) {
            logger.debug("{}: No headers present", logPrefix);
            return;
        }
        
        try {
            StringBuilder headerLog = new StringBuilder(logPrefix).append(": Headers = {");
            message.getHeaders().entrySet().forEach(entry -> 
                headerLog.append(entry.getKey()).append("=").append(entry.getValue()).append(", ")
            );
            headerLog.append("}");
            logger.debug(headerLog.toString());
        } catch (Exception e) {
            logger.warn("{}: Error logging headers: {}", logPrefix, e.getMessage());
        }
    }
}