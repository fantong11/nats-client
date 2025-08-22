package com.example.natsclient.service.strategy;

import java.time.Duration;

/**
 * Strategy interface for different retry approaches.
 * Implements Strategy Pattern to provide flexible retry behavior for different types of operations and errors.
 */
public interface RetryStrategy {
    
    /**
     * Determines whether a retry should be attempted for the given exception and attempt number.
     *
     * @param exception The exception that occurred
     * @param attemptNumber The current attempt number (starting from 1)
     * @return true if a retry should be attempted, false otherwise
     */
    boolean shouldRetry(Exception exception, int attemptNumber);
    
    /**
     * Calculates the delay before the next retry attempt.
     *
     * @param attemptNumber The current attempt number (starting from 1)
     * @return The duration to wait before the next retry
     */
    Duration calculateDelay(int attemptNumber);
    
    /**
     * Gets the maximum number of retry attempts allowed.
     *
     * @return Maximum number of retry attempts
     */
    int getMaxAttempts();
    
    /**
     * Gets a human-readable name for this retry strategy.
     *
     * @return Strategy name for logging and debugging
     */
    String getStrategyName();
    
    /**
     * Called when all retry attempts have been exhausted.
     * Can be used for logging, cleanup, or notification purposes.
     *
     * @param exception The final exception that caused the failure
     * @param totalAttempts The total number of attempts made
     */
    default void onRetryExhausted(Exception exception, int totalAttempts) {
        // Default implementation does nothing
    }
    
    /**
     * Called before each retry attempt.
     * Can be used for logging or state adjustment.
     *
     * @param exception The exception from the previous attempt
     * @param attemptNumber The upcoming attempt number
     */
    default void onBeforeRetry(Exception exception, int attemptNumber) {
        // Default implementation does nothing
    }
}