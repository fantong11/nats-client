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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 全面的 ListenerRegistry 单元测试 - Pull Consumer 版本
 * 测试各种场景包括 Future 和 AtomicBoolean 的管理
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
        Future<?> future = CompletableFuture.completedFuture(null);
        AtomicBoolean running = new AtomicBoolean(true);

        // When
        String listenerId = listenerRegistry.registerListener(
            subject, idFieldName, subscription1, messageHandler1, future, running);

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
        Future<?> future1 = CompletableFuture.completedFuture(null);
        Future<?> future2 = CompletableFuture.completedFuture(null);
        AtomicBoolean running1 = new AtomicBoolean(true);
        AtomicBoolean running2 = new AtomicBoolean(true);

        // When
        String listenerId1 = listenerRegistry.registerListener(
            subject1, idFieldName, subscription1, messageHandler1, future1, running1);
        String listenerId2 = listenerRegistry.registerListener(
            subject2, idFieldName, subscription2, messageHandler2, future2, running2);

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
        Future<?> future = CompletableFuture.completedFuture(null);
        AtomicBoolean running = new AtomicBoolean(true);
        String listenerId = listenerRegistry.registerListener(
            subject, idFieldName, subscription1, messageHandler1, future, running);

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
        assertEquals(future, unregisteredInfo.fetcherFuture());
        assertEquals(running, unregisteredInfo.running());
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
        Future<?> future1 = CompletableFuture.completedFuture(null);
        Future<?> future2 = CompletableFuture.completedFuture(null);
        AtomicBoolean running1 = new AtomicBoolean(true);
        AtomicBoolean running2 = new AtomicBoolean(true);

        String listenerId1 = listenerRegistry.registerListener(
            subject1, idFieldName, subscription1, messageHandler1, future1, running1);
        String listenerId2 = listenerRegistry.registerListener(
            subject2, idFieldName, subscription2, messageHandler2, future2, running2);

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
        Future<?> future1 = CompletableFuture.completedFuture(null);
        Future<?> future2 = CompletableFuture.completedFuture(null);
        AtomicBoolean running1 = new AtomicBoolean(true);
        AtomicBoolean running2 = new AtomicBoolean(true);

        String listenerId1 = listenerRegistry.registerListener(
            subject1, idFieldName1, subscription1, messageHandler1, future1, running1);
        String listenerId2 = listenerRegistry.registerListener(
            subject2, idFieldName2, subscription2, messageHandler2, future2, running2);

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
        Future<?> future = CompletableFuture.completedFuture(null);
        AtomicBoolean running = new AtomicBoolean(true);
        listenerRegistry.registerListener(subject, idFieldName, subscription1, messageHandler1, future, running);

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
        Future<?> future = CompletableFuture.completedFuture(null);
        AtomicBoolean running = new AtomicBoolean(true);
        listenerRegistry.registerListener(registeredSubject, idFieldName, subscription1, messageHandler1, future, running);

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
        Future<?> future = CompletableFuture.completedFuture(null);
        AtomicBoolean running = new AtomicBoolean(true);
        String listenerId = listenerRegistry.registerListener(
            subject, idFieldName, subscription1, messageHandler1, future, running);

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
        Future<?> future1 = CompletableFuture.completedFuture(null);
        Future<?> future2 = CompletableFuture.completedFuture(null);
        AtomicBoolean running1 = new AtomicBoolean(true);
        AtomicBoolean running2 = new AtomicBoolean(true);

        listenerRegistry.registerListener(subject1, idFieldName, subscription1, messageHandler1, future1, running1);
        listenerRegistry.registerListener(subject2, idFieldName, subscription2, messageHandler2, future2, running2);

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
        // Given
        Future<?> future1 = CompletableFuture.completedFuture(null);
        Future<?> future2 = CompletableFuture.completedFuture(null);
        AtomicBoolean running1 = new AtomicBoolean(true);
        AtomicBoolean running2 = new AtomicBoolean(true);

        // Initially should be 0
        assertEquals(0, listenerRegistry.getActiveListenerCount());

        // Register first listener
        String listenerId1 = listenerRegistry.registerListener(
            "subject1", "userId", subscription1, messageHandler1, future1, running1);
        assertEquals(1, listenerRegistry.getActiveListenerCount());

        // Register second listener
        String listenerId2 = listenerRegistry.registerListener(
            "subject2", "orderId", subscription2, messageHandler2, future2, running2);
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
        Future<?> future = CompletableFuture.completedFuture(null);
        AtomicBoolean running = new AtomicBoolean(true);

        // When
        ListenerRegistry.ListenerInfo info = new ListenerRegistry.ListenerInfo(
            "listener-123", subject, idFieldName, subscription1, messageHandler1,
            future, running, startTime);

        // Then
        assertEquals("listener-123", info.listenerId());
        assertEquals(subject, info.subject());
        assertEquals(idFieldName, info.idFieldName());
        assertEquals(subscription1, info.subscription());
        assertEquals(messageHandler1, info.messageHandler());
        assertEquals(future, info.fetcherFuture());
        assertEquals(running, info.running());
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
        Future<?> future = CompletableFuture.completedFuture(null);
        AtomicBoolean running = new AtomicBoolean(true);

        ListenerRegistry.ListenerInfo activeInfo = new ListenerRegistry.ListenerInfo(
            "listener-123", subject, idFieldName, subscription1, messageHandler1,
            future, running, startTime);

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
        assertEquals(activeInfo.fetcherFuture(), stoppedInfo.fetcherFuture());
        assertEquals(activeInfo.running(), stoppedInfo.running());
        assertEquals(activeInfo.startTime(), stoppedInfo.startTime());
    }

    @Test
    void listenerInfo_RunningFlag_CanBeModified() {
        // Given
        AtomicBoolean running = new AtomicBoolean(true);
        Future<?> future = CompletableFuture.completedFuture(null);

        ListenerRegistry.ListenerInfo info = new ListenerRegistry.ListenerInfo(
            "listener-123", "test.subject", "userId", subscription1, messageHandler1,
            future, running, Instant.now());

        // When
        assertTrue(info.running().get());
        info.running().set(false);

        // Then
        assertFalse(info.running().get());
    }

    @Test
    void listenerInfo_FutureCancellation_ShouldWork() {
        // Given
        CompletableFuture<Void> future = new CompletableFuture<>();
        AtomicBoolean running = new AtomicBoolean(true);

        ListenerRegistry.ListenerInfo info = new ListenerRegistry.ListenerInfo(
            "listener-123", "test.subject", "userId", subscription1, messageHandler1,
            future, running, Instant.now());

        // When
        assertFalse(future.isCancelled());
        info.fetcherFuture().cancel(true);

        // Then
        assertTrue(future.isCancelled());
    }
}
