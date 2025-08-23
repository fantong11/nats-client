# NATS Client API Documentation

完整的NATS Client Service API參考文檔，包含所有端點、請求/響應格式和使用示例。

## 📋 目錄

- [API概覽](#api概覽)
- [Swagger UI](#swagger-ui)
- [認證授權](#認證授權)
- [核心端點](#核心端點)
- [監控端點](#監控端點)
- [錯誤處理](#錯誤處理)
- [架構特性](#架構特性)
- [使用示例](#使用示例)

## 🌐 API概覽

### 基礎URL
```
http://localhost:8080/api/nats
```

### 內容類型
所有API請求和響應使用 `application/json`，除非另有說明。

### API版本
當前版本: `1.0.0`

## 📚 Swagger UI

啟動應用後，可以通過以下URL訪問完整的API文檔：

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI文檔**: http://localhost:8080/api-docs
- **API文檔JSON**: http://localhost:8080/v3/api-docs

### Swagger UI 功能
- 🎯 **Try it out**: 直接在頁面測試API
- 🔍 **API過濾**: 快速搜索特定端點
- ⏱️ **請求時長顯示**: 查看API響應時間
- 📖 **完整文檔**: 包含所有參數和響應示例
- 🏷️ **標籤分組**: 按功能分組的API端點

## 🌐 Base Information

### Base URL
```
http://localhost:8080/api/nats
```

### Content Type
All API requests and responses use `application/json` unless otherwise specified.

### API Version
Current version: `0.0.1-SNAPSHOT`

### Response Format
Responses vary by endpoint but generally follow these patterns:

**Success Response (NATS Request)**:
```json
{
  "correlationId": "CORR-uuid",
  "subject": "test.api", 
  "success": true,
  "responsePayload": "response data or timeout message",
  "errorMessage": null,
  "timestamp": "2025-08-23T07:03:56.120538754"
}
```

**Statistics Response**:
```json
{
  "totalRequests": 150,
  "pendingRequests": 2,
  "successfulRequests": 135,
  "failedRequests": 8,
  "timeoutRequests": 5,
  "errorRequests": 2,
  "successRate": 90.0
}
```

**Validation Error Response**:
```json
{
  "fieldErrors": {
    "subject": "Subject is required",
    "payload": "Payload is required"
  },
  "error": "Validation Error",
  "message": "Invalid request parameters",
  "timestamp": "2025-08-23T07:04:38.207469602"
}
```

## 🔐 認證授權

當前服務主要用於內部微服務通信，暫不需要特殊認證。對於生產環境部署，建議實施：

- **API Keys**: 服務間認證
- **JWT Tokens**: 用戶身份驗證
- **OAuth 2.0**: 標準授權協議
- **mTLS**: 雙向TLS認證
- **IP白名單**: 網絡層訪問控制

## 🎯 核心端點

### API端點總覽

| 標籤 | 方法 | 端點 | 描述 |
|------|------|------|------|
| NATS Operations | POST | `/api/nats/request` | 發送NATS請求並等待響應 |
| NATS Operations | POST | `/api/nats/publish` | 發布消息到NATS JetStream |
| Request Tracking | GET | `/api/nats/status/{requestId}` | 根據請求ID查詢狀態 |
| Request Tracking | GET | `/api/nats/status/correlation/{correlationId}` | 根據相關ID查詢狀態 |
| Request Tracking | GET | `/api/nats/requests/{status}` | 根據狀態查詢請求列表 |
| Statistics | GET | `/api/nats/statistics` | 獲取NATS統計信息 |
| Health Check | GET | `/api/nats/health` | 檢查服務健康狀態 |
| Testing | POST | `/api/nats/test/echo` | 測試回音功能 |
| Testing | POST | `/api/nats/test/timeout` | 測試超時處理 |
| Testing | POST | `/api/nats/test/error` | 測試錯誤處理 |

### Send NATS Request
Sends a message to NATS JetStream and processes it asynchronously with the Enhanced NATS Message Service.

**POST** `/request`

#### Request Body
```json
{
  "subject": "string (required)",
  "payload": "object (required)",
  "correlationId": "string (optional - auto-generated if not provided)"
}
```

#### Success Response
```json
{
  "correlationId": "CORR-e2ae7364-9673-460a-9634-1787f1e57739",
  "subject": "test.k8s",
  "success": false,
  "responsePayload": null,
  "errorMessage": "com.example.natsclient.exception.NatsTimeoutException: No response received within timeout period",
  "timestamp": "2025-08-23T07:04:26.718973818"
}
```

#### Validation Error Response
```json
{
  "fieldErrors": {
    "subject": "Subject is required",
    "payload": "Payload is required"
  },
  "error": "Validation Error", 
  "message": "Invalid request parameters",
  "timestamp": "2025-08-23T07:04:38.207469602"
}
```

#### cURL Example
```bash
curl -X POST http://localhost:8080/api/nats/request \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "test.api",
    "payload": {
      "message": "Hello NATS!",
      "timestamp": "2025-08-23T07:00:00Z"
    },
    "correlationId": "test-123"
  }'
```

### Publish Message
Publishes a fire-and-forget message to NATS JetStream for reliable delivery.

**POST** `/publish`

#### Request Body
```json
{
  "subject": "string (required)",
  "payload": "object (required)"
}
```

#### Success Response
```
Message published successfully
```

#### Validation Error Response
```json
{
  "fieldErrors": {
    "subject": "Subject is required",
    "payload": "Payload is required"
  },
  "error": "Validation Error",
  "message": "Invalid request parameters", 
  "timestamp": "2025-08-23T07:04:43.283483441"
}
```

#### cURL Example
```bash
curl -X POST http://localhost:8080/api/nats/publish \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "performance.test",
    "payload": {
      "data": "Large payload test",
      "timestamp": "2025-08-23T07:04:50Z",
      "size": "large"
    }
  }'
```

### Get Request Status
Retrieves the status of a specific request by correlation ID.

**GET** `/status/{correlationId}`

#### Path Parameters
- `correlationId` (string, required): Correlation ID of the request

#### Success Response (Found)
```json
{
  "requestId": "CORR-e2ae7364-9673-460a-9634-1787f1e57739",
  "subject": "test.k8s",
  "status": "SUCCESS", 
  "payload": "request payload data",
  "response": "response data",
  "timestamp": "2025-08-23T07:04:26.718973818"
}
```

#### Error Response (Not Found)
```json
{
  "requestId": "test-001",
  "subject": null,
  "errorType": "VALIDATION_ERROR", 
  "error": "NATS Client Error",
  "message": "Request not found",
  "timestamp": "2025-08-23T07:04:07.776231383"
}
```

#### cURL Example
```bash
curl -X GET http://localhost:8080/api/nats/status/CORR-e2ae7364-9673-460a-9634-1787f1e57739
```

### Get Statistics  
Retrieves real-time NATS client statistics and performance metrics.

**GET** `/statistics`

#### Response
```json
{
  "totalRequests": 34,
  "pendingRequests": 0,
  "successfulRequests": 29, 
  "failedRequests": 0,
  "timeoutRequests": 5,
  "errorRequests": 0,
  "successRate": 85.29411764705883
}
```

#### cURL Example
```bash
curl -X GET http://localhost:8080/api/nats/statistics
```


## 📊 Monitoring Endpoints

### Health Check (Spring Boot Actuator)
Returns the current health status of the service with Kubernetes probes.

**GET** `/actuator/health`

#### Response
```json
{
  "status": "UP",
  "groups": ["liveness", "readiness"]
}
```

### Liveness Probe
Kubernetes liveness probe endpoint.

**GET** `/actuator/health/liveness`

#### Response
```json
{
  "status": "UP"
}
```

### Readiness Probe  
Kubernetes readiness probe endpoint.

**GET** `/actuator/health/readiness`

#### Response
```json
{
  "status": "UP"
}
```

#### cURL Examples
```bash
# General health check
curl -X GET http://localhost:8080/actuator/health

# Liveness probe
curl -X GET http://localhost:8080/actuator/health/liveness

# Readiness probe  
curl -X GET http://localhost:8080/actuator/health/readiness
```

## ❌ Error Handling

### Common HTTP Status Codes
- `200 OK`: Successful request
- `400 Bad Request`: Validation errors or invalid request format
- `404 Not Found`: Request status not found
- `500 Internal Server Error`: Server-side error

### Common Error Types
- **Validation Errors**: Missing required fields (subject, payload)
- **NATS Timeout**: No response received within timeout period
- **Request Not Found**: Status lookup for non-existent correlation ID

## 🏗️ Current Architecture Features

### Enhanced NATS Message Service
The current implementation uses an **Enhanced NATS Message Service** with the following key features:

#### **Template Method Pattern**
- **NatsRequestProcessor**: Handles request-response operations with JetStream
- **NatsPublishProcessor**: Handles fire-and-forget publishing operations
- **AbstractNatsMessageProcessor**: Base class providing common processing logic

#### **Observer Pattern**  
- **NatsEventPublisher**: Event-driven architecture for message processing events
- **Event Broadcasting**: Publishes events for monitoring and integration

#### **Factory Pattern**
- **MetricsFactory**: Centralized creation of Micrometer metrics
- **NatsMetricsSet**: Grouped metrics for requests, successes, errors, and timers

#### **Hybrid NATS Operations**
- **NATS Core**: Used for request-response operations (optimal performance)
- **JetStream**: Used for publish operations (reliable delivery and persistence)
- **Auto-correlation**: Automatic generation of correlation IDs if not provided

#### **Advanced Monitoring**
- **Real-time Statistics**: Live calculation of success rates, error counts
- **Performance Metrics**: Response times, throughput, concurrent request handling
- **Memory Management**: Built-in stress testing and leak detection
- **Kubernetes Integration**: Health probes and graceful shutdown handling

#### **Database Integration**
- **Oracle Database**: Complete audit trail with request/response logging
- **Request Log Service**: Persistent storage of all NATS operations
- **Statistics Calculation**: Database-driven metrics and reporting

#### **Production Features**
- **Comprehensive Testing**: 100+ unit tests with performance and stress testing
- **Error Handling**: Custom exceptions with detailed context information
- **Validation**: Input validation with detailed error messages
- **Container Ready**: Docker and Kubernetes deployment configurations

### API Usage Patterns

#### Simple Request-Response
```bash
curl -X POST http://localhost:8080/api/nats/request \
  -H "Content-Type: application/json" \
  -d '{"subject":"test.simple","payload":{"message":"Hello"}}'
```

#### Fire-and-Forget Publishing
```bash
curl -X POST http://localhost:8080/api/nats/publish \
  -H "Content-Type: application/json" \
  -d '{"subject":"events.user.created","payload":{"userId":"123"}}'
```

#### Monitoring and Health Checks
```bash
# Check application health
curl http://localhost:8080/actuator/health

# View real-time statistics  
curl http://localhost:8080/api/nats/statistics

# Check specific request status
curl http://localhost:8080/api/nats/status/your-correlation-id
```

This API documentation reflects the current implementation as of **August 2025** with enhanced patterns, comprehensive monitoring, and production-ready features.