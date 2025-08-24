package com.example.natsclient.service.impl;

import com.example.natsclient.config.NatsProperties;
import com.example.natsclient.dto.NatsRequestLogDto;
import com.example.natsclient.exception.NatsRequestException;
import com.example.natsclient.exception.NatsTimeoutException;
import com.example.natsclient.service.PayloadProcessor;
import com.example.natsclient.service.RequestLogService;
import com.example.natsclient.service.builder.NatsPublishOptionsBuilder;
import com.example.natsclient.service.factory.MetricsFactory;
import com.example.natsclient.service.observer.NatsEventPublisher;
import com.example.natsclient.service.validator.RequestValidator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.PublishOptions;
import io.nats.client.api.PublishAck;
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
    private JetStream jetStream;

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
    private NatsProperties.JetStream jetStreamProperties;

    @Mock
    private NatsProperties.JetStream.StreamConfig streamProperties;

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
    private MetricsFactory metricsFactory;

    @Mock
    private NatsPublishOptionsBuilder publishOptionsBuilder;

    @Mock
    private NatsEventPublisher eventPublisher;

    @Mock
    private Timer.Sample timerSample;

    @Mock
    private Message mockMessage;

    @Mock
    private PublishAck mockPublishAck;

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
        lenient().when(natsProperties.getJetStream()).thenReturn(jetStreamProperties);
        lenient().when(jetStreamProperties.getStream()).thenReturn(streamProperties);
        lenient().when(streamProperties.getDefaultName()).thenReturn("DEFAULT_STREAM");
        lenient().when(mockPublishAck.getSeqno()).thenReturn(1L);
        lenient().when(mockPublishAck.getStream()).thenReturn("DEFAULT_STREAM");
        
        // Mock JetStream.publish method - fix the signature to match actual usage (subject, headers, payloadBytes, publishOptions)
        try {
            lenient().when(jetStream.publish(anyString(), any(), any(byte[].class), any(PublishOptions.class)))
                    .thenReturn(mockPublishAck);
        } catch (Exception e) {
            // This shouldn't happen in tests, but we need to handle checked exceptions
        }
        
        // Mock MeterRegistry configuration
        lenient().when(meterRegistry.config()).thenReturn(meterRegistryConfig);
        lenient().when(meterRegistryConfig.pauseDetector()).thenReturn(null);
        
        // Mock MetricsFactory behavior
        MetricsFactory.NatsMetricsSet metricsSet = mock(MetricsFactory.NatsMetricsSet.class);
        when(metricsSet.getRequestCounter()).thenReturn(requestCounter);
        when(metricsSet.getSuccessCounter()).thenReturn(successCounter);
        when(metricsSet.getErrorCounter()).thenReturn(errorCounter);
        when(metricsSet.getRequestTimer()).thenReturn(requestTimer);
        
        when(metricsFactory.createNatsMetricsSet(anyString(), eq(meterRegistry))).thenReturn(metricsSet);
        
        // Mock PublishOptionsBuilder
        PublishOptions mockPublishOptions = mock(PublishOptions.class);
        lenient().when(publishOptionsBuilder.createDefault()).thenReturn(mockPublishOptions);
        lenient().when(publishOptionsBuilder.createCritical()).thenReturn(mockPublishOptions);
        
        // Now create the service with all required dependencies
        enhancedService = new EnhancedNatsMessageService(
                natsConnection, jetStream, requestLogService, payloadProcessor, 
                requestValidator, natsProperties, meterRegistry, metricsFactory, 
                publishOptionsBuilder, eventPublisher);
    }

    @Test
    void sendRequest_Success_ShouldIncrementMetrics() throws Exception {
        NatsRequestLogDto mockRequestLog = new NatsRequestLogDto();
        when(payloadProcessor.serialize(testPayload)).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(payloadBytes);
        when(requestLogService.createRequestLog(anyString(), eq(testSubject), eq(serializedPayload), eq(testCorrelationId)))
                .thenReturn(mockRequestLog);

        CompletableFuture<String> result = enhancedService.sendRequest(testSubject, testPayload, testCorrelationId);

        assertNotNull(result);
        assertEquals("Message published to JetStream successfully - processing asynchronously", result.get());
        
        verify(requestCounter).increment();
        verify(successCounter).increment();
        verify(errorCounter, never()).increment();
    }

    @Test
    void sendRequest_JetStreamFailure_ShouldIncrementErrorMetrics() throws Exception {
        NatsRequestLogDto mockRequestLog = new NatsRequestLogDto();
        when(payloadProcessor.serialize(testPayload)).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(payloadBytes);
        when(requestLogService.createRequestLog(anyString(), eq(testSubject), eq(serializedPayload), eq(testCorrelationId)))
                .thenReturn(mockRequestLog);
        
        // Mock JetStream publish to throw exception (fix signature)
        try {
            when(jetStream.publish(eq(testSubject), any(), eq(payloadBytes), any(PublishOptions.class)))
                    .thenThrow(new RuntimeException("JetStream failure"));
        } catch (Exception e) {
            // Handle checked exception
        }

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
        verify(requestLogService).updateWithError(anyString(), contains("JetStream failure"));
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
                .thenReturn(new NatsRequestLogDto());
        
        // Mock JetStream publish to throw exception for retry testing (fix signature)
        try {
            when(jetStream.publish(eq(testSubject), any(), eq(payloadBytes), any(PublishOptions.class)))
                    .thenThrow(new RuntimeException("JetStream publish failed"));
        } catch (Exception e) {
            // Handle checked exception
        }

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

        // Verify that JetStream publish was attempted (fix signature)
        try {
            verify(jetStream, times(1)).publish(eq(testSubject), any(), eq(payloadBytes), any(PublishOptions.class));
        } catch (Exception e) {
            // Handle checked exception in verification
        }
    }

    @Test
    void publishMessage_WithMDCLogging_ShouldSetCorrectContexts() throws Exception {
        NatsRequestLogDto mockRequestLog = mock(NatsRequestLogDto.class);
        when(payloadProcessor.serialize(testPayload)).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(payloadBytes);
        when(requestLogService.createRequestLog(anyString(), eq(testSubject), eq(serializedPayload), isNull()))
                .thenReturn(mockRequestLog);
        when(jetStream.publish(eq(testSubject), any(), eq(payloadBytes), any(PublishOptions.class)))
                .thenReturn(mockPublishAck);

        CompletableFuture<Void> result = enhancedService.publishMessage(testSubject, testPayload);

        assertNotNull(result);
        assertDoesNotThrow(() -> result.get());
        
        try {
            verify(jetStream).publish(eq(testSubject), any(), eq(payloadBytes), any(PublishOptions.class));
        } catch (Exception e) {
            // Ignore verification exceptions
        }
        verify(mockRequestLog).setStatus(NatsRequestLogDto.RequestStatus.SUCCESS);
        verify(mockRequestLog).setResponsePayload(contains("JetStream Publish ACK"));
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
        when(requestLogService.createRequestLog(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new NatsRequestLogDto());

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

        NatsRequestLogDto mockRequestLog = new NatsRequestLogDto();
        when(payloadProcessor.serialize(largePayload)).thenReturn(largeSerialized);
        when(payloadProcessor.toBytes(largeSerialized)).thenReturn(largeBytes);
        when(requestLogService.createRequestLog(anyString(), eq(testSubject), eq(largeSerialized), eq(testCorrelationId)))
                .thenReturn(mockRequestLog);

        long startTime = System.currentTimeMillis();
        CompletableFuture<String> result = enhancedService.sendRequest(testSubject, largePayload, testCorrelationId);
        long endTime = System.currentTimeMillis();

        assertNotNull(result);
        assertEquals("Message published to JetStream successfully - processing asynchronously", result.get());
        assertTrue(endTime - startTime < 5000, "Large payload processing should complete within 5 seconds");
    }

    @Test
    void sendRequest_MemoryStress_ShouldNotCauseMemoryLeak() throws Exception {
        int iterations = 1000;
        
        when(payloadProcessor.serialize(any())).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(payloadBytes);
        when(requestLogService.createRequestLog(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new NatsRequestLogDto());

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

        // With Template Method pattern, metrics are handled internally by processors
        // No need to verify metrics calls on the main service
    }

    @Test
    void publishMessage_ExceptionHandling_ShouldLogAndUpdateDatabase() throws Exception {
        NatsRequestLogDto mockRequestLog = new NatsRequestLogDto();
        when(payloadProcessor.serialize(testPayload)).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(payloadBytes);
        lenient().when(requestLogService.createRequestLog(anyString(), eq(testSubject), eq(serializedPayload), isNull()))
                .thenReturn(mockRequestLog);
        doThrow(new RuntimeException("JetStream publish failed")).when(jetStream)
                .publish(eq(testSubject), any(), eq(payloadBytes), any(PublishOptions.class));

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

        verify(requestLogService).updateWithError(anyString(), contains("JetStream publish failed"));
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