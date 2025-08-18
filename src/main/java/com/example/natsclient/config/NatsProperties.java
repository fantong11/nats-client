package com.example.natsclient.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "nats")
@Data
public class NatsProperties {

    private String url = "nats://localhost:4222";
    private String username;
    private String password;
    private String token;
    private String credentials;
    private Connection connection = new Connection();
    private Request request = new Request();
    private Tls tls = new Tls();

    @Data
    public static class Connection {
        private long timeout = 10000;
        private Reconnect reconnect = new Reconnect();

        @Data
        public static class Reconnect {
            private long wait = 2000;
            private int maxAttempts = 10;
        }
    }

    @Data
    public static class Request {
        private long timeout = 30000;
    }

    @Data
    public static class Tls {
        private boolean enabled = false;
        private String keystore;
        private String keystorePassword;
        private String truststore;
        private String truststorePassword;
    }

}