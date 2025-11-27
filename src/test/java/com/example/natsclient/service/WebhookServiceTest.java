package com.example.natsclient.service;

import com.example.natsclient.entity.NatsRequestLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private WebhookService webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new WebhookService(restTemplate);
    }

    @Test
    void sendWebhook_Success_ShouldLogSuccess() {
        // Arrange
        String webhookUrl = "https://example.com/webhook";
        NatsRequestLog requestLog = createMockRequestLog();
        when(restTemplate.postForEntity(eq(webhookUrl), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("Success"));

        // Act
        webhookService.sendWebhook(webhookUrl, requestLog);

        // Assert
        verify(restTemplate).postForEntity(eq(webhookUrl), any(), eq(String.class));
    }

    @Test
    void sendWebhook_Failure_ShouldLogFailure() {
        // Arrange
        String webhookUrl = "https://example.com/webhook";
        NatsRequestLog requestLog = createMockRequestLog();
        when(restTemplate.postForEntity(eq(webhookUrl), any(), eq(String.class)))
                .thenThrow(new RuntimeException("Connection failed"));

        // Act
        // Should not throw exception as it is caught internally
        assertDoesNotThrow(() -> webhookService.sendWebhook(webhookUrl, requestLog));

        // Assert
        verify(restTemplate).postForEntity(eq(webhookUrl), any(), eq(String.class));
    }

    private NatsRequestLog createMockRequestLog() {
        NatsRequestLog log = new NatsRequestLog();
        log.setRequestId("req-123");
        log.setSubject("test.subject");
        log.setStatus(NatsRequestLog.RequestStatus.SUCCESS);
        log.setCreatedDate(LocalDateTime.now());
        log.setResponseTimestamp(LocalDateTime.now());
        log.setWebhookUrl("https://example.com/webhook");
        return log;
    }
}
