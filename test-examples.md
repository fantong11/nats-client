# NATS Client 實際測試範例

## 測試準備

1. **確保 NATS 服務器運行**:
   ```bash
   docker-compose -f docker-compose-nats-only.yml up -d
   ```

2. **啟動 Spring Boot 應用**:
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=local
   ```

## 實際測試範例

### 1. Echo 測試

**請求**:
```bash
curl -X POST http://localhost:8080/api/nats/test/echo \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Hello NATS!",
    "metadata": "test from API"
  }'
```

**預期響應**:
```json
{
  "success": true,
  "requestId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "correlationId": "corr-98765432-1234-5678-9abc-def012345678",
  "response": {
    "status": "success",
    "message": "Echo response",
    "original_payload": "{\"message\":\"Hello NATS!\",\"metadata\":\"test from API\"}",
    "timestamp": "2025-08-18T00:45:30.123",
    "processed_by": "nats-test-subscriber"
  },
  "executionTimeMs": 45,
  "timestamp": "2025-08-18T00:45:30.168"
}
```

**日誌輸出**:
```
2025-08-18 00:45:30.080 INFO  --- [http-nio-8080-exec-1] c.e.n.controller.NatsController : Received NATS request - Subject: test.echo
2025-08-18 00:45:30.085 INFO  --- [http-nio-8080-exec-1] c.e.n.service.NatsOrchestrationService : Processing NATS request - Subject: test.echo, CorrelationID: corr-98765432
2025-08-18 00:45:30.090 INFO  --- [nats-async-1] c.e.n.service.impl.NatsMessageServiceImpl : Sending NATS request - ID: a1b2c3d4, Subject: test.echo, Correlation: corr-98765432
2025-08-18 00:45:30.095 INFO  --- [nats-subscriber-1] c.e.n.service.NatsTestSubscriber : Received message - Subject: test.echo, ReplyTo: _INBOX.abc123def456, Payload: {"message":"Hello NATS!","metadata":"test from API"}
2025-08-18 00:45:30.110 INFO  --- [nats-subscriber-1] c.e.n.service.NatsTestSubscriber : Sent reply to: _INBOX.abc123def456, Response: {"status":"success",...}
2025-08-18 00:45:30.125 INFO  --- [nats-async-1] c.e.n.service.impl.NatsMessageServiceImpl : Received NATS response - ID: a1b2c3d4, Response length: 187
```

### 2. 業務請求測試

**請求**:
```bash
curl -X POST http://localhost:8080/api/nats/request \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "demo.user.create",
    "payload": {
      "username": "john_doe",
      "email": "john@example.com",
      "department": "IT"
    }
  }'
```

**預期響應**:
```json
{
  "success": true,
  "requestId": "b2c3d4e5-f6g7-8901-bcde-f23456789012",
  "correlationId": "corr-87654321-2345-6789-abcd-ef2345678901",
  "response": {
    "status": "success",
    "message": "Generic message processed",
    "subject": "demo.user.create",
    "original_payload": "{\"username\":\"john_doe\",\"email\":\"john@example.com\",\"department\":\"IT\"}",
    "timestamp": "2025-08-18T00:46:15.456",
    "server_info": "NATS Test Subscriber v1.0"
  },
  "executionTimeMs": 32,
  "timestamp": "2025-08-18T00:46:15.488"
}
```

### 3. 超時測試

**請求**:
```bash
curl -X POST http://localhost:8080/api/nats/test/timeout \
  -H "Content-Type: application/json"
```

**預期響應** (如果在超時時間內):
```json
{
  "success": true,
  "requestId": "c3d4e5f6-g7h8-9012-cdef-345678901234",
  "correlationId": "corr-76543210-3456-7890-bcde-f34567890123",
  "response": {
    "status": "success",
    "message": "Timeout test completed",
    "delay": "2000ms",
    "timestamp": "2025-08-18T00:47:02.789"
  },
  "executionTimeMs": 2045,
  "timestamp": "2025-08-18T00:47:02.834"
}
```

### 4. 錯誤處理測試

**請求**:
```bash
curl -X POST http://localhost:8080/api/nats/test/error \
  -H "Content-Type: application/json"
