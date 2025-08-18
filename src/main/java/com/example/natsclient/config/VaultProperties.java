package com.example.natsclient.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "vault")
@Data
public class VaultProperties {
    
    private String host = "localhost";
    private int port = 8200;
    private String scheme = "http";
    private String token;
    private String kv2Path = "secret";
    private String natsPath = "nats";
    private long connectionTimeoutMs = 5000;
    private long readTimeoutMs = 15000;
    
    
    public String getUri() {
        return String.format("%s://%s:%d", scheme, host, port);
    }
}