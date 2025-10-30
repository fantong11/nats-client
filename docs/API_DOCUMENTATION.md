# NATS Client API Documentation

## Overview
The NATS Client provides a comprehensive REST API for publishing messages to NATS JetStream, tracking message status, and managing response listeners. The API uses **Pull Consumer architecture** for active message fetching, providing better flow control and backpressure management. The system supports distributed deployments with automatic failover and recovery.

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

### 2. Message Publishing with Pull Consumer Response Tracking
```
Client -> POST /publish (with responseSubject) ->
NATS Publish + Create Pull Consumer Listener ->
Response with requestId ->
Client polls GET /status/{requestId} for updates
```

**Pull Consumer Workflow:**
1. API creates Pull Consumer subscription on response subject
2. Spawns background fetcher thread in thread pool
3. Fetcher actively pulls messages in batches (10 messages/batch)
4. Processes and correlates responses using `responseIdField`
5. Updates request status in database
6. Listener continues running for future requests

### 3. Automatic Response Correlation with Pull Consumer
When `responseSubject` is provided:
1. System checks if Pull Consumer listener exists for subject
2. If not, creates new Pull Consumer with:
   - Durable consumer name: `pull-consumer-{subject}`
   - Explicit ACK policy
   - Batch fetching (10 messages per batch, 1s max wait)
   - Background thread in ExecutorService pool
3. Fetcher actively pulls and correlates responses using `responseIdField`
4. Updates request status when matching response is received
5. Listener remains active (AtomicBoolean flag controls lifecycle)
6. Graceful shutdown via `running.set(false)` + `future.cancel()`

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

---

## Pull Consumer Architecture Details

### Overview
The application uses **NATS JetStream Pull Consumer** pattern for active message consumption. This provides better control over message flow compared to Push Consumers.

### Key Characteristics

#### 1. Active Message Fetching
- Application actively pulls messages from NATS server
- Controls fetch rate and batch size
- Better backpressure management

#### 2. Batch Processing
```
Default Configuration:
- Batch Size: 10 messages per fetch
- Max Wait: 1 second per batch
- Poll Interval: 100ms between batches
```

#### 3. Thread Pool Management
```
ExecutorService (Cached Thread Pool):
- One background thread per active listener
- Threads run continuous pull loops
- Controlled by AtomicBoolean flag
- Graceful shutdown on application stop
```

#### 4. Consumer Configuration
Each Pull Consumer is created with:
```yaml
Consumer Name: pull-consumer-{subject}
Durable: true
Deliver Policy: New (only new messages)
ACK Policy: Explicit
ACK Wait: 30 seconds
Max Deliver: 3 attempts
Max ACK Pending: 1000 messages
```

### Pull Consumer Lifecycle

#### Starting a Listener
```
1. Check if listener exists for subject
2. Create Pull Consumer subscription
   - jetStream.subscribe(subject, pullOptions)
3. Create AtomicBoolean(true) flag
4. Submit fetcher task to thread pool
   - executorService.submit(() -> fetcher.startFetchingLoop(...))
5. Register listener with Future and AtomicBoolean
   - listenerRegistry.registerListener(subscription, future, running)
6. Return listener ID
```

#### Active Fetching Loop
```
while (running.get()) {
    1. Pull batch of messages (10 at a time, max wait 1s)
    2. For each message:
       - Process and correlate with request
       - Update database status
       - ACK message
    3. Sleep 100ms between batches
    4. Handle errors gracefully (continue on error)
}
```

#### Stopping a Listener
```
1. Retrieve listener info from registry
2. Set running flag to false
   - running.set(false)
3. Cancel fetcher future
   - future.cancel(true)
4. Unsubscribe from NATS
   - subscription.unsubscribe()
5. Remove from registry
```

### Error Handling

#### Message Processing Errors
- Individual message failures don't stop the fetcher loop
- Failed messages are not ACKed
- NATS will redeliver up to MaxDeliver (3) times
- After max attempts, message moves to dead letter queue

#### Network Errors
- Fetch errors are logged
- Loop sleeps and retries
- No permanent failure unless running flag is set to false

#### Graceful Shutdown
```
@PreDestroy
public void shutdown() {
    1. Stop all active fetcher loops
       - Set all AtomicBoolean flags to false
    2. Shutdown thread pool
       - executorService.shutdownNow()
    3. Unsubscribe all listeners
    4. Clear registry
}
```

### Performance Considerations

#### Advantages of Pull Consumer
1. **Flow Control**: Application controls message fetch rate
2. **Batch Efficiency**: Process 10 messages per batch reduces overhead
3. **Backpressure**: Can slow down when overloaded
4. **Resource Control**: Thread pool limits concurrent fetchers
5. **Scalability**: Multiple instances can share work via durable consumers

#### Configuration Tuning
Adjust these values in `application.yml`:
```yaml
pull-consumer:
  batch-size: 10        # Messages per batch
  max-wait: 1000        # Max wait per batch (ms)
  poll-interval: 100    # Sleep between batches (ms)
  ack-timeout: 30000    # ACK timeout (ms)
  max-deliver: 3        # Max delivery attempts
  max-ack-pending: 1000 # Max unacknowledged messages
```

### Monitoring

#### Listener Status
Use `GET /listeners/status` to monitor:
- Active listeners and their subjects
- Listener start time
- Processing status

#### Health Check
Use `GET /health` to verify:
- NATS connection status
- Database connectivity
- Overall service health

#### Statistics
Use `GET /statistics` to track:
- Total requests processed
- Success/failure rates
- Average response times