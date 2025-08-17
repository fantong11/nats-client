# NATS Client Service

A Spring Boot application that provides a robust NATS client service with Oracle database integration for tracking requests and responses.

## Features

- **NATS Integration**: Send JSON requests and handle async responses
- **Oracle Database**: Track all requests/responses with comprehensive logging
- **Exception Handling**: Robust error handling for timeouts, connection issues, and bad requests
- **Retry Mechanism**: Automatic retry for failed requests with configurable parameters
- **Async Response Handling**: Handle delayed responses through separate NATS subjects
- **REST API**: Complete REST endpoints for testing and monitoring
- **Statistics**: Real-time statistics and monitoring capabilities

## Configuration

### Database Configuration
Update `application.yml` with your Oracle database details:
```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@localhost:1521:xe
    username: your_username
    password: your_password
```

### NATS Configuration
Configure NATS connection in `application.yml`:
```yaml
nats:
  url: nats://localhost:4222
  connection:
    timeout: 10000
    reconnect:
      wait: 2000
      max-attempts: 10
  request:
    timeout: 30000
```

## Database Setup

Run the SQL script in `src/main/resources/schema.sql` to create the required table and sequences.

## API Endpoints

### Send Request
```
POST /api/nats/request
Content-Type: application/json

{
  "subject": "your.subject",
  "payload": {
    "key": "value"
  }
}
```

### Publish Message
```
POST /api/nats/publish
Content-Type: application/json

{
  "subject": "your.subject",
  "payload": {
    "key": "value"
  }
}
```

### Get Request Status
```
GET /api/nats/status/{requestId}
GET /api/nats/status/correlation/{correlationId}
```

### Get Requests by Status
```
GET /api/nats/requests/{status}
```
Status values: PENDING, SUCCESS, FAILED, TIMEOUT, ERROR

### Get Statistics
```
GET /api/nats/statistics
```

### Health Check
```
GET /api/nats/health
```

### Test Endpoints
```
POST /api/nats/test/echo
POST /api/nats/test/timeout
POST /api/nats/test/error
```

## Exception Handling

The service handles various error scenarios:

1. **Connection Errors**: NATS server unavailable
2. **Timeouts**: No response within configured timeout
3. **Serialization Errors**: Invalid JSON payload
4. **Validation Errors**: Missing required fields
5. **No Response**: Request sent but no response received
6. **Bad Request**: Invalid request format

## Retry Mechanism

- Automatic retry for failed requests
- Maximum 3 retry attempts
- 5-minute delay between retries
- Configurable retry strategies
- Intelligent error classification (some errors are not retryable)

## Monitoring

- Real-time request statistics
- Request/response tracking in Oracle database
- Comprehensive logging
- Health check endpoints

## Running the Application

1. Ensure Oracle database and NATS server are running
2. Update configuration in `application.yml`
3. Run the database schema script
4. Start the application:
   ```bash
   mvn spring-boot:run
   ```

## Testing

Use the provided test endpoints to verify functionality:

1. **Echo Test**: Tests basic request/response cycle
2. **Timeout Test**: Tests timeout handling
3. **Error Test**: Tests error handling

The service will log all activities and store request/response data in the Oracle database for auditing and monitoring purposes.