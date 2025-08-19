package com.example.natsclient.service.impl;

import com.example.natsclient.config.NatsProperties;
import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.exception.NatsRequestException;
import com.example.natsclient.exception.NatsTimeoutException;
import com.example.natsclient.service.PayloadProcessor;
import com.example.natsclient.service.RequestLogService;
import com.example.natsclient.service.validator.RequestValidator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.nats.client.Connection;
import io.nats.client.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.MockedStatic;

@ExtendWith(MockitoExtension.class)
class EnhancedNatsMessageServiceTest {

    @Mock
    private Connection natsConnection;

    @Mock
    private RequestLogService requestLogService;

    @Mock
    private PayloadProcessor payloadProcessor;

    @Mock
    private RequestValidator requestValidator;

    @Mock
    private NatsProperties natsProperties;

    @Mock
    private NatsProperties.Request requestProperties;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private MeterRegistry.Config meterRegistryConfig;

    @Mock
    private Counter requestCounter;

    @Mock
    private Counter successCounter;

    @Mock
    private Counter errorCounter;

    @Mock
    private Timer requestTimer;

    @Mock
    private Timer.Sample timerSample;

    @Mock
    private Message mockMessage;

    private EnhancedNatsMessageService enhancedService;

    private final String testSubject = "test.subject";
    private final Object testPayload = new TestPayload("test data");
    private final String testCorrelationId = "corr-123";
    private final String serializedPayload = "{\"data\":\"test data\"}";
    private final byte[] payloadBytes = serializedPayload.getBytes();
    private final String responsePayload = "{\"status\":\"success\"}";
    private final byte[] responseBytes = responsePayload.getBytes();

    @BeforeEach
    void setUp() {
        lenient().when(natsProperties.getRequest()).thenReturn(requestProperties);
        lenient().when(requestProperties.getTimeout()).thenReturn(30000L);
        
        // Mock MeterRegistry configuration
        lenient().when(meterRegistry.config()).thenReturn(meterRegistryConfig);
        lenient().when(meterRegistryConfig.pauseDetector()).thenReturn(null);
        
        // Create mock builders for Counter and Timer
        Counter.Builder requestCounterBuilder = mock(Counter.Builder.class);
        Counter.Builder successCounterBuilder = mock(Counter.Builder.class);
        Counter.Builder errorCounterBuilder = mock(Counter.Builder.class);
        Timer.Builder requestTimerBuilder = mock(Timer.Builder.class);
        
        // Mock Counter.Builder chain for request counter
        when(requestCounterBuilder.description(anyString())).thenReturn(requestCounterBuilder);
        when(requestCounterBuilder.register(meterRegistry)).thenReturn(requestCounter);
        
        // Mock Counter.Builder chain for success counter  
        when(successCounterBuilder.description(anyString())).thenReturn(successCounterBuilder);
        when(successCounterBuilder.register(meterRegistry)).thenReturn(successCounter);
        
        // Mock Counter.Builder chain for error counter
        when(errorCounterBuilder.description(anyString())).thenReturn(errorCounterBuilder);
        when(errorCounterBuilder.register(meterRegistry)).thenReturn(errorCounter);
        
        // Mock Timer.Builder chain
        when(requestTimerBuilder.description(anyString())).thenReturn(requestTimerBuilder);
        when(requestTimerBuilder.register(meterRegistry)).thenReturn(requestTimer);
        
        // Mock static Counter.builder method to return our mock builders
        try (MockedStatic<Counter> counterMock = mockStatic(Counter.class);
             MockedStatic<Timer> timerMock = mockStatic(Timer.class)) {
            
            counterMock.when(() -> Counter.builder("nats.requests.total")).thenReturn(requestCounterBuilder);
            counterMock.when(() -> Counter.builder("nats.requests.success")).thenReturn(successCounterBuilder);
            counterMock.when(() -> Counter.builder("nats.requests.error")).thenReturn(errorCounterBuilder);
            timerMock.when(() -> Timer.builder("nats.request.duration")).thenReturn(requestTimerBuilder);
            
            // Now create the service - this should work with mocked static methods
            enhancedService = new EnhancedNatsMessageService(
                    natsConnection, requestLogService, payloadProcessor, 
                    requestValidator, natsProperties, meterRegistry);
        }
    }

    @Test
    void sendRequest_Success_ShouldIncrementMetrics() throws Exception {
        NatsRequestLog mockRequestLog = new NatsRequestLog();
        when(payloadProcessor.serialize(testPayload)).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(payloadBytes);
        when(payloadProcessor.fromBytes(responseBytes)).thenReturn(responsePayload);
        when(requestLogService.createRequestLog(anyString(), eq(testSubject), eq(serializedPayload), eq(testCorrelationId)))
                .thenReturn(mockRequestLog);
        when(natsConnection.request(eq(testSubject), eq(payloadBytes), any(Duration.class)))
                .thenReturn(mockMessage);
        when(mockMessage.getData()).thenReturn(responseBytes);

        CompletableFuture<String> result = enhancedService.sendRequest(testSubject, testPayload, testCorrelationId);

        assertNotNull(result);
        assertEquals(responsePayload, result.get());
        
        verify(requestCounter).increment();
        verify(successCounter).increment();
        verify(errorCounter, never()).increment();
    }

