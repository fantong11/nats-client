package com.example.natsclient.config;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import static org.mockito.Mockito.mock;

/**
 * 測試環境的 NATS 配置，使用 Mock 對象避免真實連接
 */
@TestConfiguration
@Profile("test")
public class TestNatsConfig {

    @Bean("natsConnection")
    @Primary
    public Connection testNatsConnection() {
        return mock(Connection.class);
    }

    @Bean("jetStream")
    @Primary
    public JetStream testJetStream() {
        return mock(JetStream.class);
    }

    @Bean("jetStreamManagement")
    @Primary
    public JetStreamManagement testJetStreamManagement() {
        return mock(JetStreamManagement.class);
    }
}