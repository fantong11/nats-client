package com.example.natsclient.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for payload manipulation operations including ID extraction and correlation field injection.
 */
@Service
public class PayloadProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(PayloadProcessor.class);
    private static final String CORRELATION_ID_FIELD = "correlationId";
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * Inject correlationId field into the payload object.
     * 
     * @param originalPayload The original payload object
     * @param correlationId The correlation ID to inject (typically the requestId)
     * @return Payload with correlationId field added
     */
    public Object injectCorrelationId(Object originalPayload, String correlationId) {
        if (originalPayload == null || correlationId == null) {
            logger.warn("Cannot inject correlationId: originalPayload={}, correlationId={}", originalPayload, correlationId);
            return originalPayload;
        }
        
        try {
            // Convert payload to JsonNode for manipulation
            JsonNode payloadNode = objectMapper.valueToTree(originalPayload);
            
            if (payloadNode.isObject()) {
                ObjectNode objectNode = (ObjectNode) payloadNode;
                
                // Add correlationId field
                objectNode.put(CORRELATION_ID_FIELD, correlationId);
                
                logger.debug("Injected correlationId into payload: {}", correlationId);
                
                // Convert back to generic object
                return objectMapper.treeToValue(objectNode, Object.class);
            } else {
                logger.warn("Payload is not a JSON object, cannot inject correlationId. Payload type: {}", 
                           payloadNode.getNodeType());
                return originalPayload;
            }
            
        } catch (Exception e) {
            logger.error("Error injecting correlationId into payload: {}", correlationId, e);
            return originalPayload;
        }
    }
    
    /**
     * Extract correlation ID from a payload.
     * 
     * @param payload The payload to extract from
     * @return The correlation ID, or null if not found
     */
    public String extractCorrelationId(Object payload) {
        if (payload == null) {
            return null;
        }
        
        try {
            JsonNode payloadNode = objectMapper.valueToTree(payload);
            
            if (payloadNode.isObject() && payloadNode.has(CORRELATION_ID_FIELD)) {
                String correlationId = payloadNode.get(CORRELATION_ID_FIELD).asText();
                logger.debug("Extracted correlationId from payload: {}", correlationId);
                return correlationId;
            }
            
        } catch (Exception e) {
            logger.error("Error extracting correlationId from payload", e);
        }
        
        return null;
    }
    
    /**
     * Extract correlation ID from a JSON string payload.
     * 
     * @param jsonPayload The JSON string payload
     * @return The correlation ID, or null if not found
     */
    public String extractCorrelationIdFromJson(String jsonPayload) {
        if (jsonPayload == null || jsonPayload.trim().isEmpty()) {
            return null;
        }
        
        try {
            JsonNode payloadNode = objectMapper.readTree(jsonPayload);
            
            if (payloadNode.isObject() && payloadNode.has(CORRELATION_ID_FIELD)) {
                String correlationId = payloadNode.get(CORRELATION_ID_FIELD).asText();
                logger.debug("Extracted correlationId from JSON payload: {}", correlationId);
                return correlationId;
            }
            
        } catch (Exception e) {
            logger.error("Error extracting correlationId from JSON payload", e);
        }
        
        return null;
    }
    
    /**
     * Extract ID from payload using specified field name (supports dot notation).
     * 
     * @param payload The payload object to extract from
     * @param fieldName The field name to extract (supports dot notation like "user.id", "order.details.id")
     * @return The extracted ID, or null if not found
     */
    public String extractIdFromPayload(Object payload, String fieldName) {
        if (payload == null || fieldName == null || fieldName.trim().isEmpty()) {
            return null;
        }
        
        try {
            JsonNode payloadNode = objectMapper.valueToTree(payload);
            return extractIdFromJsonNode(payloadNode, fieldName);
            
        } catch (Exception e) {
            logger.error("Error extracting ID '{}' from payload", fieldName, e);
            return null;
        }
    }
    
    /**
     * Extract ID from JSON string using specified field name (supports dot notation).
     * 
     * @param jsonPayload The JSON string payload
     * @param fieldName The field name to extract (supports dot notation)
     * @return The extracted ID, or null if not found
     */
    public String extractIdFromJson(String jsonPayload, String fieldName) {
        if (jsonPayload == null || jsonPayload.trim().isEmpty() || fieldName == null) {
            return null;
        }
        
        try {
            JsonNode payloadNode = objectMapper.readTree(jsonPayload);
            return extractIdFromJsonNode(payloadNode, fieldName);
            
        } catch (Exception e) {
            logger.error("Error extracting ID '{}' from JSON payload", fieldName, e);
            return null;
        }
    }
    
    /**
     * Extract ID from JsonNode using field name with dot notation support.
     */
    private String extractIdFromJsonNode(JsonNode node, String fieldName) {
        if (!node.isObject()) {
            return null;
        }
        
        String[] fieldParts = fieldName.split("\\.");
        JsonNode currentNode = node;
        
        for (String fieldPart : fieldParts) {
            if (!currentNode.has(fieldPart)) {
                logger.debug("Field '{}' not found in payload at path '{}'", fieldPart, fieldName);
                return null;
            }
            currentNode = currentNode.get(fieldPart);
        }
        
        String extractedId = currentNode.asText();
        logger.debug("Extracted ID '{}' from field '{}'", extractedId, fieldName);
        return extractedId;
    }
}