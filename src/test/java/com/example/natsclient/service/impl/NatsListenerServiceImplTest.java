package com.example.natsclient.service.impl;

import com.example.natsclient.model.ListenerResult;
import com.example.natsclient.service.NatsListenerService.ListenerStatus;
import com.example.natsclient.service.config.ConsumerConfigurationFactory;
import com.example.natsclient.service.handler.MessageProcessor;
import com.example.natsclient.service.registry.ListenerRegistry;
import io.nats.client.*;
import io.nats.client.api.ConsumerConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NatsListenerServiceImpl following Clean Code + SOLID principles.
 * Tests the orchestration logic and dependency interactions.
 */
@ExtendWith(MockitoExtension.class)
class NatsListenerServiceImplTest {
    
    @Mock
    private Connection natsConnection;
    
    @Mock
    private JetStream jetStream;
    
    @Mock
    private ConsumerConfigurationFactory configFactory;
    
    @Mock
    private MessageProcessor messageProcessor;
    
    @Mock
    private ListenerRegistry listenerRegistry;
    
    @Mock
    private Dispatcher dispatcher;
    
    @Mock
    private JetStreamSubscription subscription;
    
    @Mock
    private ConsumerConfiguration consumerConfig;
    
    private NatsListenerServiceImpl listenerService;
    
    @BeforeEach
    void setUp() {
        listenerService = new NatsListenerServiceImpl(
            natsConnection,
            jetStream,
            configFactory,
            messageProcessor,
            listenerRegistry
        );
    }
    
    @Test
    void startListener_WithValidInputs_ShouldStartSuccessfully() throws Exception {
        // Given
        String subject = "test.subject";
        String idFieldName = "userId";
        String listenerId = "listener-123";
        String consumerName = "durable-consumer-test-subject";
        Consumer<ListenerResult.MessageReceived> messageHandler = msg -> {};
        
        when(configFactory.createDurableConsumerConfig(subject)).thenReturn(consumerConfig);
        when(configFactory.generateDurableConsumerName(subject)).thenReturn(consumerName);
        when(natsConnection.createDispatcher()).thenReturn(dispatcher);
        when(jetStream.subscribe(eq(subject), eq(dispatcher), any(MessageHandler.class), eq(false), any()))
            .thenReturn(subscription);
        when(listenerRegistry.registerListener(subject, idFieldName, subscription, messageHandler))
            .thenReturn(listenerId);
        
        // When
        CompletableFuture<String> result = listenerService.startListener(subject, idFieldName, messageHandler);
        String actualListenerId = result.get();
        
        // Then
        assertEquals(listenerId, actualListenerId);
        verify(configFactory).createDurableConsumerConfig(subject);
        verify(configFactory).generateDurableConsumerName(subject);
        verify(natsConnection).createDispatcher();
        verify(jetStream).subscribe(eq(subject), eq(dispatcher), any(MessageHandler.class), eq(false), any());
        verify(listenerRegistry).registerListener(subject, idFieldName, subscription, messageHandler);
    }
    
    @Test
    void startListener_WithNullSubject_ShouldThrowException() {
        // Given
        String subject = null;
        String idFieldName = "userId";
        Consumer<ListenerResult.MessageReceived> messageHandler = msg -> {};
        
        // When & Then
        CompletableFuture<String> future = listenerService.startListener(subject, idFieldName, messageHandler);
        
        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        verifyNoInteractions(configFactory, jetStream, listenerRegistry);
    }
    
    @Test
    void startListener_WithEmptySubject_ShouldThrowException() {
        // Given
        String subject = "";
        String idFieldName = "userId";
        Consumer<ListenerResult.MessageReceived> messageHandler = msg -> {};
        
        // When & Then
        CompletableFuture<String> future = listenerService.startListener(subject, idFieldName, messageHandler);
        
        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        verifyNoInteractions(configFactory, jetStream, listenerRegistry);
    }
    
    @Test
    void startListener_WithNullIdFieldName_ShouldThrowException() {
        // Given
        String subject = "test.subject";
        String idFieldName = null;
        Consumer<ListenerResult.MessageReceived> messageHandler = msg -> {};
        
        // When & Then
        CompletableFuture<String> future = listenerService.startListener(subject, idFieldName, messageHandler);
        
        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        verifyNoInteractions(configFactory, jetStream, listenerRegistry);
    }
    
