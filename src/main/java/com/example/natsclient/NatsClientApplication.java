package com.example.natsclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import com.example.natsclient.config.NatsProperties;
import com.example.natsclient.config.InfoProperties;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties({NatsProperties.class, InfoProperties.class})
public class NatsClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(NatsClientApplication.class, args);
    }

}