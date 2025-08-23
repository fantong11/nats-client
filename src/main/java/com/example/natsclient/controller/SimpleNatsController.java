package com.example.natsclient.controller;

import com.example.natsclient.service.NatsOrchestrationService;
import com.example.natsclient.service.NatsOrchestrationService.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/simple")
@Slf4j
@Tag(name = "Simple NATS API", description = "簡化的 NATS API 測試")
public class SimpleNatsController {

    @Autowired
    private NatsOrchestrationService orchestrationService;

    @GetMapping("/health")
    @Operation(summary = "健康檢查", description = "檢查服務健康狀態")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Service is healthy");
    }

    @PostMapping("/test")
    @Operation(summary = "簡單測試", description = "簡單的 NATS 測試端點")
    public ResponseEntity<String> test(@RequestBody String message) {
        log.info("Received test message: {}", message);
        return ResponseEntity.ok("Message received: " + message);
    }
}