    @Test
    void startListener_WithNullMessageHandler_ShouldThrowException() {
        // Given
        String subject = "test.subject";
        String idFieldName = "userId";
        Consumer<ListenerResult.MessageReceived> messageHandler = null;
        
        // When & Then
        CompletableFuture<String> future = listenerService.startListener(subject, idFieldName, messageHandler);
        
        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        verifyNoInteractions(configFactory, jetStream, listenerRegistry);
    }
    
    @Test
    void startListener_WhenJetStreamThrowsException_ShouldThrowListenerStartupException() throws Exception {
        // Given
        String subject = "test.subject";
        String idFieldName = "userId";
        Consumer<ListenerResult.MessageReceived> messageHandler = msg -> {};
        
        when(configFactory.createDurableConsumerConfig(subject)).thenReturn(consumerConfig);
        when(configFactory.generateDurableConsumerName(subject)).thenReturn("consumer-name");
        when(natsConnection.createDispatcher()).thenReturn(dispatcher);
        when(jetStream.subscribe(any(), any(), any(MessageHandler.class), anyBoolean(), any()))
            .thenThrow(new RuntimeException("JetStream error"));
        
        // When & Then
        CompletableFuture<String> future = listenerService.startListener(subject, idFieldName, messageHandler);
        
        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        assertTrue(exception.getCause() instanceof NatsListenerServiceImpl.ListenerStartupException);
        verify(configFactory).createDurableConsumerConfig(subject);
        verifyNoInteractions(listenerRegistry);
    }
    
    @Test
    void stopListener_WithValidListenerId_ShouldStopSuccessfully() throws Exception {
        // Given
        String listenerId = "listener-123";
        ListenerRegistry.ListenerInfo listenerInfo = new ListenerRegistry.ListenerInfo(
            listenerId, "test.subject", "userId", subscription, msg -> {}, Instant.now()
        );
        
        when(listenerRegistry.unregisterListener(listenerId)).thenReturn(listenerInfo);
        
        // When
        CompletableFuture<Void> result = listenerService.stopListener(listenerId);
        result.get();
        
        // Then
        verify(listenerRegistry).unregisterListener(listenerId);
        verify(subscription).unsubscribe();
    }
    
    @Test
    void stopListener_WithNonExistentListenerId_ShouldComplete() throws Exception {
        // Given
        String listenerId = "non-existent";
        
        when(listenerRegistry.unregisterListener(listenerId)).thenReturn(null);
        
        // When
        CompletableFuture<Void> result = listenerService.stopListener(listenerId);
        result.get();
        
        // Then
        verify(listenerRegistry).unregisterListener(listenerId);
        verifyNoInteractions(subscription);
    }
    
    @Test
    void stopListener_WithNullListenerId_ShouldThrowException() {
        // Given
        String listenerId = null;
        
        // When & Then
        CompletableFuture<Void> future = listenerService.stopListener(listenerId);
        
        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        verifyNoInteractions(listenerRegistry);
    }
    
    @Test
    void stopListener_WhenUnsubscribeThrowsException_ShouldHandleGracefully() throws Exception {
        // Given
        String listenerId = "listener-123";
        ListenerRegistry.ListenerInfo listenerInfo = new ListenerRegistry.ListenerInfo(
            listenerId, "test.subject", "userId", subscription, msg -> {}, Instant.now()
        );
        
        when(listenerRegistry.unregisterListener(listenerId)).thenReturn(listenerInfo);
        doThrow(new RuntimeException("Unsubscribe failed")).when(subscription).unsubscribe();
        
        // When
        CompletableFuture<Void> result = listenerService.stopListener(listenerId);
        result.get(); // Should not throw
        
        // Then
        verify(listenerRegistry).unregisterListener(listenerId);
        verify(subscription).unsubscribe();
    }
    
