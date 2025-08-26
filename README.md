# NATS Client Service

A production-ready Spring Boot microservice that provides robust NATS messaging capabilities with Oracle database integration, comprehensive error handling, and monitoring features. Built with enterprise-grade patterns including Template Method, Observer, and Factory patterns for enhanced maintainability and scalability.

## 🚀 Features

### Core NATS Integration
- **JetStream-First Architecture**: All messaging operations use JetStream for reliability and durability
- **Template Method Pattern**: Specialized processors for requests (`NatsRequestProcessor`) and publishing (`NatsPublishProcessor`)
- **Async Processing Model**: CompletableFuture-based asynchronous message handling
- **Request ID Tracking**: Simplified single-ID system for end-to-end request tracing

### Advanced Messaging Features  
- **Request-Response Pattern**: JetStream-based communication with 2-hour timeout support
- **Publish-Subscribe Pattern**: Asynchronous message broadcasting with acknowledgments
- **Message Event System**: Observer pattern with `NatsEventPublisher` for event-driven architecture
- **Smart Routing**: Subject-based message routing and processing

### Enterprise Monitoring & Metrics
- **Micrometer Integration**: Request counters, success rates, error tracking, and response time metrics
- **Custom Metrics Factory**: Centralized metrics creation and management
- **Performance Testing**: Built-in load testing and memory stress testing capabilities
- **Real-time Statistics**: Live request/response statistics and success rates

### Database & Persistence
- **Oracle Database Integration**: Complete audit trail with request/response logging
- **Request Log Service**: Comprehensive tracking of all NATS operations with single requestId
- **Database Statistics**: Query-based metrics and reporting
- **Transactional Support**: ACID compliance for all database operations

### Security & Authentication
- **Multiple Auth Methods**: Username/password, token-based, and credential file authentication
- **TLS/SSL Support**: Encrypted connections for production environments
- **Input Validation**: Comprehensive request validation with custom validators
- **Request ID Tracking**: End-to-end request tracing with simplified ID system

### Deployment & Operations
- **Kubernetes Ready**: Complete K8s deployment with ConfigMaps, Secrets, and health probes
- **Docker Support**: Multi-stage builds with optimized container images
- **Health Checks**: Liveness and readiness probes for K8s orchestration
- **Graceful Shutdown**: Proper resource cleanup and connection management

## 🏗️ Architecture

### High-Level Architecture
```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   REST API      │────│ Enhanced NATS    │────│ NATS Server     │
│   Controller    │    │ Message Service  │    │ + JetStream     │
└─────────────────┘    └────────┬─────────┘    └─────────────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │    Template Method   │
                    │      Processors      │
                    │ ┌──────────────────┐ │
                    │ │ NatsRequest      │ │
                    │ │ Processor        │ │
                    │ └──────────────────┘ │
                    │ ┌──────────────────┐ │
                    │ │ NatsPublish      │ │
                    │ │ Processor        │ │
                    │ └──────────────────┘ │
                    └────────┬─────────────┘
                             │
                    ┌────────▼─────────────┐
                    │  Monitoring &        │
                    │  Event System        │
                    │ ┌──────────────────┐ │
                    │ │ Metrics Factory  │ │
                    │ │ Event Publisher  │ │
                    │ │ Request Logger   │ │
                    │ └──────────────────┘ │
                    └────────┬─────────────┘
                             │
                             ▼
                    ┌──────────────────────┐
                    │    Oracle Database   │
                    │  (Audit & Metrics)   │
                    └──────────────────────┘
```

### Component Architecture
```
┌─────────────────────────────────────────┐
│            NATS Client Service          │
├─────────────────────────────────────────┤
│         Enhanced NATS Service           │
│                                         │
│  ┌─────────────────────────────────────┐│
│  │        Template Method              ││
│  │         Processors                  ││
│  │                                     ││
│  │ ┌─────────────┬─────────────────┐   ││
│  │ │ Request     │    Publish      │   ││
│  │ │ Processor   │    Processor    │   ││
│  │ └─────────────┴─────────────────┘   ││
│  └─────────────────────────────────────┘│
├─────────────────────────────────────────┤
│           JetStream Operations          │
│  ┌─────────────────────────────────────┐│
│  │        All NATS Communication       ││
│  │     (Request & Publish via JS)     ││
│  └─────────────────────────────────────┘│
├─────────────────────────────────────────┤
│      Supporting Services                │
│  ┌─────────────┬─────────────────────┐  │
│  │ Payload     │  Request Validator  │  │
│  │ Processor   │                     │  │
│  └─────────────┴─────────────────────┘  │
│  ┌─────────────┬─────────────────────┐  │
│  │ Event       │  Metrics Factory    │  │
│  │ Publisher   │                     │  │
│  └─────────────┴─────────────────────┘  │
└─────────────────────────────────────────┘
```

