# NATS Client API Documentation

## Overview
The NATS Client provides a comprehensive REST API for publishing messages to NATS JetStream, tracking message status, and managing response listeners. The API supports distributed deployments with automatic failover and recovery.

## Base URL
```
http://localhost:8080/api/nats
```

## Authentication
Currently, no authentication is required for API access.

---

## Endpoints

### 1. Message Publishing

#### POST `/publish`
Publish a message to NATS JetStream with optional response tracking.

**Request Body:**
```json
{
  "subject": "orders.create",
  "payload": {
    "orderId": "ORD-001",
    "customerId": "CUST-123",
    "amount": 100.50
  },
  "responseSubject": "orders.response",
  "responseIdField": "orderId"
}
```

**Parameters:**
- `subject` (required): NATS subject to publish to
- `payload` (required): Message payload as JSON object
- `responseSubject` (optional): Subject to listen for responses
- `responseIdField` (optional): Field name for response correlation, defaults to "correlationId"

**Response (Success - 200):**
```json
{
  "requestId": "req-12345",
  "status": "PUBLISHED",
  "message": "Message published successfully, use trackingUrl to check status",
  "subject": "orders.create",
  "trackingUrl": "/api/nats/status/req-12345",
  "timestamp": "2024-01-01T10:00:00Z"
}
```

**Response (Error - 500):**
```json
{
  "status": "FAILED",
  "message": "Failed to publish message: Connection error",
  "subject": "orders.create",
  "timestamp": "2024-01-01T10:00:00Z"
}
```

---

### 2. Status Tracking

#### GET `/status/{requestId}`
Get the status and details of a specific request.

**Parameters:**
- `requestId` (path): The request ID returned from publish operation

**Response (200):**
```json
{
  "requestId": "req-12345",
  "subject": "orders.create",
  "status": "SUCCESS",
  "requestTimestamp": "2024-01-01T10:00:00Z",
  "responseTimestamp": "2024-01-01T10:00:05Z",
  "requestPayload": "{\"orderId\":\"ORD-001\",\"amount\":100.50}",
  "responsePayload": "{\"orderId\":\"ORD-001\",\"status\":\"confirmed\"}",
  "responseSubject": "orders.response",
  "responseIdField": "orderId",
  "retryCount": 0,
  "errorMessage": null
}
```

**Response (404):**
Request ID not found.

---

### 3. Request Queries

#### GET `/requests/{status}`
Get all requests with a specific status.

**Parameters:**
- `status` (path): Request status - PENDING, SUCCESS, FAILED, TIMEOUT, ERROR

**Response (200):**
```json
[
  {
    "requestId": "req-12345",
    "subject": "orders.create",
    "status": "SUCCESS",
    "requestTimestamp": "2024-01-01T10:00:00Z",
    "responseTimestamp": "2024-01-01T10:00:05Z"
  },
  {
    "requestId": "req-12346",
    "subject": "users.create",
    "status": "SUCCESS",
    "requestTimestamp": "2024-01-01T10:01:00Z",
    "responseTimestamp": "2024-01-01T10:01:03Z"
  }
]
```

---

### 4. Statistics

#### GET `/statistics`
Get comprehensive statistics about NATS operations.

**Response (200):**
```json
{
  "totalRequests": 1000,
  "successfulRequests": 950,
  "failedRequests": 30,
  "timeoutRequests": 15,
  "errorRequests": 5,
  "successRate": 95.0,
  "averageResponseTime": 2.5,
  "lastUpdated": "2024-01-01T10:00:00Z"
}
```

---

### 5. Health Check

#### GET `/health`
Check the health status of the NATS service.

**Response (200):**
```json
{
  "status": "UP",
  "timestamp": "2024-01-01T10:00:00",
  "totalRequests": 1000,
  "successRate": 95.5,
  "natsConnected": true,
  "databaseConnected": true
}
```

---

### 6. Listener Management

#### GET `/listeners/status`
Get status of all active response listeners.

**Response (200):**
```json
[
  {
    "listenerId": "listener-orders-response-123",
    "subject": "orders.response",
    "idField": "orderId",
    "status": "ACTIVE",
    "messagesReceived": 42,
    "startTime": "2024-01-01T10:00:00Z",
    "lastMessageTime": "2024-01-01T10:05:00Z"
  },
  {
    "listenerId": "listener-users-response-456",
    "subject": "users.response",
    "idField": "userId",
    "status": "ACTIVE",
    "messagesReceived": 28,
    "startTime": "2024-01-01T10:01:00Z",
    "lastMessageTime": "2024-01-01T10:04:30Z"
  }
]
```

---

## Request/Response Flow

### 1. Standard Message Publishing
```
Client -> POST /publish -> NATS Publish -> Response
```

### 2. Message Publishing with Response Tracking
```
Client -> POST /publish (with responseSubject) -> 
NATS Publish + Create Listener -> 
Response with requestId ->
Client polls GET /status/{requestId} for updates
```

### 3. Automatic Response Correlation
When `responseSubject` is provided:
1. System automatically creates a response listener
2. Correlates responses using the specified `responseIdField`
3. Updates request status when response is received
4. Listener remains active for future requests with same subject+idField

---

## Status Codes

### Request Status Values
- `PENDING`: Request published, waiting for response
- `SUCCESS`: Response received successfully
- `FAILED`: Request failed during publishing
- `TIMEOUT`: Response not received within timeout period
- `ERROR`: System error occurred

### HTTP Status Codes
- `200`: Operation successful
- `400`: Bad request (invalid parameters)
- `404`: Resource not found
- `500`: Internal server error

---

## Error Handling

All endpoints return structured error responses:

```json
{
  "error": "Error type",
  "message": "Detailed error description",
  "timestamp": "2024-01-01T10:00:00Z",
  "path": "/api/nats/publish"
}
```

Common error scenarios:
- **Connection errors**: NATS server unavailable
- **Validation errors**: Invalid request format
- **Timeout errors**: Response not received within configured timeout
- **Processing errors**: Payload processing failures

---

## Usage Examples

### Basic Message Publishing
```bash
curl -X POST http://localhost:8080/api/nats/publish \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "orders.create",
    "payload": {
      "orderId": "ORD-001",
      "amount": 100.50
    }
  }'
```

### Message Publishing with Response Tracking
```bash
curl -X POST http://localhost:8080/api/nats/publish \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "orders.create",
    "payload": {
      "orderId": "ORD-001",
      "amount": 100.50
    },
    "responseSubject": "orders.response",
    "responseIdField": "orderId"
  }'
```

### Check Request Status
```bash
curl http://localhost:8080/api/nats/status/req-12345
```

### Get All Failed Requests
```bash
curl http://localhost:8080/api/nats/requests/FAILED
```

### View Service Statistics
```bash
curl http://localhost:8080/api/nats/statistics
```