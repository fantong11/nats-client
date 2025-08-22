package com.example.natsclient.service;

import com.example.natsclient.service.factory.RetryStrategyFactory;
import com.example.natsclient.service.strategy.RetryStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Generic retry executor that uses Strategy Pattern for flexible retry behavior.
 * Supports both synchronous and asynchronous retry execution.
 */
@Component
public class RetryExecutor {
    
    private static final Logger logger = LoggerFactory.getLogger(RetryExecutor.class);
    
    private final RetryStrategyFactory strategyFactory;
    private final ScheduledExecutorService scheduler;
    
    public RetryExecutor(RetryStrategyFactory strategyFactory) {
        this.strategyFactory = strategyFactory;
        this.scheduler = Executors.newScheduledThreadPool(2); // Small pool for retry scheduling
    }
    
    /**
     * Executes an operation with retry logic using the specified strategy.
     *
     * @param operation The operation to execute
     * @param strategyName The name of the retry strategy to use
     * @param operationName Name for logging purposes
     * @return CompletableFuture with the result
     */
    public <T> CompletableFuture<T> executeWithRetry(
            Supplier<T> operation, 
            String strategyName, 
            String operationName) {
        
        RetryStrategy strategy = strategyFactory.getStrategy(strategyName);
        return executeWithRetry(operation, strategy, operationName);
    }
    
    /**
     * Executes an operation with retry logic using the provided strategy.
     *
     * @param operation The operation to execute
     * @param strategy The retry strategy to use
     * @param operationName Name for logging purposes
     * @return CompletableFuture with the result
     */
    public <T> CompletableFuture<T> executeWithRetry(
            Supplier<T> operation, 
            RetryStrategy strategy, 
            String operationName) {
        
        return executeWithRetryInternal(operation, strategy, operationName, 1);
    }
    
    /**
     * Executes an operation with automatic strategy selection based on operation type.
     *
     * @param operation The operation to execute
     * @param operationType The type of operation (used for strategy selection)
     * @param operationName Name for logging purposes
     * @return CompletableFuture with the result
     */
    public <T> CompletableFuture<T> executeWithAutoRetry(
            Supplier<T> operation, 
            String operationType, 
            String operationName) {
        
        RetryStrategy strategy = strategyFactory.getStrategyForOperation(operationType);
        return executeWithRetry(operation, strategy, operationName);
    }
    
    /**
     * Internal method that handles the actual retry logic recursively.
     */
    private <T> CompletableFuture<T> executeWithRetryInternal(
            Supplier<T> operation,
            RetryStrategy strategy,
            String operationName,
            int attemptNumber) {
        
        CompletableFuture<T> result = new CompletableFuture<>();
        
        try {
            logger.debug("Executing {} (attempt {})", operationName, attemptNumber);
            T operationResult = operation.get();
            result.complete(operationResult);
            
            if (attemptNumber > 1) {
                logger.info("{} succeeded on attempt {} using strategy {}", 
                           operationName, attemptNumber, strategy.getStrategyName());
            }
            
        } catch (Exception exception) {
            logger.debug("{} failed on attempt {}: {}", operationName, attemptNumber, exception.getMessage());
            
            if (strategy.shouldRetry(exception, attemptNumber)) {
                // Calculate delay and schedule retry
                var delay = strategy.calculateDelay(attemptNumber);
                strategy.onBeforeRetry(exception, attemptNumber + 1);
                
                logger.info("Retrying {} in {}ms (attempt {} of {})", 
                           operationName, delay.toMillis(), attemptNumber + 1, strategy.getMaxAttempts());
                
                scheduler.schedule(() -> {
                    executeWithRetryInternal(operation, strategy, operationName, attemptNumber + 1)
                            .whenComplete((res, ex) -> {
                                if (ex != null) {
                                    result.completeExceptionally(ex);
                                } else {
                                    result.complete(res);
                                }
                            });
                }, delay.toMillis(), TimeUnit.MILLISECONDS);
                
            } else {
                // No more retries
                strategy.onRetryExhausted(exception, attemptNumber);
                
                logger.warn("{} failed after {} attempts using strategy {}. Final error: {}", 
                           operationName, attemptNumber, strategy.getStrategyName(), exception.getMessage());
                
                result.completeExceptionally(exception);
            }
        }
        
        return result;
    }
    
    /**
     * Executes an operation with retry logic based on the exception type.
     *
     * @param operation The operation to execute
     * @param lastException The exception from the previous attempt (for strategy selection)
     * @param operationName Name for logging purposes
     * @return CompletableFuture with the result
     */
    public <T> CompletableFuture<T> executeWithExceptionBasedRetry(
            Supplier<T> operation,
            Exception lastException,
            String operationName) {
        
        RetryStrategy strategy = strategyFactory.getStrategyForException(lastException);
        return executeWithRetry(operation, strategy, operationName);
    }
    
    /**
     * Shutdown the internal scheduler when the application stops.
     */
    public void shutdown() {
        logger.info("Shutting down RetryExecutor scheduler");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}