package com.example.natsclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class NatsClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(NatsClientApplication.class, args);
    }

}