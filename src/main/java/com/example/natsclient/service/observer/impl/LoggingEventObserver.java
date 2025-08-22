package com.example.natsclient.service.observer.impl;

import com.example.natsclient.service.event.NatsMessageEvent;
import com.example.natsclient.service.event.impl.MessageFailedEvent;
import com.example.natsclient.service.event.impl.MessageRetryEvent;
import com.example.natsclient.service.observer.NatsMessageEventObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Observer that logs NATS message processing events.
 * Provides detailed logging for debugging and monitoring purposes.
 */
@Component
public class LoggingEventObserver implements NatsMessageEventObserver {
    
    private static final Logger logger = LoggerFactory.getLogger(LoggingEventObserver.class);
    
    @Override
    public void onEvent(NatsMessageEvent event) {
        switch (event.getEventType()) {
            case "MESSAGE_STARTED":
                logger.info("🚀 Started: {} | Subject: {} | RequestId: {}", 
                           event.getOperationType(), event.getSubject(), event.getRequestId());
                break;
                
            case "MESSAGE_COMPLETED":
                logger.info("✅ Completed: {} | Subject: {} | RequestId: {} | Time: {}ms", 
                           event.getOperationType(), event.getSubject(), event.getRequestId(),
                           event.getMetadata().get("processingTime"));
                break;
                
            case "MESSAGE_FAILED":
                MessageFailedEvent failedEvent = (MessageFailedEvent) event;
                logger.error("❌ Failed: {} | Subject: {} | RequestId: {} | Attempt: {} | Error: {}", 
                            event.getOperationType(), event.getSubject(), event.getRequestId(),
                            failedEvent.getAttemptNumber(), failedEvent.getException().getMessage());
                break;
                
            case "MESSAGE_RETRY":
                MessageRetryEvent retryEvent = (MessageRetryEvent) event;
                logger.warn("🔄 Retry: {} | Subject: {} | RequestId: {} | Attempt: {} | Delay: {}ms | Strategy: {}", 
                           event.getOperationType(), event.getSubject(), event.getRequestId(),
                           retryEvent.getAttemptNumber(), retryEvent.getRetryDelay().toMillis(),
                           retryEvent.getRetryStrategy());
                break;
                
            default:
                logger.debug("📝 Event: {} | {}", event.getEventType(), event.getDescription());
        }
    }
    
    @Override
    public String getObserverName() {
        return "LoggingEventObserver";
    }
    
    @Override
    public void onRegistered() {
        logger.info("📊 Logging event observer registered for NATS message events");
    }
    
    @Override
    public void onUnregistered() {
        logger.info("📊 Logging event observer unregistered");
    }
}