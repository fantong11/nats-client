package com.example.natsclient.service.registry;

import com.example.natsclient.model.ListenerResult;
import com.example.natsclient.service.NatsListenerService.ListenerStatus;
import io.nats.client.JetStreamSubscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ListenerRegistry.
 * Tests the Single Responsibility Principle implementation for listener lifecycle management.
 */
@ExtendWith(MockitoExtension.class)
class ListenerRegistryTest {
    
    @Mock
    private JetStreamSubscription subscription1;
    
    @Mock
    private JetStreamSubscription subscription2;
    
    @Mock
    private JetStreamSubscription subscription3;
    
    @Mock
    private Consumer<ListenerResult.MessageReceived> messageHandler1;
    
    @Mock
    private Consumer<ListenerResult.MessageReceived> messageHandler2;
    
    private ListenerRegistry listenerRegistry;
    
    @BeforeEach
    void setUp() {
        listenerRegistry = new ListenerRegistry();
    }
    
    @Test
    void registerListener_WithValidParameters_ShouldReturnListenerId() {
        // Given
        String subject = "test.subject";
        String idFieldName = "userId";
        
        // When
        String listenerId = listenerRegistry.registerListener(
            subject, idFieldName, subscription1, messageHandler1);
        
        // Then
        assertNotNull(listenerId);
        assertTrue(listenerId.startsWith("listener-"));
        assertEquals(1, listenerRegistry.getActiveListenerCount());
    }
    
    @Test
    void registerListener_MultipleListeners_ShouldGenerateUniqueIds() {
        // Given
        String subject1 = "subject1";
        String subject2 = "subject2";
        String idFieldName = "userId";
        
        // When
        String listenerId1 = listenerRegistry.registerListener(
            subject1, idFieldName, subscription1, messageHandler1);
        String listenerId2 = listenerRegistry.registerListener(
            subject2, idFieldName, subscription2, messageHandler2);
        
        // Then
        assertNotNull(listenerId1);
        assertNotNull(listenerId2);
        assertNotEquals(listenerId1, listenerId2);
        assertEquals(2, listenerRegistry.getActiveListenerCount());
    }
    
    @Test
    void unregisterListener_WithExistingListener_ShouldReturnListenerInfo() {
        // Given
        String subject = "test.subject";
        String idFieldName = "userId";
        String listenerId = listenerRegistry.registerListener(
            subject, idFieldName, subscription1, messageHandler1);
        
        // When
        ListenerRegistry.ListenerInfo unregisteredInfo = 
            listenerRegistry.unregisterListener(listenerId);
        
        // Then
        assertNotNull(unregisteredInfo);
        assertEquals(listenerId, unregisteredInfo.listenerId());
        assertEquals(subject, unregisteredInfo.subject());
        assertEquals(idFieldName, unregisteredInfo.idFieldName());
        assertEquals(subscription1, unregisteredInfo.subscription());
        assertEquals(messageHandler1, unregisteredInfo.messageHandler());
        assertEquals("STOPPED", unregisteredInfo.status());
        assertEquals(0, listenerRegistry.getActiveListenerCount());
    }
    
    @Test
    void unregisterListener_WithNonExistentListener_ShouldReturnNull() {
        // Given
        String nonExistentListenerId = "non-existent-listener";
        
        // When
        ListenerRegistry.ListenerInfo unregisteredInfo = 
            listenerRegistry.unregisterListener(nonExistentListenerId);
        
        // Then
        assertNull(unregisteredInfo);
        assertEquals(0, listenerRegistry.getActiveListenerCount());
    }
    
    @Test
    void getAllListenerIds_WithMultipleListeners_ShouldReturnAllIds() {
        // Given
        String subject1 = "subject1";
        String subject2 = "subject2";
        String idFieldName = "userId";
        
        String listenerId1 = listenerRegistry.registerListener(
            subject1, idFieldName, subscription1, messageHandler1);
        String listenerId2 = listenerRegistry.registerListener(
            subject2, idFieldName, subscription2, messageHandler2);
        
        // When
        List<String> allIds = listenerRegistry.getAllListenerIds();
        
        // Then
        assertEquals(2, allIds.size());
        assertTrue(allIds.contains(listenerId1));
        assertTrue(allIds.contains(listenerId2));
    }
    
