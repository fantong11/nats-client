# API Documentation

Complete API reference for the NATS Client Service, including all endpoints, request/response formats, and examples.

## 📋 Table of Contents

- [Base Information](#base-information)
- [Authentication](#authentication)
- [Core Endpoints](#core-endpoints)
- [Test Endpoints](#test-endpoints)
- [Monitoring Endpoints](#monitoring-endpoints)
- [Error Handling](#error-handling)
- [Request/Response Examples](#requestresponse-examples)
- [Rate Limiting](#rate-limiting)
- [SDK Examples](#sdk-examples)

## 🌐 Base Information

### Base URL
```
http://localhost:8080/api/nats
```

### Content Type
All API requests and responses use `application/json` unless otherwise specified.

### API Version
Current version: `v1.0`

### Response Format
All responses follow a consistent format:
```json
{
  "status": "success|error",
  "data": {},
  "message": "string",
  "timestamp": "2024-01-01T12:00:00Z",
  "requestId": "uuid"
}
```

## 🔐 Authentication

Currently, the service operates without authentication for internal microservice communication. For production deployments, consider implementing:

- API Keys
- JWT tokens
- OAuth 2.0
- mTLS

## 🎯 Core Endpoints

### Send NATS Request
Sends a message to NATS and waits for a response.

**POST** `/request`

#### Request Body
```json
{
  "subject": "string (required)",
  "payload": "object (required)",
  "timeout": "number (optional, milliseconds)",
  "correlationId": "string (optional)",
  "retryAttempts": "number (optional, default: 3)"
}
```

#### Response
```json
{
  "status": "success",
  "data": {
    "requestId": "550e8400-e29b-41d4-a716-446655440000",
    "correlationId": "user-defined-correlation-id",
    "subject": "demo.user.create",
    "response": {
      "userId": "12345",
      "username": "john_doe",
      "status": "created"
    },
    "responseTime": 150,
    "timestamp": "2024-01-01T12:00:00Z"
  }
}
```

#### cURL Example
```bash
curl -X POST http://localhost:8080/api/nats/request \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "demo.user.create",
    "payload": {
      "username": "john_doe",
      "email": "john@example.com",
      "action": "create_user"
    },
    "timeout": 30000
  }'
```

### Publish Message
Publishes a fire-and-forget message to NATS.

**POST** `/publish`

#### Request Body
```json
{
  "subject": "string (required)",
  "payload": "object (required)",
  "correlationId": "string (optional)"
}
```

#### Response
```json
{
  "status": "success",
  "data": {
    "requestId": "550e8400-e29b-41d4-a716-446655440001",
    "subject": "api.notification.send",
    "published": true,
    "timestamp": "2024-01-01T12:00:00Z"
  }
}
```

#### cURL Example
```bash
curl -X POST http://localhost:8080/api/nats/publish \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "api.notification.send",
    "payload": {
      "userId": "12345",
      "message": "Welcome to our service!",
      "type": "welcome"
    }
  }'
```

### Get Request Status
Retrieves the status of a specific request by ID.

**GET** `/status/{requestId}`

#### Path Parameters
- `requestId` (string, required): UUID of the request

#### Response
```json
{
  "status": "success",
  "data": {
    "requestId": "550e8400-e29b-41d4-a716-446655440000",
    "correlationId": "user-correlation-id",
    "subject": "demo.user.create",
    "status": "SUCCESS",
    "payload": {"username": "john_doe"},
    "response": {"userId": "12345", "status": "created"},
    "createdAt": "2024-01-01T12:00:00Z",
    "updatedAt": "2024-01-01T12:00:02Z",
    "responseTime": 2000,
    "retryCount": 0,
    "errorMessage": null
  }
}
```

#### cURL Example
```bash
curl -X GET http://localhost:8080/api/nats/status/550e8400-e29b-41d4-a716-446655440000
```

### Get Request by Correlation ID
Retrieves request information using correlation ID.

**GET** `/status/correlation/{correlationId}`

#### Path Parameters
- `correlationId` (string, required): User-defined correlation ID

#### Response
Same as Get Request Status endpoint.

#### cURL Example
```bash
curl -X GET http://localhost:8080/api/nats/status/correlation/user-correlation-id
```

### Get Requests by Status
Retrieves all requests with a specific status.

**GET** `/requests/{status}`

#### Path Parameters
- `status` (string, required): One of `PENDING`, `SUCCESS`, `FAILED`, `TIMEOUT`, `ERROR`

#### Query Parameters
- `page` (number, optional): Page number (default: 0)
- `size` (number, optional): Page size (default: 20)
- `sort` (string, optional): Sort field (default: createdAt)
- `direction` (string, optional): Sort direction `ASC`|`DESC` (default: DESC)

#### Response
```json
{
  "status": "success",
  "data": {
    "content": [
      {
        "requestId": "550e8400-e29b-41d4-a716-446655440000",
        "correlationId": "user-correlation-id",
        "subject": "demo.user.create",
        "status": "SUCCESS",
        "createdAt": "2024-01-01T12:00:00Z",
        "responseTime": 1500
      }
    ],
    "page": {
      "size": 20,
      "number": 0,
      "totalElements": 100,
      "totalPages": 5
    }
  }
}
```

#### cURL Example
```bash
curl -X GET "http://localhost:8080/api/nats/requests/SUCCESS?page=0&size=10&sort=createdAt&direction=DESC"
```

## 🧪 Test Endpoints

### Echo Test
Tests basic request-response functionality.

**POST** `/test/echo`

#### Request Body
```json
{
  "message": "string (required)",
  "metadata": "string (optional)"
}
```

#### Response
```json
{
  "status": "success",
  "data": {
    "echo": {
      "message": "Hello NATS!",
      "metadata": "test from REST API",
      "timestamp": "2024-01-01T12:00:00Z",
      "requestId": "550e8400-e29b-41d4-a716-446655440000"
    },
    "responseTime": 45
  }
}
```

#### cURL Example
```bash
curl -X POST http://localhost:8080/api/nats/test/echo \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Hello NATS!",
    "metadata": "test from REST API"
  }'
```

### Timeout Test
Tests timeout handling mechanism.

**POST** `/test/timeout`

#### Request Body
```json
{
  "delayMs": "number (optional, default: 35000)"
}
```

#### Response (Timeout)
```json
{
  "status": "error",
  "message": "Request timeout after 30000ms",
  "data": {
    "requestId": "550e8400-e29b-41d4-a716-446655440000",
    "timeoutMs": 30000,
    "elapsedMs": 30001
  },
  "timestamp": "2024-01-01T12:00:30Z"
}
```

#### cURL Example
```bash
curl -X POST http://localhost:8080/api/nats/test/timeout \
  -H "Content-Type: application/json" \
  --max-time 35
```

### Error Test
Tests error handling mechanism.

**POST** `/test/error`

#### Request Body
```json
{
  "errorType": "string (optional, default: 'generic')",
  "errorMessage": "string (optional)"
}
```

#### Response
```json
{
  "status": "error",
  "message": "Simulated error for testing",
  "data": {
    "requestId": "550e8400-e29b-41d4-a716-446655440000",
    "errorType": "generic",
    "errorCode": "TEST_ERROR"
  },
  "timestamp": "2024-01-01T12:00:00Z"
}
```

#### cURL Example
```bash
curl -X POST http://localhost:8080/api/nats/test/error \
  -H "Content-Type: application/json" \
  -d '{
    "errorType": "validation",
    "errorMessage": "Test validation error"
  }'
```

## 📊 Monitoring Endpoints

### Health Check
Returns the current health status of the service.

**GET** `/health`

#### Response
```json
{
  "status": "UP",
  "components": {
    "nats": {
      "status": "UP",
      "details": {
        "connected": true,
        "url": "nats://localhost:4222",
        "servers": 1,
        "reconnects": 0
      }
    },
    "database": {
      "status": "UP",
      "details": {
        "database": "Oracle",
        "validationQuery": "SELECT 1 FROM DUAL",
        "result": 1
      }
    }
  },
  "timestamp": "2024-01-01T12:00:00Z"
}
```

#### cURL Example
```bash
curl -X GET http://localhost:8080/api/nats/health
```

### Statistics
Returns real-time statistics and metrics.

**GET** `/statistics`

#### Query Parameters
- `period` (string, optional): Time period `HOUR`|`DAY`|`WEEK`|`MONTH` (default: DAY)

#### Response
```json
{
  "status": "success",
  "data": {
    "period": "DAY",
    "timestamp": "2024-01-01T12:00:00Z",
    "requests": {
      "total": 1250,
      "success": 1180,
      "failed": 45,
      "timeout": 15,
      "error": 10,
      "successRate": 94.4
    },
    "performance": {
      "averageResponseTime": 150,
      "p95ResponseTime": 500,
      "p99ResponseTime": 1200,
      "throughput": 52.08
    },
    "subjects": [
      {
        "subject": "demo.user.create",
        "count": 450,
        "avgResponseTime": 120,
        "successRate": 98.9
      },
      {
        "subject": "api.notification.send",
        "count": 380,
        "avgResponseTime": 80,
        "successRate": 100.0
      }
    ],
    "errors": [
      {
        "errorType": "TIMEOUT",
        "count": 15,
        "percentage": 1.2
      },
      {
        "errorType": "CONNECTION_ERROR",
        "count": 10,
        "percentage": 0.8
      }
    ]
  }
}
```

#### cURL Example
```bash
curl -X GET "http://localhost:8080/api/nats/statistics?period=HOUR"
```

## ❌ Error Handling

### Error Response Format
```json
{
  "status": "error",
  "error": {
    "code": "ERROR_CODE",
    "message": "Human readable error message",
    "details": "Detailed technical information",
    "timestamp": "2024-01-01T12:00:00Z",
    "requestId": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

### HTTP Status Codes
- `200 OK`: Successful request
- `400 Bad Request`: Invalid request format or parameters
- `408 Request Timeout`: NATS request timeout
- `500 Internal Server Error`: Server-side error
- `503 Service Unavailable`: NATS server unavailable

### Error Codes
- `INVALID_REQUEST`: Request validation failed
- `NATS_CONNECTION_ERROR`: Cannot connect to NATS server
- `NATS_TIMEOUT`: Request timeout exceeded
- `DATABASE_ERROR`: Database operation failed
- `PAYLOAD_PROCESSING_ERROR`: JSON processing error
- `INTERNAL_ERROR`: Unexpected server error

### Validation Errors
```json
{
  "status": "error",
  "error": {
    "code": "INVALID_REQUEST",
    "message": "Validation failed",
    "details": {
      "subject": "Subject is required and cannot be empty",
      "payload": "Payload must be a valid JSON object"
    }
  }
}
```

## 📝 Request/Response Examples

### Complex Request Example
```bash
curl -X POST http://localhost:8080/api/nats/request \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "order.process",
    "payload": {
      "orderId": "ORD-12345",
      "customerId": "CUST-67890",
      "items": [
        {
          "productId": "PROD-001",
          "quantity": 2,
          "price": 29.99
        },
        {
          "productId": "PROD-002",
          "quantity": 1,
          "price": 49.99
        }
      ],
      "shippingAddress": {
        "street": "123 Main St",
        "city": "Anytown",
        "state": "ST",
        "zipCode": "12345"
      },
      "paymentMethod": "credit_card",
      "promotionCode": "SAVE10"
    },
    "timeout": 45000,
    "correlationId": "order-process-12345",
    "retryAttempts": 2
  }'
```

### Batch Operation Example
```bash
# Process multiple requests
for i in {1..5}; do
  curl -X POST http://localhost:8080/api/nats/request \
    -H "Content-Type: application/json" \
    -d "{
      \"subject\": \"batch.process\",
      \"payload\": {
        \"batchId\": \"BATCH-$i\",
        \"data\": \"processing item $i\"
      },
      \"correlationId\": \"batch-$i\"
    }" &
done
wait
```

## 🚦 Rate Limiting

### Current Limits
- **Requests per minute**: 1000
- **Concurrent connections**: 100
- **Request payload size**: 10MB

### Rate Limit Headers
```
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 999
X-RateLimit-Reset: 1704067200
```

### Rate Limit Exceeded Response
```json
{
  "status": "error",
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Rate limit exceeded. Try again later.",
    "retryAfter": 60
  }
}
```

## 💻 SDK Examples

### Java Example
```java
import org.springframework.web.reactive.function.client.WebClient;

public class NatsClientSDK {
    private final WebClient webClient;
    
    public NatsClientSDK(String baseUrl) {
        this.webClient = WebClient.builder()
            .baseUrl(baseUrl + "/api/nats")
            .build();
    }
    
    public Mono<NatsResponse> sendRequest(NatsRequest request) {
        return webClient.post()
            .uri("/request")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(NatsResponse.class);
    }
}
```

### Python Example
```python
import requests
import json

class NatsClient:
    def __init__(self, base_url):
        self.base_url = f"{base_url}/api/nats"
        self.session = requests.Session()
    
    def send_request(self, subject, payload, timeout=30000):
        response = self.session.post(
            f"{self.base_url}/request",
            json={
                "subject": subject,
                "payload": payload,
                "timeout": timeout
            },
            headers={"Content-Type": "application/json"}
        )
        return response.json()

# Usage
client = NatsClient("http://localhost:8080")
result = client.send_request("demo.test", {"message": "Hello from Python"})
print(json.dumps(result, indent=2))
```

### JavaScript Example
```javascript
class NatsClient {
    constructor(baseUrl) {
        this.baseUrl = `${baseUrl}/api/nats`;
    }
    
    async sendRequest(subject, payload, timeout = 30000) {
        const response = await fetch(`${this.baseUrl}/request`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                subject,
                payload,
                timeout
            })
        });
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        return await response.json();
    }
}

// Usage
const client = new NatsClient('http://localhost:8080');
client.sendRequest('demo.test', { message: 'Hello from JavaScript' })
    .then(result => console.log(result))
    .catch(error => console.error('Error:', error));
```

This API documentation provides comprehensive information for integrating with and using the NATS Client Service across different programming languages and use cases.