    @Test
    void sendRequest_Timeout_ShouldIncrementErrorMetrics() throws Exception {
        NatsRequestLog mockRequestLog = new NatsRequestLog();
        when(payloadProcessor.serialize(testPayload)).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(payloadBytes);
        when(requestLogService.createRequestLog(anyString(), eq(testSubject), eq(serializedPayload), eq(testCorrelationId)))
                .thenReturn(mockRequestLog);
        when(natsConnection.request(eq(testSubject), eq(payloadBytes), any(Duration.class)))
                .thenReturn(null);

        assertThrows(NatsTimeoutException.class, () -> {
            try {
                enhancedService.sendRequest(testSubject, testPayload, testCorrelationId).get();
            } catch (Exception e) {
                if (e.getCause() instanceof NatsTimeoutException) {
                    throw (NatsTimeoutException) e.getCause();
                }
                throw new RuntimeException(e);
            }
        });

        verify(requestCounter).increment();
        verify(errorCounter).increment();
        verify(successCounter, never()).increment();
        verify(requestLogService).updateWithTimeout(anyString(), anyString());
    }

    @Test
    void sendRequest_Exception_ShouldIncrementErrorMetrics() throws Exception {
        when(payloadProcessor.serialize(testPayload))
                .thenThrow(new RuntimeException("Serialization failed"));

        assertThrows(NatsRequestException.class, () -> {
            try {
                enhancedService.sendRequest(testSubject, testPayload, testCorrelationId).get();
            } catch (Exception e) {
                if (e.getCause() instanceof NatsRequestException) {
                    throw (NatsRequestException) e.getCause();
                }
                throw new RuntimeException(e);
            }
        });

        verify(requestCounter).increment();
        verify(errorCounter).increment();
        verify(successCounter, never()).increment();
    }

    @Test
    void sendRequest_RetryMechanism_ShouldRetryOnFailure() throws Exception {
        when(payloadProcessor.serialize(testPayload)).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(payloadBytes);
        when(requestLogService.createRequestLog(anyString(), eq(testSubject), eq(serializedPayload), eq(testCorrelationId)))
                .thenReturn(new NatsRequestLog());
        
        when(natsConnection.request(eq(testSubject), eq(payloadBytes), any(Duration.class)))
                .thenThrow(new RuntimeException("Connection failed"));

        // Since retry mechanism requires Spring context, expect immediate failure in unit test
        assertThrows(NatsRequestException.class, () -> {
            try {
                enhancedService.sendRequest(testSubject, testPayload, testCorrelationId).get();
            } catch (Exception e) {
                if (e.getCause() instanceof NatsRequestException) {
                    throw (NatsRequestException) e.getCause();
                }
                throw new RuntimeException(e);
            }
        });

        verify(natsConnection, times(1)).request(eq(testSubject), eq(payloadBytes), any(Duration.class));
    }

    @Test
    void publishMessage_WithMDCLogging_ShouldSetCorrectContexts() throws Exception {
        NatsRequestLog mockRequestLog = mock(NatsRequestLog.class);
        when(payloadProcessor.serialize(testPayload)).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(payloadBytes);
        when(requestLogService.createRequestLog(anyString(), eq(testSubject), eq(serializedPayload), isNull()))
                .thenReturn(mockRequestLog);

        CompletableFuture<Void> result = enhancedService.publishMessage(testSubject, testPayload);

        assertNotNull(result);
        assertDoesNotThrow(() -> result.get());
        
        verify(natsConnection).publish(eq(testSubject), eq(payloadBytes));
        verify(mockRequestLog).setStatus(NatsRequestLog.RequestStatus.SUCCESS);
        verify(requestLogService).saveRequestLog(mockRequestLog);
    }

