package com.example.natsclient.service.registry;

import com.example.natsclient.model.ListenerResult;
import com.example.natsclient.service.NatsListenerService.ListenerStatus;
import io.nats.client.JetStreamSubscription;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Registry for managing active NATS listeners.
 * Follows Single Responsibility Principle - only responsible for listener lifecycle management.
 *
 * Supports Pull Consumer mode:
 * - Manages fetcher threads and running state
 * - Controls the start and stop of pull loops
 */
@Component
public class ListenerRegistry {

    private final ConcurrentMap<String, ListenerInfo> activeListeners = new ConcurrentHashMap<>();

    /**
     * Registers a Pull Consumer listener.
     *
     * @param subject The subject name
     * @param idFieldName The ID field name
     * @param subscription JetStream subscription
     * @param messageHandler Message handler
     * @param fetcherFuture Future of the fetcher thread
     * @param running Running flag to control the pull loop
     * @return Generated listener ID
     */

    /**
     * Registers a listener with a pre-generated ID.
     * Ensures ID consistency between fetcher and registry.
     */
    public void registerListenerWithId(String listenerId, String subject, String idFieldName,
                                      JetStreamSubscription subscription,
                                      Consumer<ListenerResult.MessageReceived> messageHandler,
                                      Future<?> fetcherFuture,
                                      AtomicBoolean running) {
        ListenerInfo listenerInfo = new ListenerInfo(
            listenerId, subject, idFieldName, subscription, messageHandler,
            fetcherFuture, running, Instant.now()
        );
        activeListeners.put(listenerId, listenerInfo);
    }
    public String registerListener(String subject, String idFieldName,
                                  JetStreamSubscription subscription,
                                  Consumer<ListenerResult.MessageReceived> messageHandler,
                                  Future<?> fetcherFuture,
                                  AtomicBoolean running) {
        String listenerId = generateListenerId();

        ListenerInfo listenerInfo = new ListenerInfo(
            listenerId, subject, idFieldName, subscription, messageHandler,
            fetcherFuture, running, Instant.now()
        );

        activeListeners.put(listenerId, listenerInfo);
        return listenerId;
    }
    
    /**
     * Unregisters and returns a listener.
     * 
     * @param listenerId The listener ID
     * @return The listener info, or null if not found
     */
    public ListenerInfo unregisterListener(String listenerId) {
        ListenerInfo listener = activeListeners.remove(listenerId);
        if (listener != null) {
            return listener.markAsStopped();
        }
        return null;
    }
    
    /**
     * Gets all registered listener IDs.
     */
    public List<String> getAllListenerIds() {
        return List.copyOf(activeListeners.keySet());
    }
    
    /**
     * Gets the status of all listeners.
     */
    public List<ListenerStatus> getAllListenerStatuses() {
        return activeListeners.values().stream()
            .map(this::toListenerStatus)
            .toList();
    }
    
    /**
     * Checks if any listener is active for the given subject.
     */
    public boolean hasActiveListenerFor(String subject) {
        return activeListeners.values().stream()
            .anyMatch(listener -> listener.subject().equals(subject) && listener.isActive());
    }
    
    /**
     * Clears all listeners.
     */
    public void clearAll() {
        activeListeners.clear();
    }
    
    /**
     * Gets the count of active listeners.
     */
    public int getActiveListenerCount() {
        return activeListeners.size();
    }
    
    public String generateListenerId() {
        return "listener-" + UUID.randomUUID().toString();
    }
    
    private ListenerStatus toListenerStatus(ListenerInfo listener) {
        return new ListenerStatus(
            listener.listenerId(),
            listener.subject(),
            listener.idFieldName(),
            listener.status(),
            0L, // simplified - no message counting
            listener.startTime(),
            null // simplified - no last message time
        );
    }
    
    /**
     * Immutable record representing Pull Consumer listener information.
     *
     * @param fetcherFuture Future of the fetcher thread, used to cancel the fetching task
     * @param running Atomic boolean flag to control the pull loop execution
     */
    public record ListenerInfo(
        String listenerId,
        String subject,
        String idFieldName,
        JetStreamSubscription subscription,
        Consumer<ListenerResult.MessageReceived> messageHandler,
        Future<?> fetcherFuture,
        AtomicBoolean running,
        Instant startTime,
        String status
    ) {
        public ListenerInfo(String listenerId, String subject, String idFieldName,
                           JetStreamSubscription subscription,
                           Consumer<ListenerResult.MessageReceived> messageHandler,
                           Future<?> fetcherFuture,
                           AtomicBoolean running,
                           Instant startTime) {
            this(listenerId, subject, idFieldName, subscription, messageHandler,
                 fetcherFuture, running, startTime, "ACTIVE");
        }

        public boolean isActive() {
            return "ACTIVE".equals(status);
        }

        public ListenerInfo markAsStopped() {
            return new ListenerInfo(listenerId, subject, idFieldName, subscription,
                                  messageHandler, fetcherFuture, running, startTime, "STOPPED");
        }
    }
}