## 🔧 Configuration

### NATS Configuration
```yaml
nats:
  url: ${NATS_URL:nats://localhost:4222}
  connection:
    timeout: ${NATS_CONNECTION_TIMEOUT:10000}
    reconnect-wait: ${NATS_RECONNECT_WAIT:2000}
  request:
    timeout: ${NATS_REQUEST_TIMEOUT:7200000}  # 2 hours
  jetstream:
    default-timeout: ${NATS_JETSTREAM_TIMEOUT:5000}
    publish-ack:
      timeout: ${NATS_PUBLISH_ACK_TIMEOUT:5000}
    subscribe:
      ack-timeout: ${NATS_SUBSCRIBE_ACK_TIMEOUT:30000}
```

### Database Configuration
```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:oracle:thin:@localhost:1521:xe}
    username: ${DB_USERNAME:nats_user}
    password: ${DB_PASSWORD:oracle123}
    driver-class-name: oracle.jdbc.OracleDriver
  jpa:
    hibernate:
      ddl-auto: ${DDL_AUTO:update}
    show-sql: ${SHOW_SQL:false}
```

## 📊 API Endpoints

### Request Operations
- `POST /api/nats/request` - Send request to NATS server
- `GET /api/nats/status/{requestId}` - Get request status by ID

### Publishing Operations  
- `POST /api/nats/publish` - Publish message to NATS server

### Monitoring & Statistics
- `GET /api/nats/statistics` - Get overall statistics
- `GET /api/nats/requests/{status}` - Get requests by status
- `GET /api/nats/health` - Health check endpoint

## 🚢 Deployment

### Kubernetes Deployment
```bash
# Apply all Kubernetes resources
kubectl apply -f k8s-deploy-all.yml

# Port forward for local access
kubectl port-forward service/nats-client-external 8080:8080
```

### Docker Build
```bash
# Build application
./apache-maven-3.9.6/bin/mvn clean package -DskipTests

# Build and load Docker image
docker build -t nats-client:latest .
minikube image load nats-client:latest
```

## 🧪 Testing

### Unit Tests
```bash
# Run all tests
./apache-maven-3.9.6/bin/mvn test

# Run specific test classes
./apache-maven-3.9.6/bin/mvn test -Dtest="EnhancedNatsMessageServiceTest"
./apache-maven-3.9.6/bin/mvn test -Dtest="NatsPerformanceTest"
```

### Performance Testing
The application includes comprehensive performance tests:
- **Throughput Test**: High-volume request handling
- **Latency Test**: Response time measurement
- **Concurrency Test**: Maximum concurrent request handling
- **Memory Usage Test**: Memory leak detection
- **Load Balancing Test**: Request distribution verification

## 📋 Key Changes from Previous Version

### Architecture Simplification
- **Removed correlationId**: Simplified to use only `requestId` for tracking
- **JetStream Only**: All operations now use JetStream for consistency
- **Enhanced Service Primary**: `EnhancedNatsMessageService` is now the primary implementation

### Request Tracking
- **Single ID System**: Uses only `requestId` (format: `REQ-{UUID}`)
- **Simplified Database Schema**: Removed `CORRELATION_ID` column
- **Cleaner API Responses**: No more duplicate ID fields in responses

### Timeout Configuration
- **Extended Timeout**: Request timeout increased to 2 hours (7,200,000ms)
- **Long-Running Support**: Handles extended processing scenarios

### Testing Coverage
- **All Tests Updated**: Unit tests and performance tests updated for new architecture
- **JetStream Mocking**: Corrected mock signatures for JetStream operations
- **Error Handling**: Comprehensive async error handling tests

## 🔍 Monitoring

### Metrics Available
- `nats.requests.total` - Total number of requests
- `nats.requests.success` - Successful requests counter  
- `nats.requests.error` - Error requests counter
- `nats.requests.duration` - Request processing time

### Health Checks
- `/actuator/health` - Application health status
- `/api/nats/health` - NATS-specific health information

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)  
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.