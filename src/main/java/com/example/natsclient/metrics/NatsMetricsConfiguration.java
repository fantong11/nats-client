package com.example.natsclient.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class NatsMetricsConfiguration {

    @Bean
    public NatsMetricsCollector natsMetricsCollector(MeterRegistry meterRegistry) {
        // Configure common tags for NATS metrics
        meterRegistry.config()
                .meterFilter(MeterFilter.commonTags(Arrays.asList(
                        Tag.of("application", "nats-client"),
                        Tag.of("service", "nats-messaging")
                )));
        
        return new NatsMetricsCollector(meterRegistry);
    }
}