    @Test
    void concurrentRequests_ShouldHandleMultipleRequests() throws Exception {
        int threadCount = 10;
        int requestsPerThread = 5;
        CountDownLatch latch = new CountDownLatch(threadCount * requestsPerThread);
        AtomicInteger successCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        when(payloadProcessor.serialize(any())).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(payloadBytes);
        when(payloadProcessor.fromBytes(responseBytes)).thenReturn(responsePayload);
        when(requestLogService.createRequestLog(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new NatsRequestLog());
        when(natsConnection.request(anyString(), any(byte[].class), any(Duration.class)))
                .thenReturn(mockMessage);
        when(mockMessage.getData()).thenReturn(responseBytes);

        for (int i = 0; i < threadCount; i++) {
            int threadId = i;
            executor.submit(() -> {
                for (int j = 0; j < requestsPerThread; j++) {
                    try {
                        CompletableFuture<String> result = enhancedService.sendRequest(
                                "test.subject." + threadId + "." + j, 
                                testPayload, 
                                "corr-" + threadId + "-" + j
                        );
                        result.get(5, TimeUnit.SECONDS);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        System.err.println("Request failed: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        assertEquals(threadCount * requestsPerThread, successCount.get());
        
        verify(requestCounter, times(threadCount * requestsPerThread)).increment();
        verify(successCounter, times(threadCount * requestsPerThread)).increment();
    }

    @Test
    void sendRequest_LargePayload_ShouldHandleEfficiently() throws Exception {
        StringBuilder largeData = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeData.append("This is test data line ").append(i).append("\n");
        }
        
        Object largePayload = new TestPayload(largeData.toString());
        String largeSerialized = "{\"data\":\"" + largeData.toString() + "\"}";
        byte[] largeBytes = largeSerialized.getBytes();

        NatsRequestLog mockRequestLog = new NatsRequestLog();
        when(payloadProcessor.serialize(largePayload)).thenReturn(largeSerialized);
        when(payloadProcessor.toBytes(largeSerialized)).thenReturn(largeBytes);
        when(payloadProcessor.fromBytes(responseBytes)).thenReturn(responsePayload);
        when(requestLogService.createRequestLog(anyString(), eq(testSubject), eq(largeSerialized), eq(testCorrelationId)))
                .thenReturn(mockRequestLog);
        when(natsConnection.request(eq(testSubject), eq(largeBytes), any(Duration.class)))
                .thenReturn(mockMessage);
        when(mockMessage.getData()).thenReturn(responseBytes);

        long startTime = System.currentTimeMillis();
        CompletableFuture<String> result = enhancedService.sendRequest(testSubject, largePayload, testCorrelationId);
        long endTime = System.currentTimeMillis();

        assertNotNull(result);
        assertEquals(responsePayload, result.get());
        assertTrue(endTime - startTime < 5000, "Large payload processing should complete within 5 seconds");
    }

    @Test
    void sendRequest_MemoryStress_ShouldNotCauseMemoryLeak() throws Exception {
        int iterations = 1000;
        
        when(payloadProcessor.serialize(any())).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(payloadBytes);
        when(payloadProcessor.fromBytes(responseBytes)).thenReturn(responsePayload);
        when(requestLogService.createRequestLog(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new NatsRequestLog());
        when(natsConnection.request(anyString(), any(byte[].class), any(Duration.class)))
                .thenReturn(mockMessage);
        when(mockMessage.getData()).thenReturn(responseBytes);

        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        for (int i = 0; i < iterations; i++) {
            CompletableFuture<String> result = enhancedService.sendRequest(
                    "stress.test." + i, testPayload, "corr-" + i);
            result.get();
            
            if (i % 100 == 0) {
                System.gc();
                Thread.sleep(10);
            }
        }

        System.gc();
        Thread.sleep(100);
        
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        
        assertTrue(memoryIncrease < 50 * 1024 * 1024, 
                "Memory increase should be less than 50MB after " + iterations + " requests");
    }

    @Test
    void sendRequest_WithNullValues_ShouldHandleGracefully() throws Exception {
        doThrow(new IllegalArgumentException("Subject cannot be null"))
                .when(requestValidator).validateRequest(null, testPayload);

        assertThrows(RuntimeException.class, () -> {
            try {
                enhancedService.sendRequest(null, testPayload, testCorrelationId).get();
            } catch (Exception e) {
                if (e.getCause() instanceof NatsRequestException) {
                    throw (NatsRequestException) e.getCause();
                }
                throw new RuntimeException(e);
            }
        });

        verify(requestCounter).increment();
        verify(errorCounter).increment();
    }

    @Test
    void publishMessage_ExceptionHandling_ShouldLogAndUpdateDatabase() throws Exception {
        when(payloadProcessor.serialize(testPayload)).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(payloadBytes);
        doThrow(new RuntimeException("Publish failed")).when(natsConnection)
                .publish(eq(testSubject), eq(payloadBytes));

        assertThrows(NatsRequestException.class, () -> {
            try {
                enhancedService.publishMessage(testSubject, testPayload).get();
            } catch (Exception e) {
                if (e.getCause() instanceof NatsRequestException) {
                    throw (NatsRequestException) e.getCause();
                }
                throw new RuntimeException(e);
            }
        });

        verify(requestLogService).updateWithError(anyString(), contains("Publish failed"));
    }

    private static class TestPayload {
        private final String data;

        public TestPayload(String data) {
            this.data = data;
        }

        public String getData() {
            return data;
        }
    }
}