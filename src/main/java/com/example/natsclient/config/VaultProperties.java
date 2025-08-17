package com.example.natsclient.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "vault")
public class VaultProperties {
    
    private String host = "localhost";
    private int port = 8200;
    private String scheme = "http";
    private String token;
    private String kv2Path = "secret";
    private String natsPath = "nats";
    private long connectionTimeoutMs = 5000;
    private long readTimeoutMs = 15000;
    
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    
    public String getScheme() { return scheme; }
    public void setScheme(String scheme) { this.scheme = scheme; }
    
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    
    public String getKv2Path() { return kv2Path; }
    public void setKv2Path(String kv2Path) { this.kv2Path = kv2Path; }
    
    public String getNatsPath() { return natsPath; }
    public void setNatsPath(String natsPath) { this.natsPath = natsPath; }
    
    public long getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public void setConnectionTimeoutMs(long connectionTimeoutMs) { this.connectionTimeoutMs = connectionTimeoutMs; }
    
    public long getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(long readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    
    public String getUri() {
        return String.format("%s://%s:%d", scheme, host, port);
    }
}