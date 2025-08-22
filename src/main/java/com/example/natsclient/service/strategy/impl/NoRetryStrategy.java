package com.example.natsclient.service.strategy.impl;

import com.example.natsclient.service.strategy.RetryStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * No retry strategy implementation.
 * Does not retry any failed operations - fail fast approach.
 */
@Component("noRetryStrategy")
public class NoRetryStrategy implements RetryStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(NoRetryStrategy.class);
    
    @Override
    public boolean shouldRetry(Exception exception, int attemptNumber) {
        return false; // Never retry
    }
    
    @Override
    public Duration calculateDelay(int attemptNumber) {
        return Duration.ZERO; // No delay needed since we don't retry
    }
    
    @Override
    public int getMaxAttempts() {
        return 1; // Only one attempt
    }
    
    @Override
    public String getStrategyName() {
        return "NoRetry";
    }
    
    @Override
    public void onRetryExhausted(Exception exception, int totalAttempts) {
        logger.debug("No retry strategy - operation failed immediately: {}", exception.getMessage());
    }
    
    @Override
    public void onBeforeRetry(Exception exception, int attemptNumber) {
        // This should never be called since shouldRetry always returns false
        logger.warn("onBeforeRetry called on NoRetryStrategy - this should not happen");
    }
}