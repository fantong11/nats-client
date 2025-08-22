package com.example.natsclient.service.factory;

import com.example.natsclient.service.strategy.RetryStrategy;
import com.example.natsclient.service.strategy.impl.ExponentialBackoffRetryStrategy;
import com.example.natsclient.service.strategy.impl.FixedDelayRetryStrategy;
import com.example.natsclient.service.strategy.impl.NoRetryStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Factory for creating and managing retry strategies.
 * Combines Factory Pattern with Strategy Pattern for flexible retry behavior selection.
 */
@Component
public class RetryStrategyFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(RetryStrategyFactory.class);
    
    private final Map<String, RetryStrategy> strategies;
    
    @Autowired
    public RetryStrategyFactory(
            ExponentialBackoffRetryStrategy exponentialBackoffStrategy,
            FixedDelayRetryStrategy fixedDelayStrategy,
            NoRetryStrategy noRetryStrategy) {
        
        this.strategies = Map.of(
                "exponential", exponentialBackoffStrategy,
                "fixed", fixedDelayStrategy,
                "none", noRetryStrategy,
                "default", exponentialBackoffStrategy // Default strategy
        );
        
        logger.info("RetryStrategyFactory initialized with {} strategies: {}", 
                   strategies.size(), strategies.keySet());
    }
    
    /**
     * Gets a retry strategy by name.
     *
     * @param strategyName The name of the strategy ("exponential", "fixed", "none", "default")
     * @return The corresponding retry strategy
     * @throws IllegalArgumentException if strategy name is not recognized
     */
    public RetryStrategy getStrategy(String strategyName) {
        if (strategyName == null || strategyName.trim().isEmpty()) {
            logger.debug("No strategy name provided, using default strategy");
            return getDefaultStrategy();
        }
        
        String normalizedName = strategyName.toLowerCase().trim();
        RetryStrategy strategy = strategies.get(normalizedName);
        
        if (strategy == null) {
            logger.warn("Unknown retry strategy '{}', falling back to default", strategyName);
            return getDefaultStrategy();
        }
        
        logger.debug("Selected retry strategy: {} ({})", strategy.getStrategyName(), normalizedName);
        return strategy;
    }
    
    /**
     * Gets the default retry strategy (exponential backoff).
     */
    public RetryStrategy getDefaultStrategy() {
        return strategies.get("default");
    }
    
    /**
     * Gets a strategy appropriate for the operation type.
     *
     * @param operationType The type of operation ("request", "publish", "critical")
     * @return An appropriate retry strategy
     */
    public RetryStrategy getStrategyForOperation(String operationType) {
        if (operationType == null) {
            return getDefaultStrategy();
        }
        
        switch (operationType.toLowerCase()) {
            case "request":
            case "critical":
                // Use exponential backoff for critical operations
                return getStrategy("exponential");
                
            case "publish":
                // Use fixed delay for publish operations (typically high volume)
                return getStrategy("fixed");
                
            case "batch":
            case "background":
                // Use no retry for batch operations to avoid blocking
                return getStrategy("none");
                
            default:
                logger.debug("Unknown operation type '{}', using default strategy", operationType);
                return getDefaultStrategy();
        }
    }
    
    /**
     * Gets a strategy based on the exception type.
     *
     * @param exception The exception that occurred
     * @return An appropriate retry strategy
     */
    public RetryStrategy getStrategyForException(Exception exception) {
        if (exception == null) {
            return getDefaultStrategy();
        }
        
        String message = exception.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            
            // Use no retry for validation errors
            if (lowerMessage.contains("validation") || lowerMessage.contains("invalid")) {
                return getStrategy("none");
            }
            
            // Use exponential backoff for network issues
            if (lowerMessage.contains("network") || lowerMessage.contains("connection")) {
                return getStrategy("exponential");
            }
            
            // Use fixed delay for timeout issues
            if (lowerMessage.contains("timeout")) {
                return getStrategy("fixed");
            }
        }
        
        // Default strategy for unknown exceptions
        return getDefaultStrategy();
    }
    
    /**
     * Lists all available strategy names.
     */
    public String[] getAvailableStrategies() {
        return strategies.keySet().toArray(new String[0]);
    }
}