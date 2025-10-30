package com.example.natsclient.service.fetcher;

import com.example.natsclient.model.ListenerResult;
import com.example.natsclient.service.handler.MessageProcessor;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 全面的 PullMessageFetcher 单元测试
 * 测试各种场景和边界条件
 */
@ExtendWith(MockitoExtension.class)
class PullMessageFetcherTest {

    @Mock
    private MessageProcessor messageProcessor;

    @Mock
    private JetStreamSubscription subscription;

    @Mock
    private Message message1;

    @Mock
    private Message message2;

    @Mock
    private Message message3;

    @Mock
    private Consumer<ListenerResult.MessageReceived> messageHandler;

    private PullMessageFetcher fetcher;

    @BeforeEach
    void setUp() {
        fetcher = new PullMessageFetcher(messageProcessor);
    }

    @Test
    void startFetchingLoop_WithMessages_ShouldProcessSuccessfully() throws Exception {
        // Given
        String listenerId = "test-listener-1";
        String subject = "test.subject";
        String idFieldName = "id";
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger callCount = new AtomicInteger(0);

        // Mock subscription.iterate() 返回消息迭代器
        Iterator<Message> messageIterator = List.of(message1, message2).iterator();
        when(subscription.iterate(anyInt(), any(Duration.class)))
                .thenAnswer(invocation -> {
                    if (callCount.incrementAndGet() == 1) {
                        return messageIterator;
                    } else {
                        // 第二次调用时停止循环
                        running.set(false);
                        return Collections.emptyIterator();
                    }
                });

        // When
        fetcher.startFetchingLoop(listenerId, subject, idFieldName, subscription, messageHandler, running);

        // Then
        verify(subscription, atLeast(1)).iterate(anyInt(), any(Duration.class));
        verify(messageProcessor, times(2)).processMessage(
                eq(listenerId), eq(subject), eq(idFieldName), any(Message.class), eq(messageHandler)
        );
    }

    @Test
    void startFetchingLoop_WithNoMessages_ShouldHandleGracefully() throws Exception {
        // Given
        String listenerId = "test-listener-2";
        String subject = "test.subject";
        String idFieldName = "id";
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger callCount = new AtomicInteger(0);

        // Mock subscription.iterate() 返回空迭代器
        when(subscription.iterate(anyInt(), any(Duration.class)))
                .thenAnswer(invocation -> {
                    if (callCount.incrementAndGet() >= 2) {
                        running.set(false);
                    }
                    return Collections.emptyIterator();
                });

        // When
        fetcher.startFetchingLoop(listenerId, subject, idFieldName, subscription, messageHandler, running);

        // Then
        verify(subscription, atLeast(2)).iterate(anyInt(), any(Duration.class));
        verify(messageProcessor, never()).processMessage(any(), any(), any(), any(), any());
    }

    @Test
    void startFetchingLoop_WhenRunningStopped_ShouldExitLoop() throws Exception {
        // Given
        String listenerId = "test-listener-3";
        String subject = "test.subject";
        String idFieldName = "id";
        AtomicBoolean running = new AtomicBoolean(false); // 初始就设为 false

        // When
        fetcher.startFetchingLoop(listenerId, subject, idFieldName, subscription, messageHandler, running);

        // Then
        verify(subscription, never()).iterate(anyInt(), any(Duration.class));
        verify(messageProcessor, never()).processMessage(any(), any(), any(), any(), any());
    }

    @Test
    void startFetchingLoop_WithProcessingException_ShouldContinueProcessing() throws Exception {
        // Given
        String listenerId = "test-listener-4";
        String subject = "test.subject";
        String idFieldName = "id";
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger callCount = new AtomicInteger(0);

        Iterator<Message> messageIterator = List.of(message1, message2, message3).iterator();
        when(subscription.iterate(anyInt(), any(Duration.class)))
                .thenAnswer(invocation -> {
                    if (callCount.incrementAndGet() == 1) {
                        return messageIterator;
                    } else {
                        running.set(false);
                        return Collections.emptyIterator();
                    }
                });

        // Mock messageProcessor 处理第二条消息时抛出异常
        doNothing().when(messageProcessor).processMessage(
                eq(listenerId), eq(subject), eq(idFieldName), eq(message1), eq(messageHandler)
        );
        doThrow(new RuntimeException("Processing error")).when(messageProcessor).processMessage(
                eq(listenerId), eq(subject), eq(idFieldName), eq(message2), eq(messageHandler)
        );
        doNothing().when(messageProcessor).processMessage(
                eq(listenerId), eq(subject), eq(idFieldName), eq(message3), eq(messageHandler)
        );

        // When - 不应该抛出异常
        assertDoesNotThrow(() -> {
            fetcher.startFetchingLoop(listenerId, subject, idFieldName, subscription, messageHandler, running);
        });

        // Then - 所有消息都应该被尝试处理
        verify(messageProcessor, times(3)).processMessage(
                eq(listenerId), eq(subject), eq(idFieldName), any(Message.class), eq(messageHandler)
        );
    }

