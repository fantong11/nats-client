package com.example.natsclient.config;

import com.example.natsclient.service.observer.NatsEventPublisher;
import com.example.natsclient.service.observer.impl.LoggingEventObserver;
import com.example.natsclient.service.observer.impl.MetricsEventObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Configuration class for setting up Observer Pattern components.
 * Automatically registers default observers with the event publisher.
 */
@Configuration
public class ObserverConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(ObserverConfiguration.class);
    
    private final NatsEventPublisher eventPublisher;
    private final LoggingEventObserver loggingObserver;
    private final MetricsEventObserver metricsObserver;
    
    public ObserverConfiguration(NatsEventPublisher eventPublisher,
                                LoggingEventObserver loggingObserver,
                                MetricsEventObserver metricsObserver) {
        this.eventPublisher = eventPublisher;
        this.loggingObserver = loggingObserver;
        this.metricsObserver = metricsObserver;
    }
    
    @PostConstruct
    public void setupObservers() {
        logger.info("Setting up NATS message event observers...");
        
        // Register default observers
        eventPublisher.registerObserver(loggingObserver);
        eventPublisher.registerObserver(metricsObserver);
        
        logger.info("Observer Pattern setup complete - {} observers registered", 
                   eventPublisher.getObserverCount());
    }
}