package com.example.natsclient.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for listener recovery behavior.
 */
@Configuration
@ConfigurationProperties(prefix = "nats.listener.recovery")
@Data
public class ListenerRecoveryProperties {
    
    /**
     * Whether listener recovery is enabled on startup.
     */
    private boolean enabled = true;
    
    /**
     * Lock timeout in minutes for distributed locking.
     */
    private int lockTimeoutMinutes = 10;
    
    /**
     * Maximum number of retry attempts if recovery fails.
     */
    private int maxRetryAttempts = 3;
    
    /**
     * Delay between retry attempts in milliseconds.
     */
    private long retryDelayMs = 5000;
    
    /**
     * Whether to fail fast if lock acquisition fails.
     */
    private boolean failFast = false;
}