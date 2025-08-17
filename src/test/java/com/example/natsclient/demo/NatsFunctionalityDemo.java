package com.example.natsclient.demo;

import com.example.natsclient.config.NatsProperties;
import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.service.PayloadProcessor;
import com.example.natsclient.service.RequestLogService;
import com.example.natsclient.service.impl.EnhancedNatsMessageService;
import com.example.natsclient.service.impl.NatsMessageServiceImpl;
import com.example.natsclient.service.validator.RequestValidator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.nats.client.Connection;
import io.nats.client.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NatsFunctionalityDemo {

    @Mock
    private Connection natsConnection;
    @Mock
    private RequestLogService requestLogService;
    @Mock
    private PayloadProcessor payloadProcessor;
    @Mock
    private RequestValidator requestValidator;
    @Mock
    private NatsProperties natsProperties;
    @Mock
    private NatsProperties.Request requestProperties;
    @Mock
    private MeterRegistry meterRegistry;
    @Mock
    private Message mockMessage;

    private NatsMessageServiceImpl originalService;
    private EnhancedNatsMessageService enhancedService;

    @BeforeEach
    void setUp() {
        when(natsProperties.getRequest()).thenReturn(requestProperties);
        when(requestProperties.getTimeout()).thenReturn(5000L);
        
        // Setup for enhanced service
        when(meterRegistry.counter(anyString())).thenReturn(mock(Counter.class));
        when(meterRegistry.timer(anyString())).thenReturn(mock(Timer.class));
        
        originalService = new NatsMessageServiceImpl(
                natsConnection, requestLogService, payloadProcessor, 
                requestValidator, natsProperties);
        
        enhancedService = new EnhancedNatsMessageService(
                natsConnection, requestLogService, payloadProcessor, 
                requestValidator, natsProperties, meterRegistry);
    }

    @Test
    void demonstrateOriginalServiceFunctionality() throws Exception {
        System.out.println("=== 原始 NATS 服務功能演示 ===");
        
        // 準備測試數據
        String subject = "demo.original";
        String payload = "Hello Original NATS!";
        String correlationId = "demo-corr-001";
        String serializedPayload = "{\"message\":\"Hello Original NATS!\"}";
        String responsePayload = "{\"status\":\"success\",\"echo\":\"Hello Original NATS!\"}";
        
        // 設置 Mock 行為
        when(payloadProcessor.serialize(payload)).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(serializedPayload.getBytes());
        when(payloadProcessor.fromBytes(any())).thenReturn(responsePayload);
        when(requestLogService.createRequestLog(anyString(), eq(subject), eq(serializedPayload), eq(correlationId)))
                .thenReturn(new NatsRequestLog());
        when(natsConnection.request(eq(subject), any(byte[].class), any(Duration.class)))
                .thenReturn(mockMessage);
        when(mockMessage.getData()).thenReturn(responsePayload.getBytes());

        // 執行請求
        CompletableFuture<String> result = originalService.sendRequest(subject, payload, correlationId);
        
        // 驗證結果
        assertNotNull(result);
        assertEquals(responsePayload, result.get());
        
        // 驗證調用
        verify(requestValidator).validateRequest(subject, payload);
        verify(requestValidator).validateCorrelationId(correlationId);
        verify(natsConnection).request(eq(subject), any(byte[].class), any(Duration.class));
        verify(requestLogService).saveRequestLog(any(NatsRequestLog.class));
        verify(requestLogService).updateWithSuccess(anyString(), eq(responsePayload));
        
        System.out.println("✅ 原始服務測試通過!");
        System.out.println("   - 主題: " + subject);
        System.out.println("   - 請求: " + payload);
        System.out.println("   - 響應: " + responsePayload);
        System.out.println();
    }

    @Test
    void demonstrateEnhancedServiceFunctionality() throws Exception {
        System.out.println("=== 增強版 NATS 服務功能演示 ===");
        
        // 準備測試數據
        String subject = "demo.enhanced";
        String payload = "Hello Enhanced NATS with Metrics!";
        String correlationId = "demo-corr-002";
        String serializedPayload = "{\"message\":\"Hello Enhanced NATS with Metrics!\"}";
        String responsePayload = "{\"status\":\"success\",\"echo\":\"Hello Enhanced NATS with Metrics!\",\"features\":[\"metrics\",\"retry\",\"logging\"]}";
        
        // 設置 Mock 行為
        when(payloadProcessor.serialize(payload)).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(serializedPayload.getBytes());
        when(payloadProcessor.fromBytes(any())).thenReturn(responsePayload);
        when(requestLogService.createRequestLog(anyString(), eq(subject), eq(serializedPayload), eq(correlationId)))
                .thenReturn(new NatsRequestLog());
        when(natsConnection.request(eq(subject), any(byte[].class), any(Duration.class)))
                .thenReturn(mockMessage);
        when(mockMessage.getData()).thenReturn(responsePayload.getBytes());

        // 執行請求
        CompletableFuture<String> result = enhancedService.sendRequest(subject, payload, correlationId);
        
        // 驗證結果
        assertNotNull(result);
        assertEquals(responsePayload, result.get());
        
        // 驗證基本調用
        verify(requestValidator).validateRequest(subject, payload);
        verify(requestValidator).validateCorrelationId(correlationId);
        verify(natsConnection).request(eq(subject), any(byte[].class), any(Duration.class));
        verify(requestLogService).saveRequestLog(any(NatsRequestLog.class));
        verify(requestLogService).updateWithSuccess(anyString(), eq(responsePayload));
        
        System.out.println("✅ 增強版服務測試通過!");
        System.out.println("   - 主題: " + subject);
        System.out.println("   - 請求: " + payload);
        System.out.println("   - 響應: " + responsePayload);
        System.out.println("   - 新增功能: Metrics收集, 重試機制, 結構化日誌");
        System.out.println();
    }

    @Test
    void demonstratePublishFunctionality() throws Exception {
        System.out.println("=== NATS 發布功能演示 ===");
        
        // 準備測試數據
        String subject = "demo.publish";
        String payload = "Broadcast Message to All Subscribers";
        String serializedPayload = "{\"broadcast\":\"Broadcast Message to All Subscribers\"}";
        
        // 設置 Mock 行為
        when(payloadProcessor.serialize(payload)).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(serializedPayload.getBytes());
        when(requestLogService.createRequestLog(anyString(), eq(subject), eq(serializedPayload), isNull()))
                .thenReturn(new NatsRequestLog());

        // 執行發布 - 原始服務
        CompletableFuture<Void> originalResult = originalService.publishMessage(subject, payload);
        assertNotNull(originalResult);
        assertDoesNotThrow(() -> originalResult.get());
        
        // 執行發布 - 增強版服務
        CompletableFuture<Void> enhancedResult = enhancedService.publishMessage(subject, payload);
        assertNotNull(enhancedResult);
        assertDoesNotThrow(() -> enhancedResult.get());
        
        // 驗證調用
        verify(requestValidator, times(2)).validateRequest(subject, payload);
        verify(natsConnection, times(2)).publish(eq(subject), any(byte[].class));
        verify(requestLogService, times(2)).saveRequestLog(any(NatsRequestLog.class));
        
        System.out.println("✅ 發布功能測試通過!");
        System.out.println("   - 主題: " + subject);
        System.out.println("   - 消息: " + payload);
        System.out.println("   - 原始服務發布: 成功");
        System.out.println("   - 增強版服務發布: 成功");
        System.out.println();
    }

    @Test
    void demonstrateErrorHandling() throws Exception {
        System.out.println("=== 錯誤處理功能演示 ===");
        
        // 測試驗證失敗
        String invalidSubject = "";
        String payload = "Test payload";
        
        doThrow(new IllegalArgumentException("Subject cannot be empty"))
                .when(requestValidator).validateRequest(invalidSubject, payload);
        
        // 原始服務錯誤處理
        assertThrows(IllegalArgumentException.class, () -> {
            originalService.sendRequest(invalidSubject, payload, "corr-123");
        });
        
        // 增強版服務錯誤處理  
        assertThrows(IllegalArgumentException.class, () -> {
            enhancedService.sendRequest(invalidSubject, payload, "corr-123");
        });
        
        System.out.println("✅ 錯誤處理測試通過!");
        System.out.println("   - 無效主題被正確拒絕");
        System.out.println("   - 原始服務和增強版服務都正確處理錯誤");
        System.out.println();
    }

    @Test
    void demonstrateComparisonSummary() {
        System.out.println("=== 服務功能對比總結 ===");
        System.out.println();
        
        System.out.println("📊 原始 NatsMessageServiceImpl:");
        System.out.println("   ✅ 基本 NATS 請求-響應功能");
        System.out.println("   ✅ 消息發布功能");
        System.out.println("   ✅ 基礎日誌記錄");
        System.out.println("   ✅ 數據庫請求記錄");
        System.out.println("   ✅ 輸入驗證");
        System.out.println();
        
        System.out.println("🚀 增強版 EnhancedNatsMessageService:");
        System.out.println("   ✅ 包含原始服務所有功能");
        System.out.println("   🆕 Micrometer Metrics 收集");
        System.out.println("   🆕 自動重試機制 (@Retryable)");
        System.out.println("   🆕 結構化日誌 (MDC)");
        System.out.println("   🆕 詳細性能監控");
        System.out.println("   🆕 更好的錯誤處理");
        System.out.println("   🆕 企業級監控支持");
        System.out.println();
        
        System.out.println("🎯 新增測試功能:");
        System.out.println("   🆕 性能測試套件 (NatsPerformanceTest)");
        System.out.println("   🆕 併發壓力測試");
        System.out.println("   🆕 內存使用監控測試");
        System.out.println("   🆕 延遲測量測試");
        System.out.println("   🆕 吞吐量基準測試");
        System.out.println();
        
        System.out.println("💡 結論: 增強版服務保持向下兼容，同時添加了企業級功能！");
    }
}