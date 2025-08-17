package com.example.natsclient.controller;

import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.service.NatsOrchestrationService;
import com.example.natsclient.service.NatsOrchestrationService.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/nats")
public class NatsController {

    private static final Logger logger = LoggerFactory.getLogger(NatsController.class);

    @Autowired
    private NatsOrchestrationService orchestrationService;

    @PostMapping("/request")
    public CompletableFuture<ResponseEntity<NatsRequestResponse>> sendRequest(@Valid @RequestBody NatsRequestDto requestDto) {
        logger.info("Received NATS request - Subject: {}", requestDto.getSubject());
        
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
    public CompletableFuture<ResponseEntity<String>> publishMessage(@Valid @RequestBody NatsPublishDto publishDto) {
        logger.info("Received NATS publish request - Subject: {}", publishDto.getSubject());
        
        NatsPublishRequest request = new NatsPublishRequest();
        request.setSubject(publishDto.getSubject());
        request.setPayload(publishDto.getPayload());
        
        return orchestrationService.publishMessageWithTracking(request)
                .thenApply(result -> ResponseEntity.ok("Message published successfully"))
                .exceptionally(throwable -> {
                    logger.error("Failed to publish message", throwable);
                    return ResponseEntity.status(500).body("Failed to publish message: " + throwable.getMessage());
                });
    }

    @GetMapping("/status/{requestId}")
    public ResponseEntity<NatsRequestStatus> getRequestStatus(@PathVariable String requestId) {
        logger.debug("Getting status for request ID: {}", requestId);
        
        NatsRequestStatus status = orchestrationService.getRequestStatus(requestId);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/status/correlation/{correlationId}")
    public ResponseEntity<NatsRequestStatus> getRequestStatusByCorrelationId(@PathVariable String correlationId) {
        logger.debug("Getting status for correlation ID: {}", correlationId);
        
        NatsRequestStatus status = orchestrationService.getRequestStatusByCorrelationId(correlationId);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/requests/{status}")
    public ResponseEntity<List<NatsRequestStatus>> getRequestsByStatus(@PathVariable String status) {
        logger.debug("Getting requests with status: {}", status);
        
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
        logger.debug("Getting NATS statistics");
        
        NatsStatistics statistics = orchestrationService.getStatistics();
        return ResponseEntity.ok(statistics);
    }

    @PostMapping("/test/echo")
    public CompletableFuture<ResponseEntity<NatsRequestResponse>> testEcho(@Valid @RequestBody TestEchoDto echoDto) {
        logger.info("Test echo request - Message: {}", echoDto.getMessage());
        
        NatsRequest request = new NatsRequest();
        request.setSubject("test.echo");
        request.setPayload(echoDto);
        
        return orchestrationService.sendRequestWithTracking(request)
                .thenApply(response -> ResponseEntity.ok(response));
    }

    @PostMapping("/test/timeout")
    public CompletableFuture<ResponseEntity<NatsRequestResponse>> testTimeout() {
        logger.info("Test timeout request");
        
        NatsRequest request = new NatsRequest();
        request.setSubject("test.timeout");
        request.setPayload(new TestPayload("timeout test"));
        
        return orchestrationService.sendRequestWithTracking(request)
                .thenApply(response -> ResponseEntity.ok(response));
    }

    @PostMapping("/test/error")
    public CompletableFuture<ResponseEntity<NatsRequestResponse>> testError() {
        logger.info("Test error request");
        
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

    public static class NatsRequestDto {
        @NotBlank(message = "Subject is required")
        private String subject;
        
        @NotNull(message = "Payload is required")
        private Object payload;

        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public Object getPayload() { return payload; }
        public void setPayload(Object payload) { this.payload = payload; }
    }

    public static class NatsPublishDto {
        @NotBlank(message = "Subject is required")
        private String subject;
        
        @NotNull(message = "Payload is required")
        private Object payload;

        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public Object getPayload() { return payload; }
        public void setPayload(Object payload) { this.payload = payload; }
    }

    public static class TestEchoDto {
        @NotBlank(message = "Message is required")
        private String message;
        
        private String metadata;

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getMetadata() { return metadata; }
        public void setMetadata(String metadata) { this.metadata = metadata; }
    }

    public static class TestPayload {
        private String data;

        public TestPayload() {}
        
        public TestPayload(String data) {
            this.data = data;
        }

        public String getData() { return data; }
        public void setData(String data) { this.data = data; }
    }

    public static class HealthStatus {
        private String status;
        private java.time.LocalDateTime timestamp;
        private long totalRequests;
        private double successRate;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public java.time.LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(java.time.LocalDateTime timestamp) { this.timestamp = timestamp; }
        public long getTotalRequests() { return totalRequests; }
        public void setTotalRequests(long totalRequests) { this.totalRequests = totalRequests; }
        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }
    }
}