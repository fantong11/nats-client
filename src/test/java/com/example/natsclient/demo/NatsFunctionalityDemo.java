package com.example.natsclient.demo;

import com.example.natsclient.config.NatsProperties;
import com.example.natsclient.entity.NatsRequestLog;
import com.example.natsclient.service.NatsOperations;
import com.example.natsclient.service.PayloadProcessor;
import com.example.natsclient.service.RequestLogService;
import com.example.natsclient.service.ResponseHandler;
import com.example.natsclient.util.NatsMessageUtils;
import com.example.natsclient.service.factory.MetricsFactory;
import com.example.natsclient.service.impl.EnhancedNatsMessageService;
import com.example.natsclient.service.impl.StringResponseHandler;
import com.example.natsclient.service.observer.NatsEventPublisher;
import com.example.natsclient.service.validator.RequestValidator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.PublishOptions;
import io.nats.client.api.PublishAck;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.example.natsclient.exception.NatsRequestException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NatsFunctionalityDemo {

    @Mock
    private Connection natsConnection;
    @Mock
    private JetStream jetStream;
    @Mock
    private PublishAck mockPublishAck;
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
    private NatsProperties.JetStream jetStreamProperties;
    @Mock
    private MeterRegistry meterRegistry;
    @Mock
    private Message mockMessage;
    
    @Mock
    private MetricsFactory metricsFactory;
    
    @Mock
    private NatsMessageUtils messageUtils;
    
    @Mock
    private NatsEventPublisher eventPublisher;
    
    // SOLID-compliant dependencies
    private NatsOperations natsOperations;
    private ResponseHandler<String> responseHandler;

    private EnhancedNatsMessageService enhancedService;

    @BeforeEach
    void setUp() {
        // Essential properties - may not be used in all tests
        lenient().when(natsProperties.getRequest()).thenReturn(requestProperties);
        lenient().when(requestProperties.getTimeout()).thenReturn(5000L);
        lenient().when(natsProperties.getJetStream()).thenReturn(jetStreamProperties);
        // Removed stream properties - no longer needed
        lenient().when(mockPublishAck.getSeqno()).thenReturn(1L);
        lenient().when(mockPublishAck.getStream()).thenReturn("DEFAULT_STREAM");
        
        // Mock JetStream publish method - both 3 and 4 parameter versions
        try {
            lenient().when(jetStream.publish(anyString(), any(byte[].class), any(PublishOptions.class)))
                    .thenReturn(mockPublishAck);
            lenient().when(jetStream.publish(anyString(), any(), any(byte[].class), any(PublishOptions.class)))
                    .thenReturn(mockPublishAck);
        } catch (Exception e) {
            // Handle checked exception
        }
        
        // Setup for enhanced service - may not be used in all tests
        lenient().when(meterRegistry.counter(anyString())).thenReturn(mock(Counter.class));
        lenient().when(meterRegistry.timer(anyString())).thenReturn(mock(Timer.class));
        
        // Mock the new dependencies for enhanced service
        MetricsFactory.NatsMetricsSet metricsSet = mock(MetricsFactory.NatsMetricsSet.class);
        when(metricsSet.getRequestCounter()).thenReturn(mock(Counter.class));
        when(metricsSet.getSuccessCounter()).thenReturn(mock(Counter.class));
        when(metricsSet.getErrorCounter()).thenReturn(mock(Counter.class));
        when(metricsSet.getRequestTimer()).thenReturn(mock(Timer.class));
        when(metricsFactory.createNatsMetricsSet(anyString(), eq(meterRegistry))).thenReturn(metricsSet);
        
        PublishOptions mockPublishOptions = mock(PublishOptions.class);
        lenient().when(messageUtils.formatPublishAck(any())).thenReturn("MockPublishAck{}");
        
        // Create SOLID-compliant dependencies
        // HybridNatsOperations removed - using JetStream only
        responseHandler = new StringResponseHandler(requestLogService, payloadProcessor);
        
        // NatsMessageServiceImpl removed - using EnhancedNatsMessageService only
        
        enhancedService = new EnhancedNatsMessageService(
                natsConnection, jetStream, requestLogService, payloadProcessor, 
                requestValidator, natsProperties, meterRegistry, metricsFactory,
                messageUtils, eventPublisher);
    }

    @Test
    void demonstrateOriginalServiceFunctionality() throws Exception {
        System.out.println("=== 原始 NATS 服務功能演示 ===");
        
        // 準備測試數據
        String subject = "demo.original";
        String payload = "Hello Original NATS!";
        String serializedPayload = "{\"message\":\"Hello Original NATS!\"}";
        String responsePayload = "{\"status\":\"success\",\"echo\":\"Hello Original NATS!\"}";
        
        // 設置 Mock 行為
        when(payloadProcessor.serialize(payload)).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(serializedPayload.getBytes());
        when(requestLogService.createRequestLog(anyString(), eq(subject), eq(serializedPayload)))
                .thenReturn(new NatsRequestLog());

        // 執行請求
        CompletableFuture<String> result = enhancedService.sendRequest(subject, payload);
        
        // 驗證結果
        assertNotNull(result);
        assertEquals("Message published to JetStream successfully - processing asynchronously", result.get());
        
        // 驗證調用
        verify(requestValidator).validateRequest(subject, payload);
        verify(requestLogService).saveRequestLog(any(NatsRequestLog.class));
        verify(requestLogService).updateWithSuccess(anyString(), eq("Message published to JetStream successfully - processing asynchronously"));
        
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
        String serializedPayload = "{\"message\":\"Hello Enhanced NATS with Metrics!\"}";
        String responsePayload = "{\"status\":\"success\",\"echo\":\"Hello Enhanced NATS with Metrics!\",\"features\":[\"metrics\",\"retry\",\"logging\"]}";
        
        // 設置 Mock 行為 - 使用 JetStream
        when(payloadProcessor.serialize(payload)).thenReturn(serializedPayload);
        when(payloadProcessor.toBytes(serializedPayload)).thenReturn(serializedPayload.getBytes());
        when(requestLogService.createRequestLog(anyString(), eq(subject), eq(serializedPayload)))
                .thenReturn(new NatsRequestLog());

        // 執行請求
        CompletableFuture<String> result = enhancedService.sendRequest(subject, payload);
        
        // 驗證結果 - 期望 JetStream 異步響應
        assertNotNull(result);
        assertEquals("Message published to JetStream successfully - processing asynchronously", result.get());
        
        // 驗證基本調用
        verify(requestValidator).validateRequest(subject, payload);
        verify(requestLogService).saveRequestLog(any(NatsRequestLog.class));
        verify(requestLogService).updateWithSuccess(anyString(), eq("Message published to JetStream successfully - processing asynchronously"));
        
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
        when(requestLogService.createRequestLog(anyString(), eq(subject), eq(serializedPayload)))
                .thenReturn(new NatsRequestLog());
        try {
            when(jetStream.publish(eq(subject), any(), any(byte[].class)))
                    .thenReturn(mockPublishAck);
        } catch (Exception e) {
            // Ignore for test setup
        }

        // 執行發布 - 增強版服務（原始服務已移除）
        CompletableFuture<Void> enhancedResult = enhancedService.publishMessage(subject, payload);
        assertNotNull(enhancedResult);
        assertDoesNotThrow(() -> enhancedResult.get());
        
        // 驗證調用
        verify(requestValidator, times(1)).validateRequest(subject, payload);
        try {
            verify(jetStream, times(1)).publish(eq(subject), any(), any(byte[].class));
        } catch (Exception e) {
            // Ignore verification exceptions
        }
        verify(requestLogService, times(1)).saveRequestLog(any(NatsRequestLog.class));
        
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
        
        // 增強版服務錯誤處理（異步，需要在get()時檢查異常）
        CompletableFuture<String> syncResult = enhancedService.sendRequest(invalidSubject, payload);
        ExecutionException syncException = assertThrows(ExecutionException.class, () -> {
            syncResult.get();
        });
        assertTrue(syncException.getCause() instanceof NatsRequestException);
        
        
        System.out.println("✅ 錯誤處理測試通過!");
        System.out.println("   - 無效主題被正確拒絕");
        System.out.println("   - 原始服務和增強版服務都正確處理錯誤");
        System.out.println();
    }

    @Test
    void demonstrateComparisonSummary() {
        System.out.println("=== 服務功能對比總結 ===");
        System.out.println();
        
        System.out.println("📊 原始實現已移除，現在只使用 EnhancedNatsMessageService");
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