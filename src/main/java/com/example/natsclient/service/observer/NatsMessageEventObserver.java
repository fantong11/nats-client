package com.example.natsclient.service.observer;

import com.example.natsclient.service.event.NatsMessageEvent;

/**
 * Observer interface for NATS message processing events.
 * Part of Observer Pattern implementation.
 */
public interface NatsMessageEventObserver {
    
    /**
     * Called when a NATS message event occurs.
     *
     * @param event The event that occurred
     */
    void onEvent(NatsMessageEvent event);
    
    /**
     * Gets the name of this observer for logging and identification purposes.
     *
     * @return Observer name
     */
    String getObserverName();
    
    /**
     * Determines if this observer is interested in the given event type.
     * This allows for efficient filtering before calling onEvent.
     *
     * @param eventType The type of event
     * @return true if interested in this event type, false otherwise
     */
    default boolean isInterestedIn(String eventType) {
        return true; // By default, observe all events
    }
    
    /**
     * Called when the observer is registered with the event publisher.
     */
    default void onRegistered() {
        // Default implementation does nothing
    }
    
    /**
     * Called when the observer is unregistered from the event publisher.
     */
    default void onUnregistered() {
        // Default implementation does nothing
    }
}