    @Test
    void stopAllListeners_WithMultipleListeners_ShouldStopAll() throws Exception {
        // Given
        List<String> listenerIds = List.of("listener-1", "listener-2", "listener-3");
        
        when(listenerRegistry.getActiveListenerCount()).thenReturn(3);
        when(listenerRegistry.getAllListenerIds()).thenReturn(listenerIds);
        
        ListenerRegistry.ListenerInfo listener1 = new ListenerRegistry.ListenerInfo(
            "listener-1", "subject1", "userId", mock(JetStreamSubscription.class), msg -> {}, Instant.now()
        );
        ListenerRegistry.ListenerInfo listener2 = new ListenerRegistry.ListenerInfo(
            "listener-2", "subject2", "userId", mock(JetStreamSubscription.class), msg -> {}, Instant.now()
        );
        ListenerRegistry.ListenerInfo listener3 = new ListenerRegistry.ListenerInfo(
            "listener-3", "subject3", "userId", mock(JetStreamSubscription.class), msg -> {}, Instant.now()
        );
        
        when(listenerRegistry.unregisterListener("listener-1")).thenReturn(listener1);
        when(listenerRegistry.unregisterListener("listener-2")).thenReturn(listener2);
        when(listenerRegistry.unregisterListener("listener-3")).thenReturn(listener3);
        
        // When
        CompletableFuture<Void> result = listenerService.stopAllListeners();
        result.get();
        
        // Then
        verify(listenerRegistry).getActiveListenerCount();
        verify(listenerRegistry).getAllListenerIds();
        verify(listenerRegistry).unregisterListener("listener-1");
        verify(listenerRegistry).unregisterListener("listener-2");
        verify(listenerRegistry).unregisterListener("listener-3");
        verify(listenerRegistry).clearAll();
        
        verify(listener1.subscription()).unsubscribe();
        verify(listener2.subscription()).unsubscribe();
        verify(listener3.subscription()).unsubscribe();
    }
    
    @Test
    void stopAllListeners_WithNoActiveListeners_ShouldCompleteSuccessfully() throws Exception {
        // Given
        when(listenerRegistry.getActiveListenerCount()).thenReturn(0);
        when(listenerRegistry.getAllListenerIds()).thenReturn(List.of());
        
        // When
        CompletableFuture<Void> result = listenerService.stopAllListeners();
        result.get();
        
        // Then
        verify(listenerRegistry).getActiveListenerCount();
        verify(listenerRegistry).getAllListenerIds();
        verify(listenerRegistry).clearAll();
    }
    
    @Test
    void getListenerStatus_ShouldReturnAllStatuses() throws Exception {
        // Given
        List<ListenerStatus> expectedStatuses = List.of(
            new ListenerStatus("listener-1", "subject1", "userId", "ACTIVE", 0L, Instant.now(), null),
            new ListenerStatus("listener-2", "subject2", "orderId", "ACTIVE", 0L, Instant.now(), null)
        );
        
        when(listenerRegistry.getAllListenerStatuses()).thenReturn(expectedStatuses);
        
        // When
        CompletableFuture<List<ListenerStatus>> result = listenerService.getListenerStatus();
        List<ListenerStatus> actualStatuses = result.get();
        
        // Then
        assertEquals(expectedStatuses.size(), actualStatuses.size());
        assertEquals(expectedStatuses, actualStatuses);
        verify(listenerRegistry).getAllListenerStatuses();
    }
    
    @Test
    void isListenerActive_WithActiveListener_ShouldReturnTrue() {
        // Given
        String subject = "test.subject";
        
        when(listenerRegistry.hasActiveListenerFor(subject)).thenReturn(true);
        
        // When
        boolean isActive = listenerService.isListenerActive(subject);
        
        // Then
        assertTrue(isActive);
        verify(listenerRegistry).hasActiveListenerFor(subject);
    }
    
    @Test
    void isListenerActive_WithNoActiveListener_ShouldReturnFalse() {
        // Given
        String subject = "test.subject";
        
        when(listenerRegistry.hasActiveListenerFor(subject)).thenReturn(false);
        
        // When
        boolean isActive = listenerService.isListenerActive(subject);
        
        // Then
        assertFalse(isActive);
        verify(listenerRegistry).hasActiveListenerFor(subject);
    }
    
    @Test
    void isListenerActive_WithNullSubject_ShouldThrowException() {
        // Given
        String subject = null;
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            listenerService.isListenerActive(subject));
        
        verifyNoInteractions(listenerRegistry);
    }
    
    @Test
    void isListenerActive_WithEmptySubject_ShouldThrowException() {
        // Given
        String subject = "";
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
            listenerService.isListenerActive(subject));
        
        verifyNoInteractions(listenerRegistry);
    }
}