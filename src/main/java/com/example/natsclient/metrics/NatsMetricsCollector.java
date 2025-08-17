package com.example.natsclient.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class NatsMetricsCollector {

    private final MeterRegistry meterRegistry;
    
    private final Counter totalRequestsCounter;
    private final Counter successfulRequestsCounter;
    private final Counter failedRequestsCounter;
    private final Counter timeoutRequestsCounter;
    private final Counter publishedMessagesCounter;
    
    private final Timer requestDurationTimer;
    private final Timer publishDurationTimer;
    
    private final AtomicLong activeConnectionsGauge = new AtomicLong(0);
    private final AtomicLong pendingRequestsGauge = new AtomicLong(0);

    public NatsMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Request counters
        this.totalRequestsCounter = Counter.builder("nats.requests.total")
                .description("Total number of NATS requests")
                .register(meterRegistry);
        
        this.successfulRequestsCounter = Counter.builder("nats.requests.successful")
                .description("Number of successful NATS requests")
                .register(meterRegistry);
        
        this.failedRequestsCounter = Counter.builder("nats.requests.failed")
                .description("Number of failed NATS requests")
                .register(meterRegistry);
        
        this.timeoutRequestsCounter = Counter.builder("nats.requests.timeout")
                .description("Number of NATS requests that timed out")
                .register(meterRegistry);
        
        this.publishedMessagesCounter = Counter.builder("nats.messages.published")
                .description("Number of NATS messages published")
                .register(meterRegistry);
        
        // Duration timers
        this.requestDurationTimer = Timer.builder("nats.request.duration")
                .description("Duration of NATS request processing")
                .register(meterRegistry);
        
        this.publishDurationTimer = Timer.builder("nats.publish.duration")
                .description("Duration of NATS message publishing")
                .register(meterRegistry);
        
        // Gauges for current state
        Gauge.builder("nats.connections.active", activeConnectionsGauge, AtomicLong::doubleValue)
                .description("Number of active NATS connections")
                .register(meterRegistry);
        
        Gauge.builder("nats.requests.pending", pendingRequestsGauge, AtomicLong::doubleValue)
                .description("Number of pending NATS requests")
                .register(meterRegistry);
    }

    public void incrementTotalRequests() {
        totalRequestsCounter.increment();
    }

    public void incrementSuccessfulRequests() {
        successfulRequestsCounter.increment();
    }

    public void incrementFailedRequests() {
        failedRequestsCounter.increment();
    }

    public void incrementTimeoutRequests() {
        timeoutRequestsCounter.increment();
    }

    public void incrementPublishedMessages() {
        publishedMessagesCounter.increment();
    }

    public void recordRequestDuration(long durationMillis) {
        requestDurationTimer.record(java.time.Duration.ofMillis(durationMillis));
    }

    public void recordPublishDuration(long durationMillis) {
        publishDurationTimer.record(java.time.Duration.ofMillis(durationMillis));
    }

    public void incrementActiveConnections() {
        activeConnectionsGauge.incrementAndGet();
    }

    public void decrementActiveConnections() {
        activeConnectionsGauge.decrementAndGet();
    }

    public void incrementPendingRequests() {
        pendingRequestsGauge.incrementAndGet();
    }

    public void decrementPendingRequests() {
        pendingRequestsGauge.decrementAndGet();
    }

    public long getTotalRequests() {
        return (long) totalRequestsCounter.count();
    }

    public long getSuccessfulRequests() {
        return (long) successfulRequestsCounter.count();
    }

    public long getFailedRequests() {
        return (long) failedRequestsCounter.count();
    }

    public long getTimeoutRequests() {
        return (long) timeoutRequestsCounter.count();
    }

    public long getPublishedMessages() {
        return (long) publishedMessagesCounter.count();
    }

    public double getSuccessRate() {
        long total = getTotalRequests();
        if (total == 0) return 0.0;
        return (getSuccessfulRequests() / (double) total) * 100.0;
    }
}