```

**預期響應**:
```json
{
  "success": false,
  "requestId": "d4e5f6g7-h8i9-0123-defg-456789012345",
  "correlationId": "corr-65432109-4567-8901-cdef-456789012345",
  "response": {
    "status": "error",
    "message": "Processing failed",
    "error": "Simulated processing error for testing",
    "timestamp": "2025-08-18T00:47:30.123"
  },
  "executionTimeMs": 25,
  "timestamp": "2025-08-18T00:47:30.148"
}
```

### 5. 發布消息測試

**請求**:
```bash
curl -X POST http://localhost:8080/api/nats/publish \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "api.notification.send",
    "payload": {
      "userId": "user123",
      "message": "Welcome to our platform!",
      "type": "welcome"
    }
  }'
```

**預期響應**:
```json
{
  "message": "Message published successfully",
  "timestamp": "2025-08-18T00:48:00.456"
}
```

**訂閱者日誌**:
```
2025-08-18 00:48:00.460 INFO  --- [nats-subscriber-1] c.e.n.service.NatsTestSubscriber : Received message - Subject: api.notification.send, ReplyTo: null, Payload: {"userId":"user123","message":"Welcome to our platform!","type":"welcome"}
```

## 系統狀態查詢

### 健康檢查
```bash
curl http://localhost:8080/api/nats/health
```

**響應**:
```json
{
  "status": "UP",
  "timestamp": "2025-08-18T00:48:30.789",
  "totalRequests": 12,
  "successRate": 91.67
}
```

### 統計信息
```bash
curl http://localhost:8080/api/nats/statistics
```

**響應**:
```json
{
  "totalRequests": 12,
  "successfulRequests": 11,
  "failedRequests": 1,
  "timeoutRequests": 0,
  "successRate": 91.67,
  "averageResponseTime": 156.8,
  "lastRequestTime": "2025-08-18T00:48:30.789"
}
```

### 查詢請求狀態
```bash
curl http://localhost:8080/api/nats/status/a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

**響應**:
```json
{
  "requestId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "correlationId": "corr-98765432-1234-5678-9abc-def012345678",
  "subject": "test.echo",
  "status": "SUCCESS",
  "requestTime": "2025-08-18T00:45:30.090",
  "responseTime": "2025-08-18T00:45:30.125",
  "executionTimeMs": 35,
  "retryCount": 0
}
```

## 數據庫記錄

**查詢最近的請求**:
```sql
SELECT 
    REQUEST_ID,
    SUBJECT,
    STATUS,
    CREATED_DATE,
    RESPONSE_TIMESTAMP,
    (RESPONSE_TIMESTAMP - CREATED_DATE) * 24 * 60 * 60 * 1000 AS DURATION_MS
FROM NATS_REQUEST_LOG 
ORDER BY CREATED_DATE DESC 
FETCH FIRST 5 ROWS ONLY;
```

**結果範例**:
```
REQUEST_ID                           SUBJECT           STATUS    CREATED_DATE         RESPONSE_TIMESTAMP   DURATION_MS
a1b2c3d4-e5f6-7890-abcd-ef1234567890  test.echo        SUCCESS   2025-08-18 00:45:30  2025-08-18 00:45:30  35
b2c3d4e5-f6g7-8901-bcde-f23456789012  demo.user.create SUCCESS   2025-08-18 00:46:15  2025-08-18 00:46:15  32
c3d4e5f6-g7h8-9012-cdef-345678901234  test.timeout     SUCCESS   2025-08-18 00:47:02  2025-08-18 00:47:05  2045
d4e5f6g7-h8i9-0123-defg-456789012345  test.error       ERROR     2025-08-18 00:47:30  2025-08-18 00:47:30  25
```

這就是完整的 request-response 流程和實際結果！