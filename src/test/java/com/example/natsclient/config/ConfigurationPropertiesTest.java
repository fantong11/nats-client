package com.example.natsclient.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationPropertiesTest {

    @Test
    void natsProperties_ShouldBindFromConfigurationMap() {
        // Given
        Map<String, Object> properties = new HashMap<>();
        properties.put("nats.url", "nats://test-server:4222");
        properties.put("nats.connection-name", "test-connection");
        properties.put("nats.jet-stream.enabled", "true");
        properties.put("nats.jet-stream.stream.default-name", "TEST_STREAM");
        properties.put("nats.connection.timeout", "15000");
        properties.put("nats.connection.reconnect.wait", "3000");
        properties.put("nats.connection.reconnect.max-attempts", "5");

        ConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
        Binder binder = new Binder(source);

        // When
        NatsProperties natsProperties = binder.bind("nats", NatsProperties.class).get();

        // Then - Test basic NATS properties
        assertEquals("nats://test-server:4222", natsProperties.getUrl());
        assertEquals("test-connection", natsProperties.getConnectionName());
        
        // Test JetStream properties
        assertTrue(natsProperties.getJetStream().isEnabled());
        assertEquals("$JS.API", natsProperties.getJetStream().getPrefix());
        
        // Test connection properties
        assertEquals(15000, natsProperties.getConnection().getTimeout());
        assertEquals(3000, natsProperties.getConnection().getReconnect().getWait());
        assertEquals(5, natsProperties.getConnection().getReconnect().getMaxAttempts());
        
        // Test logging properties with defaults (not overridden)
        assertTrue(natsProperties.getLogging().isEnableConnectionEvents());
        assertTrue(natsProperties.getLogging().isEnableErrorLogging());
        assertTrue(natsProperties.getLogging().isEnableSlowConsumerWarning());
    }

    @Test
    void infoProperties_ShouldBindFromConfigurationMap() {
        // Given
        Map<String, Object> properties = new HashMap<>();
        properties.put("info.app.name", "test-app");
        properties.put("info.app.version", "1.0.0-test");
        properties.put("info.app.description", "Test Application");

        ConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
        Binder binder = new Binder(source);

        // When
        InfoProperties infoProperties = binder.bind("info.app", InfoProperties.class).get();

        // Then
        assertEquals("test-app", infoProperties.getName());
        assertEquals("1.0.0-test", infoProperties.getVersion());
        assertEquals("Test Application", infoProperties.getDescription());
    }

    @Test
    void natsProperties_ShouldUseDefaults_WhenNoConfiguration() {
        // Given - Empty configuration
        Map<String, Object> properties = new HashMap<>();
        ConfigurationPropertySource source = new MapConfigurationPropertySource(properties);
        Binder binder = new Binder(source);

        // When
        NatsProperties natsProperties = binder.bind("nats", NatsProperties.class)
                .orElse(new NatsProperties());

        // Then - Should use default values
        assertEquals("nats://localhost:4222", natsProperties.getUrl());
        assertEquals("nats-client-service", natsProperties.getConnectionName());
        assertTrue(natsProperties.getJetStream().isEnabled());
        assertEquals("$JS.API", natsProperties.getJetStream().getPrefix());
        assertEquals(10000, natsProperties.getConnection().getTimeout());
        assertTrue(natsProperties.getLogging().isEnableConnectionEvents());
    }
}