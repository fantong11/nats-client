# NATS Client Request-Response 流程詳解

## 完整流程圖

```
Client Request (HTTP) → Controller → Orchestration → NATS Client → NATS Server → Subscriber → Response
     ↓                      ↓              ↓             ↓             ↓             ↓            ↓
   JSON                 Validation    Correlation    Request       Message       Process      JSON
  Payload               & Tracking       ID          Publish     Distribution   & Reply    Response
     ↓                      ↓              ↓             ↓             ↓             ↓            ↓
  REST API              Database Log   Async Call    Timeout       Subject       Business     HTTP
  Response              Updates        Future        Handling      Matching      Logic        Response
```

## 詳細步驟說明

### 1. HTTP Request 階段
**入口**: `POST /api/nats/request`

**輸入範例**:
```json
{
  "subject": "demo.user.create",
  "payload": {
    "username": "john_doe",
    "email": "john@example.com"
  }
}
```

**處理類**: `NatsController.sendRequest()`
- 驗證輸入參數 (`@Valid`)
- 記錄請求日誌
- 轉發給 Orchestration 服務

### 2. Orchestration 階段
**處理類**: `NatsOrchestrationService.sendRequestWithTracking()`

**步驟**:
1. **生成 Correlation ID**: `UUID.randomUUID().toString()`
2. **請求驗證**: 檢查 subject 和 payload
3. **調用 NATS Client**: 異步發送請求
4. **結果處理**: 包裝響應格式

### 3. NATS Client 階段
**處理類**: `NatsMessageServiceImpl.sendRequest()`

**步驟**:
1. **輸入驗證**: `RequestValidator.validateRequest()`
2. **序列化**: `PayloadProcessor.serialize()` (JSON)
3. **數據庫記錄**: 創建 `NatsRequestLog` 記錄
4. **NATS 請求**: `natsConnection.request(subject, payload, timeout)`
5. **響應處理**: 根據結果更新數據庫

### 4. NATS Server 階段
**NATS Server 處理**:
- 接收請求到指定 subject
- 查找訂閱者
- 分發消息
- 等待響應
- 返回響應給發送者

### 5. Subscriber 階段
**處理類**: `NatsTestSubscriber.handleMessage()`

**步驟**:
1. **接收消息**: 從 NATS server 獲取
2. **路由處理**: 根據 subject 選擇處理邏輯
3. **業務處理**: 執行對應的業務邏輯
4. **生成響應**: 創建 JSON 響應
5. **發送回覆**: 通過 `replyTo` 發送響應

## 各種測試場景的流程和結果

### 場景 1: Echo 測試
**Subject**: `test.echo`

**請求**:
```json
{
  "subject": "test.echo",
  "payload": {
    "message": "Hello NATS!",
    "metadata": "test data"
  }
}
```

**Subscriber 處理** (`processEcho()`):
```java
// 1. 解析原始 payload
// 2. 創建 echo 響應
Map<String, Object> response = new HashMap<>();
response.put("status", "success");
response.put("message", "Echo response");
response.put("original_payload", payload);
response.put("timestamp", LocalDateTime.now().toString());
response.put("processed_by", "nats-test-subscriber");
```

**最終響應**:
```json
{
  "success": true,
  "requestId": "12345-abcde-67890",
  "correlationId": "corr-98765",
  "response": {
    "status": "success",
    "message": "Echo response", 
    "original_payload": "{\"message\":\"Hello NATS!\",\"metadata\":\"test data\"}",
    "timestamp": "2025-08-18T00:45:00",
    "processed_by": "nats-test-subscriber"
  },
  "timestamp": "2025-08-18T00:45:00"
}
```

### 場景 2: 超時測試
**Subject**: `test.timeout`

**Subscriber 處理** (`processTimeout()`):
```java
// 1. 模擬處理延遲
Thread.sleep(2000); // 2秒
// 2. 返回延遲響應
```

**可能結果**:
- **成功**: 如果在超時時間內響應
- **超時**: 如果超過配置的 `nats.request.timeout` (30秒)

### 場景 3: 錯誤測試
**Subject**: `test.error`

**Subscriber 處理** (`processError()`):
```java
// 故意拋出異常
throw new RuntimeException("Simulated processing error for testing");
```

**錯誤響應**:
```json
{
  "status": "error",
  "message": "Processing failed",
  "error": "Simulated processing error for testing",
  "timestamp": "2025-08-18T00:45:00"
}
```

### 場景 4: 通用業務請求
**Subject**: `demo.*` 或 `api.*`

**處理流程**:
1. 匹配 subject 模式
2. 執行通用處理邏輯
3. 返回標準化響應

**響應格式**:
```json
{
  "status": "success",
  "message": "Generic message processed",
  "subject": "demo.user.create",
  "original_payload": "...",
  "timestamp": "2025-08-18T00:45:00",
  "server_info": "NATS Test Subscriber v1.0"
}
```

## 數據庫記錄

每個請求都會在 `NATS_REQUEST_LOG` 表中創建記錄：

**請求開始時**:
```sql
INSERT INTO NATS_REQUEST_LOG (
  REQUEST_ID, SUBJECT, REQUEST_PAYLOAD, CORRELATION_ID,
  STATUS, TIMEOUT_DURATION, CREATED_BY, CREATED_DATE
) VALUES (
  'req-12345', 'test.echo', '{"message":"Hello"}', 'corr-67890',
  'PENDING', 30000, 'SYSTEM', SYSDATE
);
```

**成功響應時**:
```sql
UPDATE NATS_REQUEST_LOG SET
  STATUS = 'SUCCESS',
  RESPONSE_PAYLOAD = '{"status":"success",...}',
  RESPONSE_TIMESTAMP = SYSDATE,
  UPDATED_BY = 'SYSTEM'
WHERE REQUEST_ID = 'req-12345';
```

## 監控和調試

### 1. 日誌輸出範例
```
INFO  - Received NATS request - Subject: test.echo
INFO  - Processing NATS request - Subject: test.echo, CorrelationID: corr-67890
INFO  - Sending NATS request - ID: req-12345, Subject: test.echo, Correlation: corr-67890
INFO  - Received message - Subject: test.echo, ReplyTo: _INBOX.xxx, Payload: {"message":"Hello"}
INFO  - Sent reply to: _INBOX.xxx, Response: {"status":"success",...}
INFO  - Received NATS response - ID: req-12345, Response length: 156
```

### 2. 性能指標
- **請求-響應延遲**: 通常 < 100ms
- **序列化時間**: < 10ms
- **數據庫更新**: < 50ms
- **總處理時間**: < 200ms

### 3. 錯誤處理
- **連接失敗**: 記錄為 ERROR 狀態
- **超時**: 記錄為 TIMEOUT 狀態
- **處理異常**: 返回錯誤響應並記錄

這就是完整的 request-response 流程！每個步驟都有詳細的日誌記錄和數據庫追蹤。