    @Test
    void getAllListenerIds_WithNoListeners_ShouldReturnEmptyList() {
        // When
        List<String> allIds = listenerRegistry.getAllListenerIds();
        
        // Then
        assertTrue(allIds.isEmpty());
    }
    
    @Test
    void getAllListenerStatuses_WithMultipleListeners_ShouldReturnAllStatuses() {
        // Given
        String subject1 = "orders.created";
        String subject2 = "users.updated";
        String idFieldName1 = "orderId";
        String idFieldName2 = "userId";
        
        String listenerId1 = listenerRegistry.registerListener(
            subject1, idFieldName1, subscription1, messageHandler1);
        String listenerId2 = listenerRegistry.registerListener(
            subject2, idFieldName2, subscription2, messageHandler2);
        
        // When
        List<ListenerStatus> statuses = listenerRegistry.getAllListenerStatuses();
        
        // Then
        assertEquals(2, statuses.size());
        
        ListenerStatus status1 = statuses.stream()
            .filter(s -> s.listenerId().equals(listenerId1))
            .findFirst()
            .orElse(null);
        ListenerStatus status2 = statuses.stream()
            .filter(s -> s.listenerId().equals(listenerId2))
            .findFirst()
            .orElse(null);
        
        assertNotNull(status1);
        assertEquals(subject1, status1.subject());
        assertEquals(idFieldName1, status1.idFieldName());
        assertEquals("ACTIVE", status1.status());
        assertEquals(0L, status1.messagesReceived());
        assertNotNull(status1.startTime());
        assertNull(status1.lastMessageTime());
        
        assertNotNull(status2);
        assertEquals(subject2, status2.subject());
        assertEquals(idFieldName2, status2.idFieldName());
        assertEquals("ACTIVE", status2.status());
    }
    
    @Test
    void getAllListenerStatuses_WithNoListeners_ShouldReturnEmptyList() {
        // When
        List<ListenerStatus> statuses = listenerRegistry.getAllListenerStatuses();
        
        // Then
        assertTrue(statuses.isEmpty());
    }
    
    @Test
    void hasActiveListenerFor_WithActiveListener_ShouldReturnTrue() {
        // Given
        String subject = "test.subject";
        String idFieldName = "userId";
        listenerRegistry.registerListener(subject, idFieldName, subscription1, messageHandler1);
        
        // When
        boolean hasActiveListener = listenerRegistry.hasActiveListenerFor(subject);
        
        // Then
        assertTrue(hasActiveListener);
    }
    
    @Test
    void hasActiveListenerFor_WithDifferentSubject_ShouldReturnFalse() {
        // Given
        String registeredSubject = "registered.subject";
        String querySubject = "different.subject";
        String idFieldName = "userId";
        listenerRegistry.registerListener(registeredSubject, idFieldName, subscription1, messageHandler1);
        
        // When
        boolean hasActiveListener = listenerRegistry.hasActiveListenerFor(querySubject);
        
        // Then
        assertFalse(hasActiveListener);
    }
    
    @Test
    void hasActiveListenerFor_WithStoppedListener_ShouldReturnFalse() {
        // Given
        String subject = "test.subject";
        String idFieldName = "userId";
        String listenerId = listenerRegistry.registerListener(
            subject, idFieldName, subscription1, messageHandler1);
        
        // Stop the listener
        listenerRegistry.unregisterListener(listenerId);
        
        // When
        boolean hasActiveListener = listenerRegistry.hasActiveListenerFor(subject);
        
        // Then
        assertFalse(hasActiveListener);
    }
    
    @Test
    void hasActiveListenerFor_WithNoListeners_ShouldReturnFalse() {
        // Given
        String subject = "test.subject";
        
        // When
        boolean hasActiveListener = listenerRegistry.hasActiveListenerFor(subject);
        
        // Then
        assertFalse(hasActiveListener);
    }
    
