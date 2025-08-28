package com.example.natsclient.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Utility class for extracting ID values from JSON payloads.
 * Supports nested field access using dot notation (e.g., "user.id", "order.details.id").
 */
@Component
public class JsonIdExtractor {
    
    private static final Logger logger = LoggerFactory.getLogger(JsonIdExtractor.class);
    
    private final ObjectMapper objectMapper;
    
    public JsonIdExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Extracts ID value from JSON string using the specified field path.
     * 
     * @param jsonPayload The JSON string to extract from
     * @param fieldPath The field path to extract (supports dot notation like "user.id")
     * @return The extracted ID value as string, or null if not found
     */
    public String extractId(String jsonPayload, String fieldPath) {
        if (jsonPayload == null || jsonPayload.trim().isEmpty()) {
            logger.debug("JSON payload is null or empty");
            return null;
        }
        
        if (fieldPath == null || fieldPath.trim().isEmpty()) {
            logger.debug("Field path is null or empty");
            return null;
        }
        
        try {
            JsonNode rootNode = objectMapper.readTree(jsonPayload);
            JsonNode targetNode = navigateToField(rootNode, fieldPath.trim());
            
            if (targetNode == null || targetNode.isNull()) {
                logger.debug("Field '{}' not found or is null in JSON payload", fieldPath);
                return null;
            }
            
            // Convert the field value to string
            String extractedId = targetNode.asText();
            logger.debug("Successfully extracted ID '{}' from field '{}'", extractedId, fieldPath);
            return extractedId;
            
        } catch (Exception e) {
            logger.error("Failed to extract ID from JSON payload using field path '{}': {}", fieldPath, e.getMessage());
            return null;
        }
    }
    
    /**
     * Navigates to a nested field in JSON using dot notation.
     * 
     * @param rootNode The root JSON node
     * @param fieldPath The field path with dot notation
     * @return The target JSON node, or null if not found
     */
    private JsonNode navigateToField(JsonNode rootNode, String fieldPath) {
        String[] pathParts = fieldPath.split("\\.");
        JsonNode currentNode = rootNode;
        
        for (String part : pathParts) {
            if (currentNode == null || !currentNode.has(part)) {
                return null;
            }
            currentNode = currentNode.get(part);
        }
        
        return currentNode;
    }
    
    /**
     * Validates if the JSON contains the specified field path.
     * 
     * @param jsonPayload The JSON string to validate
     * @param fieldPath The field path to check
     * @return true if field exists, false otherwise
     */
    public boolean hasField(String jsonPayload, String fieldPath) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonPayload);
            JsonNode targetNode = navigateToField(rootNode, fieldPath);
            return targetNode != null && !targetNode.isNull();
        } catch (Exception e) {
            logger.debug("Error checking field existence: {}", e.getMessage());
            return false;
        }
    }
}