package com.example.natsclient.service.factory;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetricsFactoryTestSimple {

    private MetricsFactory metricsFactory;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        metricsFactory = new MetricsFactory();
        meterRegistry = new SimpleMeterRegistry();
    }

    @Test
    void createCounter_ShouldCreateValidCounter() {
        String name = "test.counter";
        String description = "Test counter";
        
        Counter counter = metricsFactory.createCounter(name, description, meterRegistry);
        
        assertNotNull(counter);
        assertEquals(0.0, counter.count());
        
        counter.increment();
        assertEquals(1.0, counter.count());
    }

    @Test
    void createTimer_ShouldCreateValidTimer() {
        String name = "test.timer";
        String description = "Test timer";
        
        Timer timer = metricsFactory.createTimer(name, description, meterRegistry);
        
        assertNotNull(timer);
        assertEquals(0L, timer.count());
        assertEquals(0.0, timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    @Test
    void createNatsMetricsSet_ShouldCreateAllMetrics() {
        String componentName = "test-service";
        
        MetricsFactory.NatsMetricsSet metricsSet = metricsFactory.createNatsMetricsSet(componentName, meterRegistry);
        
        assertNotNull(metricsSet);
        assertNotNull(metricsSet.getRequestCounter());
        assertNotNull(metricsSet.getSuccessCounter());
        assertNotNull(metricsSet.getErrorCounter());
        assertNotNull(metricsSet.getRequestTimer());
    }

    @Test
    void createNatsMetricsSet_WithEmptyName_ShouldWork() {
        String componentName = "";
        
        MetricsFactory.NatsMetricsSet metricsSet = metricsFactory.createNatsMetricsSet(componentName, meterRegistry);
        
        assertNotNull(metricsSet);
        assertNotNull(metricsSet.getRequestCounter());
    }

    @Test
    void natsMetricsSet_ShouldTrackMetricsCorrectly() {
        String componentName = "test";
        
        MetricsFactory.NatsMetricsSet metricsSet = metricsFactory.createNatsMetricsSet(componentName, meterRegistry);
        
        // Test counters work
        metricsSet.getRequestCounter().increment();
        metricsSet.getSuccessCounter().increment();
        
        assertEquals(1.0, metricsSet.getRequestCounter().count());
        assertEquals(1.0, metricsSet.getSuccessCounter().count());
        assertEquals(0.0, metricsSet.getErrorCounter().count());
        
        // Test timer works
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(metricsSet.getRequestTimer());
        
        assertEquals(1L, metricsSet.getRequestTimer().count());
        assertTrue(metricsSet.getRequestTimer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) > 0);
    }

    @Test
    void createCounter_WithNullRegistry_ShouldUseFallback() {
        String name = "test.counter.fallback";
        String description = "Test counter with null registry";
        
        Counter counter = metricsFactory.createCounter(name, description, null);
        
        assertNotNull(counter);
        assertEquals(0.0, counter.count());
        
        counter.increment();
        assertEquals(1.0, counter.count());
    }

    @Test
    void createTimer_WithNullRegistry_ShouldUseFallback() {
        String name = "test.timer.fallback";
        String description = "Test timer with null registry";
        
        Timer timer = metricsFactory.createTimer(name, description, null);
        
        assertNotNull(timer);
        assertEquals(0L, timer.count());
    }
}