    @Test
    void clearAll_WithMultipleListeners_ShouldClearAll() {
        // Given
        String subject1 = "subject1";
        String subject2 = "subject2";
        String idFieldName = "userId";
        
        listenerRegistry.registerListener(subject1, idFieldName, subscription1, messageHandler1);
        listenerRegistry.registerListener(subject2, idFieldName, subscription2, messageHandler2);
        
        assertEquals(2, listenerRegistry.getActiveListenerCount());
        
        // When
        listenerRegistry.clearAll();
        
        // Then
        assertEquals(0, listenerRegistry.getActiveListenerCount());
        assertTrue(listenerRegistry.getAllListenerIds().isEmpty());
        assertTrue(listenerRegistry.getAllListenerStatuses().isEmpty());
    }
    
    @Test
    void clearAll_WithNoListeners_ShouldHandleGracefully() {
        // Given
        assertEquals(0, listenerRegistry.getActiveListenerCount());
        
        // When
        assertDoesNotThrow(() -> listenerRegistry.clearAll());
        
        // Then
        assertEquals(0, listenerRegistry.getActiveListenerCount());
    }
    
    @Test
    void getActiveListenerCount_ShouldReflectCurrentState() {
        // Initially should be 0
        assertEquals(0, listenerRegistry.getActiveListenerCount());
        
        // Register first listener
        String listenerId1 = listenerRegistry.registerListener(
            "subject1", "userId", subscription1, messageHandler1);
        assertEquals(1, listenerRegistry.getActiveListenerCount());
        
        // Register second listener
        String listenerId2 = listenerRegistry.registerListener(
            "subject2", "orderId", subscription2, messageHandler2);
        assertEquals(2, listenerRegistry.getActiveListenerCount());
        
        // Unregister one listener
        listenerRegistry.unregisterListener(listenerId1);
        assertEquals(1, listenerRegistry.getActiveListenerCount());
        
        // Unregister second listener
        listenerRegistry.unregisterListener(listenerId2);
        assertEquals(0, listenerRegistry.getActiveListenerCount());
    }
    
    @Test
    void listenerInfo_ShouldBeImmutable() {
        // Given
        String subject = "test.subject";
        String idFieldName = "userId";
        Instant startTime = Instant.now();
        
        // When
        ListenerRegistry.ListenerInfo info = new ListenerRegistry.ListenerInfo(
            "listener-123", subject, idFieldName, subscription1, messageHandler1, startTime);
        
        // Then
        assertEquals("listener-123", info.listenerId());
        assertEquals(subject, info.subject());
        assertEquals(idFieldName, info.idFieldName());
        assertEquals(subscription1, info.subscription());
        assertEquals(messageHandler1, info.messageHandler());
        assertEquals(startTime, info.startTime());
        assertEquals("ACTIVE", info.status());
        assertTrue(info.isActive());
    }
    
    @Test
    void listenerInfo_markAsStopped_ShouldReturnNewInstanceWithStoppedStatus() {
        // Given
        String subject = "test.subject";
        String idFieldName = "userId";
        Instant startTime = Instant.now();
        
        ListenerRegistry.ListenerInfo activeInfo = new ListenerRegistry.ListenerInfo(
            "listener-123", subject, idFieldName, subscription1, messageHandler1, startTime);
        
        // When
        ListenerRegistry.ListenerInfo stoppedInfo = activeInfo.markAsStopped();
        
        // Then
        // Original should be unchanged
        assertEquals("ACTIVE", activeInfo.status());
        assertTrue(activeInfo.isActive());
        
        // New instance should be stopped
        assertEquals("STOPPED", stoppedInfo.status());
        assertFalse(stoppedInfo.isActive());
        
        // Other fields should remain the same
        assertEquals(activeInfo.listenerId(), stoppedInfo.listenerId());
        assertEquals(activeInfo.subject(), stoppedInfo.subject());
        assertEquals(activeInfo.idFieldName(), stoppedInfo.idFieldName());
        assertEquals(activeInfo.subscription(), stoppedInfo.subscription());
        assertEquals(activeInfo.messageHandler(), stoppedInfo.messageHandler());
        assertEquals(activeInfo.startTime(), stoppedInfo.startTime());
    }
}