    @Test
    void startFetchingLoop_WithIterateException_ShouldHandleAndContinue() throws Exception {
        // Given
        String listenerId = "test-listener-5";
        String subject = "test.subject";
        String idFieldName = "id";
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger callCount = new AtomicInteger(0);

        when(subscription.iterate(anyInt(), any(Duration.class)))
                .thenAnswer(invocation -> {
                    int count = callCount.incrementAndGet();
                    if (count == 1) {
                        // 第一次调用抛出异常
                        throw new RuntimeException("Iterate error");
                    } else if (count == 2) {
                        // 第二次调用正常返回
                        return List.of(message1).iterator();
                    } else {
                        running.set(false);
                        return Collections.emptyIterator();
                    }
                });

        // When - 不应该抛出异常
        assertDoesNotThrow(() -> {
            fetcher.startFetchingLoop(listenerId, subject, idFieldName, subscription, messageHandler, running);
        });

        // Then - 应该继续尝试拉取并处理后续消息
        verify(subscription, atLeast(2)).iterate(anyInt(), any(Duration.class));
        verify(messageProcessor, times(1)).processMessage(
                eq(listenerId), eq(subject), eq(idFieldName), eq(message1), eq(messageHandler)
        );
    }

    @Test
    void startFetchingLoop_WithMultipleBatches_ShouldProcessAllBatches() throws Exception {
        // Given
        String listenerId = "test-listener-6";
        String subject = "test.subject";
        String idFieldName = "id";
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger callCount = new AtomicInteger(0);

        when(subscription.iterate(anyInt(), any(Duration.class)))
                .thenAnswer(invocation -> {
                    int count = callCount.incrementAndGet();
                    if (count == 1) {
                        // 第一批：2条消息
                        return List.of(message1, message2).iterator();
                    } else if (count == 2) {
                        // 第二批：1条消息
                        return List.of(message3).iterator();
                    } else {
                        // 停止循环
                        running.set(false);
                        return Collections.emptyIterator();
                    }
                });

        // When
        fetcher.startFetchingLoop(listenerId, subject, idFieldName, subscription, messageHandler, running);

        // Then
        verify(subscription, atLeast(2)).iterate(anyInt(), any(Duration.class));
        verify(messageProcessor, times(3)).processMessage(
                eq(listenerId), eq(subject), eq(idFieldName), any(Message.class), eq(messageHandler)
        );
    }

    @Test
    void constructor_WithNullMessageProcessor_ShouldThrowException() {
        // When & Then
        assertThrows(NullPointerException.class, () -> {
            new PullMessageFetcher(null);
        });
    }

    @Test
    void startFetchingLoop_WithNullParameters_ShouldHandleGracefully() {
        // Given
        AtomicBoolean running = new AtomicBoolean(true);

        // When & Then - 测试各种 null 参数
        assertThrows(Exception.class, () -> {
            fetcher.startFetchingLoop(null, "subject", "idField", subscription, messageHandler, running);
        });
    }

    @Test
    void startFetchingLoop_WithEmptyListenerId_ShouldStillWork() throws Exception {
        // Given
        String listenerId = "";
        String subject = "test.subject";
        String idFieldName = "id";
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger callCount = new AtomicInteger(0);

        when(subscription.iterate(anyInt(), any(Duration.class)))
                .thenAnswer(invocation -> {
                    if (callCount.incrementAndGet() >= 1) {
                        running.set(false);
                    }
                    return Collections.emptyIterator();
                });

        // When - 应该不抛出异常
        assertDoesNotThrow(() -> {
            fetcher.startFetchingLoop(listenerId, subject, idFieldName, subscription, messageHandler, running);
        });

        // Then
        verify(subscription, atLeast(1)).iterate(anyInt(), any(Duration.class));
    }

    @Test
    void startFetchingLoop_StopMidProcessing_ShouldExitCleanly() throws Exception {
        // Given
        String listenerId = "test-listener-7";
        String subject = "test.subject";
        String idFieldName = "id";
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger callCount = new AtomicInteger(0);

        when(subscription.iterate(anyInt(), any(Duration.class)))
                .thenAnswer(invocation -> {
                    int count = callCount.incrementAndGet();
                    if (count == 1) {
                        return List.of(message1, message2).iterator();
                    } else {
                        // 第二次调用时已经停止
                        return Collections.emptyIterator();
                    }
                });

        // Mock 在处理第一条消息后停止
        doAnswer(invocation -> {
            running.set(false); // 处理第一条消息后设置停止标志
            return null;
        }).when(messageProcessor).processMessage(
                eq(listenerId), eq(subject), eq(idFieldName), eq(message1), eq(messageHandler)
        );

        // When
        fetcher.startFetchingLoop(listenerId, subject, idFieldName, subscription, messageHandler, running);

        // Then - 应该正常退出
        verify(subscription, atLeast(1)).iterate(anyInt(), any(Duration.class));
    }
}
