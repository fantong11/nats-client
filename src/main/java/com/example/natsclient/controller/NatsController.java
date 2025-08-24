package com.example.natsclient.controller;

import com.example.natsclient.dto.NatsRequestLogDto;
import com.example.natsclient.service.NatsOrchestrationService;
import com.example.natsclient.service.NatsOrchestrationService.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "NATS Operations", description = "NATS消息操作相關API")
public class NatsController {


    @Autowired
    private NatsOrchestrationService orchestrationService;

    @PostMapping("/request")
    @Operation(
            summary = "發送NATS請求",
            description = "發送請求到NATS服務器並等待響應。支持異步處理和完整的請求追蹤。",
            tags = {"NATS Operations"}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "請求處理成功",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = NatsRequestResponse.class),
                            examples = @ExampleObject(
                                    name = "成功響應",
                                    value = "{\"correlationId\":\"CORR-12345\",\"subject\":\"user.profile\",\"success\":true,\"responsePayload\":{\"userId\":123,\"name\":\"John Doe\"},\"timestamp\":\"2024-01-01T10:00:00\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "請求處理失敗",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = NatsRequestResponse.class),
                            examples = @ExampleObject(
                                    name = "錯誤響應",
                                    value = "{\"correlationId\":\"CORR-12345\",\"subject\":\"user.profile\",\"success\":false,\"errorMessage\":\"Connection timeout\",\"timestamp\":\"2024-01-01T10:00:00\"}"
                            )
                    )
            )
    })
    public CompletableFuture<ResponseEntity<NatsRequestResponse>> sendRequest(
            @Parameter(
                    description = "NATS請求參數",
                    required = true,
                    content = @Content(
                            examples = @ExampleObject(
                                    name = "用戶資料請求",
                                    value = "{\"subject\":\"user.profile\",\"payload\":{\"userId\":123}}"
                            )
                    )
            )
            @Valid @RequestBody NatsRequestDto requestDto) {
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
    @Operation(
            summary = "發布NATS消息",
            description = "發布消息到NATS JetStream，支持可靠的消息傳遞和持久化存儲。",
            tags = {"NATS Operations"}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "消息發布成功",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = NatsPublishResponse.class),
                            examples = @ExampleObject(
                                    name = "發布成功",
                                    value = "{\"requestId\":\"req-12345\",\"status\":\"PUBLISHED\",\"message\":\"Message published successfully\",\"subject\":\"events.user.created\",\"trackingUrl\":\"/api/nats/status/req-12345\",\"timestamp\":\"2024-01-01T10:00:00Z\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "消息發布失敗",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = NatsPublishResponse.class),
                            examples = @ExampleObject(
                                    name = "發布失敗",
                                    value = "{\"status\":\"FAILED\",\"message\":\"Failed to publish message: Connection error\",\"subject\":\"events.user.created\",\"timestamp\":\"2024-01-01T10:00:00Z\"}"
                            )
                    )
            )
    })
    public CompletableFuture<ResponseEntity<NatsPublishResponse>> publishMessage(
            @Parameter(
                    description = "NATS發布參數",
                    required = true,
                    content = @Content(
                            examples = @ExampleObject(
                                    name = "用戶創建事件",
                                    value = "{\"subject\":\"events.user.created\",\"payload\":{\"userId\":123,\"name\":\"John Doe\",\"email\":\"john@example.com\"}}"
                            )
                    )
            )
            @Valid @RequestBody NatsPublishDto publishDto) {
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
    @Operation(
            summary = "查詢請求狀態",
            description = "根據請求ID查詢NATS請求的處理狀態和詳細信息。",
            tags = {"Request Tracking"}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "狀態查詢成功",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = NatsRequestStatus.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "請求ID不存在"
            )
    })
    public ResponseEntity<NatsRequestStatus> getRequestStatus(
            @Parameter(
                    description = "請求ID",
                    required = true,
                    example = "req-12345"
            )
            @PathVariable String requestId) {
        log.debug("Getting status for request ID: {}", requestId);
        
        NatsRequestStatus status = orchestrationService.getRequestStatus(requestId);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/status/correlation/{correlationId}")
    @Operation(
            summary = "通過相關ID查詢請求狀態",
            description = "根據相關ID查詢NATS請求的處理狀態，用於追蹤特定的業務請求。",
            tags = {"Request Tracking"}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "狀態查詢成功",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = NatsRequestStatus.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "相關ID不存在"
            )
    })
    public ResponseEntity<NatsRequestStatus> getRequestStatusByCorrelationId(
            @Parameter(
                    description = "相關ID",
                    required = true,
                    example = "CORR-12345"
            )
            @PathVariable String correlationId) {
        log.debug("Getting status for correlation ID: {}", correlationId);
        
        NatsRequestStatus status = orchestrationService.getRequestStatusByCorrelationId(correlationId);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/requests/{status}")
    @Operation(
            summary = "根據狀態查詢請求列表",
            description = "查詢指定狀態的所有NATS請求列表，支持的狀態：PENDING, SUCCESS, FAILED, TIMEOUT, ERROR。",
            tags = {"Request Tracking"}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "查詢成功",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = NatsRequestStatus.class, type = "array")
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "無效的狀態參數"
            )
    })
    public ResponseEntity<List<NatsRequestStatus>> getRequestsByStatus(
            @Parameter(
                    description = "請求狀態",
                    required = true,
                    example = "SUCCESS",
                    schema = @Schema(allowableValues = {"PENDING", "SUCCESS", "FAILED", "TIMEOUT", "ERROR"})
            )
            @PathVariable String status) {
        log.debug("Getting requests with status: {}", status);
        
        try {
            NatsRequestLogDto.RequestStatus requestStatus = NatsRequestLogDto.RequestStatus.valueOf(status.toUpperCase());
            List<NatsRequestStatus> requests = orchestrationService.getRequestsByStatus(requestStatus);
            return ResponseEntity.ok(requests);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/statistics")
    @Operation(
            summary = "獲取NATS統計信息",
            description = "獲取NATS服務的統計數據，包括總請求數、成功率、失敗數等指標。",
            tags = {"Statistics"}
    )
    @ApiResponse(
            responseCode = "200",
            description = "統計信息獲取成功",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = NatsStatistics.class),
                    examples = @ExampleObject(
                            name = "統計數據範例",
                            value = "{\"totalRequests\":1000,\"successfulRequests\":950,\"failedRequests\":30,\"timeoutRequests\":15,\"errorRequests\":5,\"successRate\":95.0}"
                    )
            )
    )
    public ResponseEntity<NatsStatistics> getStatistics() {
        log.debug("Getting NATS statistics");
        
        NatsStatistics statistics = orchestrationService.getStatistics();
        return ResponseEntity.ok(statistics);
    }

    @PostMapping("/test/echo")
    @Operation(
            summary = "測試回音功能",
            description = "測試NATS連接和消息處理功能，發送測試消息並接收回音響應。",
            tags = {"Testing"}
    )
    @ApiResponse(
            responseCode = "200",
            description = "回音測試成功",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = NatsRequestResponse.class)
            )
    )
    public CompletableFuture<ResponseEntity<NatsRequestResponse>> testEcho(
            @Parameter(
                    description = "回音測試參數",
                    required = true,
                    content = @Content(
                            examples = @ExampleObject(
                                    name = "回音測試",
                                    value = "{\"message\":\"Hello NATS\",\"metadata\":\"test-data\"}"
                            )
                    )
            )
            @Valid @RequestBody TestEchoDto echoDto) {
        log.info("Test echo request - Message: {}", echoDto.getMessage());
        
        NatsRequest request = new NatsRequest();
        request.setSubject("test.echo");
        request.setPayload(echoDto);
        
        return orchestrationService.sendRequestWithTracking(request)
                .thenApply(response -> ResponseEntity.ok(response));
    }

    @PostMapping("/test/timeout")
    @Operation(
            summary = "測試超時處理",
            description = "測試NATS服務的超時處理機制，用於驗證系統的錯誤處理能力。",
            tags = {"Testing"}
    )
    @ApiResponse(
            responseCode = "200",
            description = "超時測試執行完成",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = NatsRequestResponse.class)
            )
    )
    public CompletableFuture<ResponseEntity<NatsRequestResponse>> testTimeout() {
        log.info("Test timeout request");
        
        NatsRequest request = new NatsRequest();
        request.setSubject("test.timeout");
        request.setPayload(new TestPayload("timeout test"));
        
        return orchestrationService.sendRequestWithTracking(request)
                .thenApply(response -> ResponseEntity.ok(response));
    }

    @PostMapping("/test/error")
    @Operation(
            summary = "測試錯誤處理",
            description = "測試NATS服務的錯誤處理機制，用於驗證系統的異常處理能力。",
            tags = {"Testing"}
    )
    @ApiResponse(
            responseCode = "200",
            description = "錯誤測試執行完成",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = NatsRequestResponse.class)
            )
    )
    public CompletableFuture<ResponseEntity<NatsRequestResponse>> testError() {
        log.info("Test error request");
        
        NatsRequest request = new NatsRequest();
        request.setSubject("test.error");
        request.setPayload(new TestPayload("error test"));
        
        return orchestrationService.sendRequestWithTracking(request)
                .thenApply(response -> ResponseEntity.ok(response));
    }

    @GetMapping("/health")
    @Operation(
            summary = "健康檢查",
            description = "檢查NATS服務的健康狀態，包括連接狀態、統計信息等。",
            tags = {"Health Check"}
    )
    @ApiResponse(
            responseCode = "200",
            description = "健康檢查成功",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = HealthStatus.class),
                    examples = @ExampleObject(
                            name = "健康狀態範例",
                            value = "{\"status\":\"UP\",\"timestamp\":\"2024-01-01T10:00:00\",\"totalRequests\":1000,\"successRate\":95.0}"
                    )
            )
    )
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
    @Schema(description = "NATS請求參數")
    public static class NatsRequestDto {
        @Schema(
                description = "NATS主題名稱",
                example = "user.profile",
                required = true
        )
        @NotBlank(message = "Subject is required")
        private String subject;
        
        @Schema(
                description = "請求負載數據，可以是任意JSON對象",
                example = "{\"userId\": 123, \"action\": \"get_profile\"}",
                required = true
        )
        @NotNull(message = "Payload is required")
        private Object payload;
    }

    @Data
    @Schema(description = "NATS發布參數")
    public static class NatsPublishDto {
        @Schema(
                description = "NATS主題名稱",
                example = "events.user.created",
                required = true
        )
        @NotBlank(message = "Subject is required")
        private String subject;
        
        @Schema(
                description = "發布的消息負載，可以是任意JSON對象",
                example = "{\"userId\": 123, \"name\": \"John Doe\", \"email\": \"john@example.com\"}",
                required = true
        )
        @NotNull(message = "Payload is required")
        private Object payload;
    }
    
    @Data
    @Schema(description = "NATS發布響應")
    public static class NatsPublishResponse {
        @Schema(description = "請求ID，用於後續追蹤", example = "req-12345")
        private String requestId;
        
        @Schema(description = "發布狀態", example = "PUBLISHED", allowableValues = {"PUBLISHED", "FAILED"})
        private String status;
        
        @Schema(description = "響應消息", example = "Message published successfully")
        private String message;
        
        @Schema(description = "NATS主題", example = "events.user.created")
        private String subject;
        
        @Schema(description = "狀態追蹤URL", example = "/api/nats/status/req-12345")
        private String trackingUrl;
        
        @Schema(description = "時間戳", example = "2024-01-01T10:00:00Z")
        private String timestamp;
    }

    @Data
    @Schema(description = "測試回音參數")
    public static class TestEchoDto {
        @Schema(
                description = "測試消息內容",
                example = "Hello NATS",
                required = true
        )
        @NotBlank(message = "Message is required")
        private String message;
        
        @Schema(
                description = "附加的元數據",
                example = "test-metadata"
        )
        private String metadata;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "測試負載數據")
    public static class TestPayload {
        @Schema(description = "測試數據內容", example = "test data")
        private String data;
    }

    @Data
    @Schema(description = "健康狀態信息")
    public static class HealthStatus {
        @Schema(description = "服務狀態", example = "UP", allowableValues = {"UP", "DOWN"})
        private String status;
        
        @Schema(description = "檢查時間戳", example = "2024-01-01T10:00:00")
        private java.time.LocalDateTime timestamp;
        
        @Schema(description = "總請求數", example = "1000")
        private long totalRequests;
        
        @Schema(description = "成功率百分比", example = "95.5")
        private double successRate;
    }
}