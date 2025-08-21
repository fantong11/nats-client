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
    private String connectionName = "nats-client-service";
    private Connection connection = new Connection();
    private Request request = new Request();
    private Tls tls = new Tls();
    private Logging logging = new Logging();

    @Data
    public static class Connection {
        private long timeout = 10000;
        private long pingInterval = 120000; // 2 minutes
        private long requestCleanupInterval = 300000; // 5 minutes
        private boolean noEcho = false;
        private int maxControlLine = 1024;
        private Reconnect reconnect = new Reconnect();

        @Data
        public static class Reconnect {
            private long wait = 2000;
            private int maxAttempts = -1; // -1 for unlimited
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

    @Data
    public static class Logging {
        private boolean enableConnectionEvents = true;
        private boolean enableErrorLogging = true;
        private boolean enableSlowConsumerWarning = true;
    }

}