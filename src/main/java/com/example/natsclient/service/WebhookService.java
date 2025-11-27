package com.example.natsclient.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class WebhookService {

    private static final Logger logger = LoggerFactory.getLogger(WebhookService.class);
    private final RestTemplate restTemplate;

    public WebhookService() {
        this.restTemplate = new RestTemplate();
    }

    @Autowired
    public WebhookService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Async
    public void sendWebhook(String url, Object payload) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }

        try {
            logger.info("Sending webhook to: {}", url);
            restTemplate.postForEntity(url, payload, String.class);
            logger.info("Webhook sent successfully to: {}", url);
        } catch (Exception e) {
            logger.error("Failed to send webhook to: {}", url, e);
        }
    }
}
