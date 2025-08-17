package com.example.natsclient.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "nats")
public class NatsProperties {

    private String url = "nats://localhost:4222";
    private String username;
    private String password;
    private String token;
    private String credentials;
    private Connection connection = new Connection();
    private Request request = new Request();
    private Tls tls = new Tls();

    public static class Connection {
        private long timeout = 10000;
        private Reconnect reconnect = new Reconnect();

        public static class Reconnect {
            private long wait = 2000;
            private int maxAttempts = 10;

            public long getWait() { return wait; }
            public void setWait(long wait) { this.wait = wait; }
            public int getMaxAttempts() { return maxAttempts; }
            public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        }

        public long getTimeout() { return timeout; }
        public void setTimeout(long timeout) { this.timeout = timeout; }
        public Reconnect getReconnect() { return reconnect; }
        public void setReconnect(Reconnect reconnect) { this.reconnect = reconnect; }
    }

    public static class Request {
        private long timeout = 30000;

        public long getTimeout() { return timeout; }
        public void setTimeout(long timeout) { this.timeout = timeout; }
    }

    public static class Tls {
        private boolean enabled = false;
        private String keystore;
        private String keystorePassword;
        private String truststore;
        private String truststorePassword;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getKeystore() { return keystore; }
        public void setKeystore(String keystore) { this.keystore = keystore; }
        public String getKeystorePassword() { return keystorePassword; }
        public void setKeystorePassword(String keystorePassword) { this.keystorePassword = keystorePassword; }
        public String getTruststore() { return truststore; }
        public void setTruststore(String truststore) { this.truststore = truststore; }
        public String getTruststorePassword() { return truststorePassword; }
        public void setTruststorePassword(String truststorePassword) { this.truststorePassword = truststorePassword; }
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getCredentials() { return credentials; }
    public void setCredentials(String credentials) { this.credentials = credentials; }
    public Connection getConnection() { return connection; }
    public void setConnection(Connection connection) { this.connection = connection; }
    public Request getRequest() { return request; }
    public void setRequest(Request request) { this.request = request; }
    public Tls getTls() { return tls; }
    public void setTls(Tls tls) { this.tls = tls; }
}