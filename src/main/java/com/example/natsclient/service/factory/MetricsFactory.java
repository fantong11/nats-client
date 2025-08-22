package com.example.natsclient.service.factory;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Factory class for creating and managing Micrometer metrics.
 * Implements Factory Pattern to centralize metrics creation logic and provide fallback handling.
 */
@Component
public class MetricsFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsFactory.class);
    private static final SimpleMeterRegistry FALLBACK_REGISTRY = new SimpleMeterRegistry();
    
    /**
     * Creates a Counter with fallback handling.
     * If registration fails, returns a no-op counter using SimpleMeterRegistry.
     */
    public Counter createCounter(String name, String description, MeterRegistry registry) {
        try {
            Counter counter = Counter.builder(name)
                    .description(description)
                    .register(registry);
            
            logger.debug("Successfully created counter: {}", name);
            return counter;
            
        } catch (Exception e) {
            logger.warn("Failed to register counter {}: {}. Using fallback counter.", name, e.getMessage());
            return Counter.builder(name)
                    .description(description)
                    .register(FALLBACK_REGISTRY);
        }
    }
    
    /**
     * Creates a Timer with fallback handling.
     * If registration fails, returns a no-op timer using SimpleMeterRegistry.
     */
    public Timer createTimer(String name, String description, MeterRegistry registry) {
        try {
            Timer timer = Timer.builder(name)
                    .description(description)
                    .register(registry);
            
            logger.debug("Successfully created timer: {}", name);
            return timer;
            
        } catch (Exception e) {
            logger.warn("Failed to register timer {}: {}. Using fallback timer.", name, e.getMessage());
            return Timer.builder(name)
                    .description(description)
                    .register(FALLBACK_REGISTRY);
        }
    }
    
    /**
     * Creates a complete set of NATS-related metrics for a given component.
     * This is a convenience method that creates commonly used metrics.
     */
    public NatsMetricsSet createNatsMetricsSet(String componentName, MeterRegistry registry) {
        String prefix = "nats." + componentName.toLowerCase() + ".";
        
        Counter requestCounter = createCounter(
                prefix + "requests.total", 
                "Total number of " + componentName + " requests", 
                registry);
        
        Counter successCounter = createCounter(
                prefix + "requests.success", 
                "Number of successful " + componentName + " requests", 
                registry);
        
        Counter errorCounter = createCounter(
                prefix + "requests.error", 
                "Number of failed " + componentName + " requests", 
                registry);
        
        Timer requestTimer = createTimer(
                prefix + "request.duration", 
                componentName + " request duration", 
                registry);
        
        logger.info("Created complete metrics set for component: {}", componentName);
        
        return new NatsMetricsSet(requestCounter, successCounter, errorCounter, requestTimer);
    }
    
    /**
     * Data class to hold a complete set of NATS metrics.
     */
    public static class NatsMetricsSet {
        private final Counter requestCounter;
        private final Counter successCounter;
        private final Counter errorCounter;
        private final Timer requestTimer;
        
        public NatsMetricsSet(Counter requestCounter, Counter successCounter, 
                             Counter errorCounter, Timer requestTimer) {
            this.requestCounter = requestCounter;
            this.successCounter = successCounter;
            this.errorCounter = errorCounter;
            this.requestTimer = requestTimer;
        }
        
        public Counter getRequestCounter() { return requestCounter; }
        public Counter getSuccessCounter() { return successCounter; }
        public Counter getErrorCounter() { return errorCounter; }
        public Timer getRequestTimer() { return requestTimer; }
    }
}