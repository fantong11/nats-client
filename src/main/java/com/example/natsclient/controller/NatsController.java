package com.example.natsclient.controller;

import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.service.NatsOrchestrationService;
import com.example.natsclient.service.NatsOrchestrationService.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/nats")
@Slf4j
public class NatsController {


    @Autowired
    private NatsOrchestrationService orchestrationService;

    @PostMapping("/request")
    public CompletableFuture<ResponseEntity<NatsRequestResponse>> sendRequest(@Valid @RequestBody NatsRequestDto requestDto) {
        log.info("Received NATS request - Subject: {}", requestDto.getSubject());
        
        NatsRequest request = new NatsRequest();
        request.setSubject(requestDto.getSubject());
        request.setPayload(requestDto.getPayload());
        
        return orchestrationService.sendRequestWithTracking(request)
                .thenApply(response -> {
                    if (response.isSuccess()) {
                        return ResponseEntity.ok(response);
                    } else {
                        return ResponseEntity.status(500).body(response);
                    }
                });
    }

    @PostMapping("/publish")
    public CompletableFuture<ResponseEntity<NatsPublishResponse>> publishMessage(@Valid @RequestBody NatsPublishDto publishDto) {
        log.info("Received NATS publish request - Subject: {}", publishDto.getSubject());
        
        NatsPublishRequest request = new NatsPublishRequest();
        request.setSubject(publishDto.getSubject());
        request.setPayload(publishDto.getPayload());
        
        return orchestrationService.publishMessageWithTracking(request)
                .thenApply(result -> {
                    NatsPublishResponse response = new NatsPublishResponse();
                    response.setRequestId(result);
                    response.setStatus("PUBLISHED");
                    response.setMessage("Message published successfully, use trackingUrl to check status");
                    response.setSubject(publishDto.getSubject());
                    response.setTrackingUrl("/api/nats/status/" + result);
                    response.setTimestamp(java.time.Instant.now().toString());
                    return ResponseEntity.ok(response);
                })
                .exceptionally(throwable -> {
                    log.error("Failed to publish message", throwable);
                    NatsPublishResponse response = new NatsPublishResponse();
                    response.setStatus("FAILED");
                    response.setMessage("Failed to publish message: " + throwable.getMessage());
                    response.setSubject(publishDto.getSubject());
                    response.setTimestamp(java.time.Instant.now().toString());
                    return ResponseEntity.status(500).body(response);
                });
    }

    @GetMapping("/status/{requestId}")
    public ResponseEntity<NatsRequestStatus> getRequestStatus(@PathVariable String requestId) {
        log.debug("Getting status for request ID: {}", requestId);
        
        NatsRequestStatus status = orchestrationService.getRequestStatus(requestId);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/status/correlation/{correlationId}")
    public ResponseEntity<NatsRequestStatus> getRequestStatusByCorrelationId(@PathVariable String correlationId) {
        log.debug("Getting status for correlation ID: {}", correlationId);
        
        NatsRequestStatus status = orchestrationService.getRequestStatusByCorrelationId(correlationId);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/requests/{status}")
    public ResponseEntity<List<NatsRequestStatus>> getRequestsByStatus(@PathVariable String status) {
        log.debug("Getting requests with status: {}", status);
        
        try {
            NatsRequestLog.RequestStatus requestStatus = NatsRequestLog.RequestStatus.valueOf(status.toUpperCase());
            List<NatsRequestStatus> requests = orchestrationService.getRequestsByStatus(requestStatus);
            return ResponseEntity.ok(requests);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/statistics")
    public ResponseEntity<NatsStatistics> getStatistics() {
        log.debug("Getting NATS statistics");
        
        NatsStatistics statistics = orchestrationService.getStatistics();
        return ResponseEntity.ok(statistics);
    }

    @PostMapping("/test/echo")
    public CompletableFuture<ResponseEntity<NatsRequestResponse>> testEcho(@Valid @RequestBody TestEchoDto echoDto) {
        log.info("Test echo request - Message: {}", echoDto.getMessage());
        
        NatsRequest request = new NatsRequest();
        request.setSubject("test.echo");
        request.setPayload(echoDto);
        
        return orchestrationService.sendRequestWithTracking(request)
                .thenApply(response -> ResponseEntity.ok(response));
    }

    @PostMapping("/test/timeout")
    public CompletableFuture<ResponseEntity<NatsRequestResponse>> testTimeout() {
        log.info("Test timeout request");
        
        NatsRequest request = new NatsRequest();
        request.setSubject("test.timeout");
        request.setPayload(new TestPayload("timeout test"));
        
        return orchestrationService.sendRequestWithTracking(request)
                .thenApply(response -> ResponseEntity.ok(response));
    }

    @PostMapping("/test/error")
    public CompletableFuture<ResponseEntity<NatsRequestResponse>> testError() {
        log.info("Test error request");
        
        NatsRequest request = new NatsRequest();
        request.setSubject("test.error");
        request.setPayload(new TestPayload("error test"));
        
        return orchestrationService.sendRequestWithTracking(request)
                .thenApply(response -> ResponseEntity.ok(response));
    }

    @GetMapping("/health")
    public ResponseEntity<HealthStatus> health() {
        HealthStatus health = new HealthStatus();
        health.setStatus("UP");
        health.setTimestamp(java.time.LocalDateTime.now());
        
        NatsStatistics stats = orchestrationService.getStatistics();
        health.setTotalRequests(stats.getTotalRequests());
        health.setSuccessRate(stats.getSuccessRate());
        
        return ResponseEntity.ok(health);
    }

    @Data
    public static class NatsRequestDto {
        @NotBlank(message = "Subject is required")
        private String subject;
        
        @NotNull(message = "Payload is required")
        private Object payload;
    }

    @Data
    public static class NatsPublishDto {
        @NotBlank(message = "Subject is required")
        private String subject;
        
        @NotNull(message = "Payload is required")
        private Object payload;
    }
    
    @Data
    public static class NatsPublishResponse {
        private String requestId;
        private String status; // PUBLISHED, FAILED
        private String message;
        private String subject;
        private String trackingUrl;
        private String timestamp;
    }

    @Data
    public static class TestEchoDto {
        @NotBlank(message = "Message is required")
        private String message;
        
        private String metadata;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestPayload {
        private String data;
    }

    @Data
    public static class HealthStatus {
        private String status;
        private java.time.LocalDateTime timestamp;
        private long totalRequests;
        private double successRate;
    }
}