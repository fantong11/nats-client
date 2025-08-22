package com.example.natsclient.service.strategy.impl;

import com.example.natsclient.exception.NatsRequestException;
import com.example.natsclient.service.strategy.RetryStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Exponential backoff retry strategy implementation.
 * Increases delay exponentially with each retry attempt.
 */
@Component("exponentialBackoffRetryStrategy")
public class ExponentialBackoffRetryStrategy implements RetryStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(ExponentialBackoffRetryStrategy.class);
    
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final Duration DEFAULT_INITIAL_DELAY = Duration.ofSeconds(1);
    private static final double DEFAULT_MULTIPLIER = 2.0;
    private static final Duration DEFAULT_MAX_DELAY = Duration.ofMinutes(1);
    
    private final int maxAttempts;
    private final Duration initialDelay;
    private final double multiplier;
    private final Duration maxDelay;
    
    public ExponentialBackoffRetryStrategy() {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_INITIAL_DELAY, DEFAULT_MULTIPLIER, DEFAULT_MAX_DELAY);
    }
    
    public ExponentialBackoffRetryStrategy(int maxAttempts, Duration initialDelay, double multiplier, Duration maxDelay) {
        this.maxAttempts = maxAttempts;
        this.initialDelay = initialDelay;
        this.multiplier = multiplier;
        this.maxDelay = maxDelay;
    }
    
    @Override
    public boolean shouldRetry(Exception exception, int attemptNumber) {
        if (attemptNumber >= maxAttempts) {
            return false;
        }
        
        // Retry for NATS-related exceptions
        if (exception instanceof NatsRequestException) {
            return true;
        }
        
        // Retry for network-related exceptions
        if (isNetworkException(exception)) {
            return true;
        }
        
        // Don't retry for validation errors or other non-transient issues
        if (isNonRetriableException(exception)) {
            return false;
        }
        
        // Default: retry for RuntimeException and its subclasses
        return exception instanceof RuntimeException;
    }
    
    @Override
    public Duration calculateDelay(int attemptNumber) {
        if (attemptNumber <= 1) {
            return initialDelay;
        }
        
        // Calculate exponential delay: initialDelay * multiplier^(attemptNumber-1)
        long delayMillis = (long) (initialDelay.toMillis() * Math.pow(multiplier, attemptNumber - 1));
        Duration calculatedDelay = Duration.ofMillis(delayMillis);
        
        // Cap at maximum delay
        Duration delay = calculatedDelay.compareTo(maxDelay) > 0 ? maxDelay : calculatedDelay;
        
        logger.debug("Calculated exponential backoff delay for attempt {}: {}ms", attemptNumber, delay.toMillis());
        return delay;
    }
    
    @Override
    public int getMaxAttempts() {
        return maxAttempts;
    }
    
    @Override
    public String getStrategyName() {
        return "ExponentialBackoff";
    }
    
    @Override
    public void onRetryExhausted(Exception exception, int totalAttempts) {
        logger.warn("Exponential backoff retry strategy exhausted after {} attempts. Final exception: {}", 
                   totalAttempts, exception.getMessage());
    }
    
    @Override
    public void onBeforeRetry(Exception exception, int attemptNumber) {
        logger.info("Exponential backoff retry attempt {} due to: {}", attemptNumber, exception.getMessage());
    }
    
    /**
     * Checks if the exception is network-related and potentially transient.
     */
    private boolean isNetworkException(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("connection") ||
               lowerMessage.contains("timeout") ||
               lowerMessage.contains("network") ||
               lowerMessage.contains("socket");
    }
    
    /**
     * Checks if the exception should not be retried (e.g., validation errors).
     */
    private boolean isNonRetriableException(Exception exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("validation") ||
               lowerMessage.contains("invalid") ||
               lowerMessage.contains("illegal") ||
               exception instanceof IllegalArgumentException ||
               exception instanceof IllegalStateException;
    }
}