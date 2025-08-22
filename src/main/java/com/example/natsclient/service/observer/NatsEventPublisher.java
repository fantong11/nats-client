package com.example.natsclient.service.observer;

import com.example.natsclient.service.event.NatsMessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Event publisher for NATS message processing events.
 * Implements the Subject part of the Observer Pattern.
 */
@Component
public class NatsEventPublisher {
    
    private static final Logger logger = LoggerFactory.getLogger(NatsEventPublisher.class);
    
    private final List<NatsMessageEventObserver> observers = new CopyOnWriteArrayList<>();
    private final ExecutorService eventExecutor = Executors.newFixedThreadPool(3);
    
    /**
     * Registers an observer to receive event notifications.
     *
     * @param observer The observer to register
     */
    public void registerObserver(NatsMessageEventObserver observer) {
        if (observer == null) {
            logger.warn("Attempted to register null observer");
            return;
        }
        
        if (observers.contains(observer)) {
            logger.debug("Observer {} is already registered", observer.getObserverName());
            return;
        }
        
        observers.add(observer);
        observer.onRegistered();
        
        logger.info("Registered observer: {} (total observers: {})", 
                   observer.getObserverName(), observers.size());
    }
    
    /**
     * Unregisters an observer from receiving event notifications.
     *
     * @param observer The observer to unregister
     */
    public void unregisterObserver(NatsMessageEventObserver observer) {
        if (observer == null) {
            return;
        }
        
        boolean removed = observers.remove(observer);
        if (removed) {
            observer.onUnregistered();
            logger.info("Unregistered observer: {} (total observers: {})", 
                       observer.getObserverName(), observers.size());
        } else {
            logger.debug("Observer {} was not registered", observer.getObserverName());
        }
    }
    
    /**
     * Publishes an event to all registered observers asynchronously.
     *
     * @param event The event to publish
     */
    public void publishEvent(NatsMessageEvent event) {
        if (event == null) {
            logger.warn("Attempted to publish null event");
            return;
        }
        
        if (observers.isEmpty()) {
            logger.debug("No observers registered for event: {}", event.getEventType());
            return;
        }
        
        logger.debug("Publishing event {} to {} observers", event.getEventType(), observers.size());
        
        // Notify observers asynchronously to avoid blocking the main processing
        eventExecutor.submit(() -> notifyObservers(event));
    }
    
    /**
     * Publishes an event to all registered observers synchronously.
     * Use this for critical events where you need to ensure all observers are notified
     * before continuing.
     *
     * @param event The event to publish
     */
    public void publishEventSync(NatsMessageEvent event) {
        if (event == null) {
            logger.warn("Attempted to publish null event");
            return;
        }
        
        notifyObservers(event);
    }
    
    /**
     * Internal method to notify all observers about an event.
     */
    private void notifyObservers(NatsMessageEvent event) {
        int notifiedCount = 0;
        int filteredCount = 0;
        
        for (NatsMessageEventObserver observer : observers) {
            try {
                if (observer.isInterestedIn(event.getEventType())) {
                    observer.onEvent(event);
                    notifiedCount++;
                } else {
                    filteredCount++;
                }
            } catch (Exception e) {
                logger.error("Error notifying observer {} about event {}: {}", 
                           observer.getObserverName(), event.getEventType(), e.getMessage(), e);
            }
        }
        
        logger.debug("Event {} notified to {} observers ({} filtered out)", 
                    event.getEventType(), notifiedCount, filteredCount);
    }
    
    /**
     * Gets the number of currently registered observers.
     */
    public int getObserverCount() {
        return observers.size();
    }
    
    /**
     * Gets the names of all registered observers.
     */
    public String[] getObserverNames() {
        return observers.stream()
                       .map(NatsMessageEventObserver::getObserverName)
                       .toArray(String[]::new);
    }
    
    /**
     * Shutdown the event executor when the application stops.
     */
    public void shutdown() {
        logger.info("Shutting down NatsEventPublisher");
        eventExecutor.shutdown();
        observers.clear();
    }
}