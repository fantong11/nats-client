# NATS Client 測試指南

## 測試架構概述

本項目包含完整的單元測試和整合測試，使用 **Mock** 方式驗證所有核心功能。

### 測試覆蓋範圍

#### 1. 單元測試 (Unit Tests)
- ✅ **NatsMessageServiceImpl** - NATS 消息處理核心邏輯
- ✅ **RequestLogServiceImpl** - 數據庫記錄服務
- ✅ **JsonPayloadProcessor** - JSON 序列化/反序列化
- ✅ **RequestValidator** - 請求驗證邏輯
- ✅ **NatsOrchestrationService** - 業務編排服務
- ✅ **NatsController** - REST API 控制器

#### 2. 整合測試 (Integration Tests)
- ✅ **End-to-End 請求流程** - 完整的請求-響應週期
- ✅ **數據庫持久化** - 數據正確存儲和檢索
- ✅ **錯誤處理** - 異常情況的正確處理
- ✅ **統計功能** - 數據統計的準確性

## 測試技術棧

```
測試框架: JUnit 5
Mock 框架: Mockito
Web 測試: Spring Boot Test + MockMvc
數據庫測試: H2 In-Memory Database
覆蓋率工具: JaCoCo
```

## 測試執行

### 1. 執行所有測試
```bash
mvn test
```

### 2. 只執行單元測試
```bash
mvn test -Dtest="!*IntegrationTest"
```

### 3. 只執行整合測試
```bash
mvn test -Dtest="*IntegrationTest"
```

### 4. 生成覆蓋率報告
```bash
mvn clean test jacoco:report
```

### 5. 使用批次檔 (Windows)
```bash
run-tests.bat
```

## 關鍵測試場景

### NatsMessageServiceImpl 測試
```java
@Test
void sendRequest_Success_ShouldReturnSuccessfulResponse() {
    // 模擬成功的 NATS 請求-響應流程
    // 驗證: 序列化、NATS 調用、數據庫更新、響應處理
}

@Test
void sendRequest_TimeoutResponse_ShouldThrowNatsTimeoutException() {
    // 模擬 NATS 超時場景 (返回 null)
    // 驗證: 超時處理、錯誤記錄、異常拋出
}
```

### RequestValidator 測試
```java
@Test
void validateRequest_NullSubject_ShouldThrowIllegalArgumentException() {
    // 測試空主題驗證
}

@Test
void validateCorrelationId_EmptyString_ShouldThrowIllegalArgumentException() {
    // 測試關聯 ID 驗證
}
```

### JsonPayloadProcessor 測試
```java
@Test
void serialize_ObjectMapperThrowsException_ShouldThrowPayloadProcessingException() {
    // 測試序列化失敗處理
}

@Test
void roundTrip_ToeBytesAndFromBytes_ShouldPreserveData() {
    // 測試序列化-反序列化的數據完整性
}
```

### NatsController 測試
```java
@Test
void sendRequest_InvalidSubject_ShouldReturnBadRequest() {
    // 測試 HTTP 400 錯誤響應
}

@Test
void getStatistics_ShouldReturnStatistics() {
    // 測試統計 API 端點
}
```

### 整合測試
```java
@Test
void endToEndRequest_ShouldPersistToDatabase() {
    // 完整的 HTTP -> Service -> Database 流程測試
}

@Test
void natsConnectionFailure_ShouldHandleGracefully() {
    // 測試 NATS 連接失敗的處理
}
```

## Mock 策略

### 1. 外部依賴 Mock
- **NATS Connection**: 模擬所有 NATS 操作
- **Database Repository**: 模擬數據庫操作
- **ObjectMapper**: 模擬 JSON 處理異常

### 2. 測試數據隔離
- **H2 In-Memory DB**: 每個測試獨立的數據庫實例
- **@Transactional**: 測試結束後自動回滾
- **Mock Reset**: 每個測試前重置 Mock 狀態

### 3. 異步測試
```java
CompletableFuture<String> result = service.sendRequest(...);
assertEquals(expectedResponse, result.get()); // 等待異步完成
```

## 覆蓋率目標

| 組件 | 目標覆蓋率 | 重點測試項目 |
|------|------------|--------------|
| NatsMessageServiceImpl | 95%+ | 成功/失敗/超時場景 |
| RequestLogServiceImpl | 90%+ | CRUD 操作 |
| JsonPayloadProcessor | 95%+ | 序列化異常處理 |
| RequestValidator | 100% | 所有驗證規則 |
| NatsController | 85%+ | HTTP 端點測試 |

## 測試數據管理

### 測試用例數據
```java
private final String testSubject = "test.subject";
private final Object testPayload = new TestPayload("test data");
private final String serializedPayload = "{\"data\":\"test data\"}";
```

### Mock 響應設置
```java
when(natsConnection.request(eq(testSubject), eq(payloadBytes), any(Duration.class)))
    .thenReturn(mockMessage);
when(mockMessage.getData()).thenReturn(responseBytes);
```

## 錯誤測試場景

### 1. 驗證失敗
- 空主題、空載荷
- 無效關聯 ID

### 2. 序列化失敗
- JSON 處理異常
- 編碼問題

### 3. NATS 連接問題
- 連接超時
- 網路異常
- 響應為空

### 4. 數據庫錯誤
- 保存失敗
- 查詢異常

## 性能測試

### 並發測試
```java
@Test
void sendRequest_ConcurrentRequests_ShouldHandleCorrectly() {
    // 測試並發請求處理
}
```

### 大數據測試
```java
@Test
void serialize_LargePayload_ShouldHandleCorrectly() {
    String largePayload = "a".repeat(10000);
    // 測試大載荷處理
}
```

## 測試報告

### 執行後檢查
1. **控制台輸出**: 測試執行結果
2. **Surefire 報告**: `target/surefire-reports/`
3. **JaCoCo 覆蓋率**: `target/site/jacoco/index.html`

### CI/CD 整合
```yaml
# 示例 GitHub Actions 配置
- name: Run Tests
  run: mvn clean test
- name: Generate Coverage Report  
  run: mvn jacoco:report
```

## 疑難排解

### 常見問題
1. **H2 數據庫初始化失敗**: 檢查 `application-test.yml` 配置
2. **Mock 不生效**: 確認 `@MockBean` 和 `@Mock` 使用正確
3. **異步測試超時**: 增加 `CompletableFuture.get()` 超時時間

### 調試技巧
```java
@Test
void debugTest() {
    // 啟用詳細日誌
    System.setProperty("logging.level.com.example.natsclient", "DEBUG");
    
    // 驗證 Mock 調用
    verify(mockService, times(1)).method(any());
    
    // 檢查實際數據
    List<NatsRequestLog> logs = repository.findAll();
    logs.forEach(log -> System.out.println(log));
}
```

這套測試框架確保了程式的可靠性和穩定性，通過全面的 Mock 測試驗證了所有關鍵功能！