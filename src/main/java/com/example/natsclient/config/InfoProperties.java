package com.example.natsclient.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "info.app")
public class InfoProperties {
    private String name = "nats-client";
    private String description = "NATS Client Service";
    private String version = "1.0.0";
    private String javaVersion = "17";
}