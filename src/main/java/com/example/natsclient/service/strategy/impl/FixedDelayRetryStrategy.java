package com.example.natsclient.service.strategy.impl;

import com.example.natsclient.exception.NatsRequestException;
import com.example.natsclient.service.strategy.RetryStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Fixed delay retry strategy implementation.
 * Uses a constant delay between retry attempts.
 */
@Component("fixedDelayRetryStrategy")
public class FixedDelayRetryStrategy implements RetryStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(FixedDelayRetryStrategy.class);
    
    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final Duration DEFAULT_DELAY = Duration.ofSeconds(2);
    
    private final int maxAttempts;
    private final Duration delay;
    
    public FixedDelayRetryStrategy() {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_DELAY);
    }
    
    public FixedDelayRetryStrategy(int maxAttempts, Duration delay) {
        this.maxAttempts = maxAttempts;
        this.delay = delay;
    }
    
    @Override
    public boolean shouldRetry(Exception exception, int attemptNumber) {
        if (attemptNumber >= maxAttempts) {
            return false;
        }
        
        // Retry for most exceptions except validation errors
        if (exception instanceof IllegalArgumentException || 
            exception instanceof IllegalStateException) {
            return false;
        }
        
        return true;
    }
    
    @Override
    public Duration calculateDelay(int attemptNumber) {
        logger.debug("Fixed delay retry strategy: using delay of {}ms for attempt {}", 
                    delay.toMillis(), attemptNumber);
        return delay;
    }
    
    @Override
    public int getMaxAttempts() {
        return maxAttempts;
    }
    
    @Override
    public String getStrategyName() {
        return "FixedDelay";
    }
    
    @Override
    public void onRetryExhausted(Exception exception, int totalAttempts) {
        logger.warn("Fixed delay retry strategy exhausted after {} attempts. Final exception: {}", 
                   totalAttempts, exception.getMessage());
    }
    
    @Override
    public void onBeforeRetry(Exception exception, int attemptNumber) {
        logger.info("Fixed delay retry attempt {} due to: {}", attemptNumber, exception.getMessage());
    }
}