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
    private JetStream jetStream = new JetStream();

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

    @Data
    public static class JetStream {
        private boolean enabled = true;
        private String domain;
        private String prefix = "$JS.API";
        private long defaultTimeout = 5000;
        private PublishAck publishAck = new PublishAck();
        private Subscribe subscribe = new Subscribe();
        private Stream stream = new Stream();

        @Data
        public static class PublishAck {
            private long timeout = 5000;
            private boolean waitForAck = true;
        }

        @Data
        public static class Subscribe {
            private long ackTimeout = 30000;
            private int maxDeliver = 3;
            private String deliverPolicy = "NEW";
        }

        @Data
        public static class Stream {
            private String defaultName = "DEFAULT_STREAM";
            private String[] subjects = {"*"};
            private String storage = "MEMORY";
            private long maxAge = 86400000; // 24 hours
            private int maxMsgs = 100000;
            private boolean replicate = false;
            private int replicas = 1;
        }
    }

}