package com.example.natsclient.performance;

import com.example.natsclient.config.NatsProperties;
import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.service.PayloadProcessor;
import com.example.natsclient.service.RequestLogService;
import com.example.natsclient.service.builder.NatsPublishOptionsBuilder;
import com.example.natsclient.service.factory.MetricsFactory;
import com.example.natsclient.service.impl.EnhancedNatsMessageService;
import com.example.natsclient.service.observer.NatsEventPublisher;
import com.example.natsclient.service.validator.RequestValidator;
import io.micrometer.core.instrument.MeterRegistry;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.PublishOptions;
import io.nats.client.api.PublishAck;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NatsPerformanceTest {

    @Mock
    private Connection natsConnection;

    @Mock
    private JetStream jetStream;

    @Mock
    private PublishAck mockPublishAck;

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
    private Message mockMessage;

    @Mock
    private MetricsFactory metricsFactory;

    @Mock
    private NatsPublishOptionsBuilder publishOptionsBuilder;

    @Mock
    private NatsEventPublisher eventPublisher;

    private EnhancedNatsMessageService natsService;

    private final String testSubject = "performance.test";
    private final Object testPayload = new PerformanceTestPayload("performance test data");
    private final String serializedPayload = "{\"data\":\"performance test data\"}";
    private final byte[] payloadBytes = serializedPayload.getBytes();
    private final String responsePayload = "{\"status\":\"success\"}";
    private final byte[] responseBytes = responsePayload.getBytes();

    @BeforeEach
    void setUp() {
        // Essential properties configuration
        lenient().when(natsProperties.getRequest()).thenReturn(requestProperties);
        lenient().when(requestProperties.getTimeout()).thenReturn(30000L);
        lenient().when(natsProperties.getJetStream()).thenReturn(jetStreamProperties);
        lenient().when(jetStreamProperties.getStream()).thenReturn(streamProperties);
        lenient().when(streamProperties.getDefaultName()).thenReturn("DEFAULT_STREAM");
        lenient().when(mockPublishAck.getSeqno()).thenReturn(1L);
        lenient().when(mockPublishAck.getStream()).thenReturn("DEFAULT_STREAM");
        
        // Lenient mocks for metrics (may not be called in all tests)
        lenient().when(meterRegistry.counter(anyString())).thenReturn(mock(io.micrometer.core.instrument.Counter.class));
        lenient().when(meterRegistry.timer(anyString())).thenReturn(mock(io.micrometer.core.instrument.Timer.class));
        
        // Mock the new dependencies
        MetricsFactory.NatsMetricsSet metricsSet = mock(MetricsFactory.NatsMetricsSet.class);
        when(metricsSet.getRequestCounter()).thenReturn(mock(io.micrometer.core.instrument.Counter.class));
        when(metricsSet.getSuccessCounter()).thenReturn(mock(io.micrometer.core.instrument.Counter.class));
        when(metricsSet.getErrorCounter()).thenReturn(mock(io.micrometer.core.instrument.Counter.class));
        when(metricsSet.getRequestTimer()).thenReturn(mock(io.micrometer.core.instrument.Timer.class));
        when(metricsFactory.createNatsMetricsSet(anyString(), eq(meterRegistry))).thenReturn(metricsSet);
        
        PublishOptions mockPublishOptions = mock(PublishOptions.class);
        lenient().when(publishOptionsBuilder.createDefault()).thenReturn(mockPublishOptions);
        lenient().when(publishOptionsBuilder.createCritical()).thenReturn(mockPublishOptions);
        
        natsService = new EnhancedNatsMessageService(
                natsConnection, jetStream, requestLogService, payloadProcessor, 
                requestValidator, natsProperties, meterRegistry, metricsFactory,
                publishOptionsBuilder, eventPublisher);

        // Essential mocks for payload processing
        when(payloadProcessor.serialize(any())).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(payloadBytes);
        lenient().when(payloadProcessor.fromBytes(responseBytes)).thenReturn(responsePayload);
        when(requestLogService.createRequestLog(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new NatsRequestLog());
        
        // Essential mocks for NATS operations
        try {
            lenient().when(natsConnection.request(anyString(), any(byte[].class), any(Duration.class)))
                    .thenReturn(mockMessage);
            // Fix the jetStream.publish mock to match the actual method signature (subject, headers, payloadBytes, publishOptions)
            when(jetStream.publish(anyString(), any(), any(byte[].class), any(PublishOptions.class)))
                    .thenReturn(mockPublishAck);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Ignore for test setup
        }
        lenient().when(mockMessage.getData()).thenReturn(responseBytes);
        
        // Ignore interrupt status for test
        Thread.interrupted();
    }

    @Test
    void throughputTest_ShouldHandleHighVolumeRequests() throws Exception {
        int totalRequests = 1000;
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(totalRequests);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalRequests; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    CompletableFuture<String> result = natsService.sendRequest(
                            testSubject + "." + requestId, 
                            testPayload, 
                            "corr-" + requestId
                    );
                    result.get(5, TimeUnit.SECONDS);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(60, TimeUnit.SECONDS), "All requests should complete within 60 seconds");
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        double throughput = (double) totalRequests / (duration / 1000.0);

        System.out.printf("Throughput Test Results:%n");
        System.out.printf("- Total Requests: %d%n", totalRequests);
        System.out.printf("- Successful: %d%n", successCount.get());
        System.out.printf("- Errors: %d%n", errorCount.get());
        System.out.printf("- Duration: %d ms%n", duration);
        System.out.printf("- Throughput: %.2f requests/second%n", throughput);

        assertEquals(totalRequests, successCount.get());
        assertEquals(0, errorCount.get());
        assertTrue(throughput > 50, "Throughput should be greater than 50 requests/second");

        executor.shutdown();
    }

    @Test
    void latencyTest_ShouldMeasureResponseTimes() throws Exception {
        int requestCount = 100;
        List<Long> latencies = new ArrayList<>();
        
        for (int i = 0; i < requestCount; i++) {
            long startTime = System.nanoTime();
            
            CompletableFuture<String> result = natsService.sendRequest(
                    testSubject + ".latency." + i, 
                    testPayload, 
                    "latency-corr-" + i
            );
            result.get();
            
            long endTime = System.nanoTime();
            long latency = (endTime - startTime) / 1_000_000; // Convert to milliseconds
            latencies.add(latency);
        }

        double averageLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long maxLatency = latencies.stream().mapToLong(Long::longValue).max().orElse(0L);
        long minLatency = latencies.stream().mapToLong(Long::longValue).min().orElse(0L);
        
        latencies.sort(Long::compareTo);
        long p95Latency = latencies.get((int) (requestCount * 0.95));
        long p99Latency = latencies.get((int) (requestCount * 0.99));

        System.out.printf("Latency Test Results:%n");
        System.out.printf("- Average Latency: %.2f ms%n", averageLatency);
        System.out.printf("- Min Latency: %d ms%n", minLatency);
        System.out.printf("- Max Latency: %d ms%n", maxLatency);
        System.out.printf("- P95 Latency: %d ms%n", p95Latency);
        System.out.printf("- P99 Latency: %d ms%n", p99Latency);

        assertTrue(averageLatency < 100, "Average latency should be less than 100ms");
        assertTrue(p95Latency < 200, "P95 latency should be less than 200ms");
    }

    @Test
    void concurrencyStressTest_ShouldHandleMaximumConcurrency() throws Exception {
        int maxConcurrency = 50;
        int requestsPerThread = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(maxConcurrency);
        AtomicInteger totalSuccesses = new AtomicInteger(0);
        AtomicLong totalDuration = new AtomicLong(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(maxConcurrency);

        for (int i = 0; i < maxConcurrency; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    
                    long threadStart = System.currentTimeMillis();
                    int successes = 0;
                    
                    for (int j = 0; j < requestsPerThread; j++) {
                        try {
                            CompletableFuture<String> result = natsService.sendRequest(
                                    testSubject + ".stress." + threadId + "." + j, 
                                    testPayload, 
                                    "stress-corr-" + threadId + "-" + j
                            );
                            result.get(10, TimeUnit.SECONDS);
                            successes++;
                        } catch (Exception e) {
                            System.err.printf("Thread %d, Request %d failed: %s%n", threadId, j, e.getMessage());
                        }
                    }
                    
                    long threadEnd = System.currentTimeMillis();
                    totalSuccesses.addAndGet(successes);
                    totalDuration.addAndGet(threadEnd - threadStart);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();
        
        assertTrue(completionLatch.await(120, TimeUnit.SECONDS), 
                "Stress test should complete within 120 seconds");

        int expectedTotal = maxConcurrency * requestsPerThread;
        double successRate = (double) totalSuccesses.get() / expectedTotal * 100;
        double averageThreadDuration = (double) totalDuration.get() / maxConcurrency;

        System.out.printf("Concurrency Stress Test Results:%n");
        System.out.printf("- Max Concurrency: %d threads%n", maxConcurrency);
        System.out.printf("- Requests per Thread: %d%n", requestsPerThread);
        System.out.printf("- Total Expected: %d%n", expectedTotal);
        System.out.printf("- Total Successful: %d%n", totalSuccesses.get());
        System.out.printf("- Success Rate: %.2f%%%n", successRate);
        System.out.printf("- Average Thread Duration: %.2f ms%n", averageThreadDuration);

        assertTrue(successRate > 95, "Success rate should be greater than 95%");
        assertTrue(averageThreadDuration < 30000, "Average thread duration should be less than 30 seconds");

        executor.shutdown();
    }

    @Test
    void memoryUsageTest_ShouldNotExceedMemoryLimits() throws Exception {
        Runtime runtime = Runtime.getRuntime();
        
        // Force garbage collection before test
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        int iterations = 500;
        
        for (int i = 0; i < iterations; i++) {
            CompletableFuture<String> result = natsService.sendRequest(
                    testSubject + ".memory." + i, 
                    testPayload, 
                    "memory-corr-" + i
            );
            result.get();
            
            // Periodic memory check and cleanup
            if (i % 50 == 0) {
                System.gc();
                try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                
                long currentMemory = runtime.totalMemory() - runtime.freeMemory();
                long memoryIncrease = currentMemory - initialMemory;
                
                // Memory should not increase by more than 100MB during test
                assertTrue(memoryIncrease < 100 * 1024 * 1024, 
                        String.format("Memory increase (%d bytes) at iteration %d exceeds limit", 
                                memoryIncrease, i));
            }
        }

        // Final memory check
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long totalMemoryIncrease = finalMemory - initialMemory;
        
        System.out.printf("Memory Usage Test Results:%n");
        System.out.printf("- Initial Memory: %d MB%n", initialMemory / (1024 * 1024));
        System.out.printf("- Final Memory: %d MB%n", finalMemory / (1024 * 1024));
        System.out.printf("- Memory Increase: %d MB%n", totalMemoryIncrease / (1024 * 1024));
        System.out.printf("- Iterations: %d%n", iterations);

        assertTrue(totalMemoryIncrease < 50 * 1024 * 1024, 
                "Total memory increase should be less than 50MB");
    }

    @Test
    void loadBalancingTest_ShouldDistributeRequestsEvenly() throws Exception {
        int totalRequests = 300;
        int threadCount = 15;
        AtomicInteger[] threadRequestCounts = new AtomicInteger[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            threadRequestCounts[i] = new AtomicInteger(0);
        }
        
        CountDownLatch latch = new CountDownLatch(totalRequests);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < totalRequests; i++) {
            final int requestId = i;
            final int threadIndex = i % threadCount;
            
            executor.submit(() -> {
                try {
                    CompletableFuture<String> result = natsService.sendRequest(
                            testSubject + ".balance." + requestId, 
                            testPayload, 
                            "balance-corr-" + requestId
                    );
                    result.get(5, TimeUnit.SECONDS);
                    threadRequestCounts[threadIndex].incrementAndGet();
                } catch (Exception e) {
                    System.err.printf("Request %d failed: %s%n", requestId, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(60, TimeUnit.SECONDS), "Load balancing test should complete within 60 seconds");

        int expectedRequestsPerThread = totalRequests / threadCount;
        int tolerance = expectedRequestsPerThread / 10; // 10% tolerance

        System.out.printf("Load Balancing Test Results:%n");
        System.out.printf("- Total Requests: %d%n", totalRequests);
        System.out.printf("- Thread Count: %d%n", threadCount);
        System.out.printf("- Expected per Thread: %d (±%d)%n", expectedRequestsPerThread, tolerance);

        for (int i = 0; i < threadCount; i++) {
            int threadRequests = threadRequestCounts[i].get();
            System.out.printf("- Thread %d: %d requests%n", i, threadRequests);
            
            assertTrue(Math.abs(threadRequests - expectedRequestsPerThread) <= tolerance,
                    String.format("Thread %d processed %d requests, expected %d (±%d)", 
                            i, threadRequests, expectedRequestsPerThread, tolerance));
        }

        executor.shutdown();
    }

    private static class PerformanceTestPayload {
        private final String data;

        public PerformanceTestPayload(String data) {
            this.data = data;
        }

        public String getData() {
            return data;
        }
    }
}