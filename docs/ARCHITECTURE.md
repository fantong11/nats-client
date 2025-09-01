# NATS Client Architecture Documentation

## Overview
The NATS Client is a Spring Boot application designed for distributed, high-availability message publishing and response tracking using NATS JetStream. The architecture emphasizes reliability, scalability, and fault tolerance in Kubernetes environments.

---

## Core Architecture

### High-Level Architecture
```
┌─────────────────────────────────────────────────────────────┐
│                    NATS Client Application                   │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────────┐ │
│  │   REST API  │  │   Listener   │  │  Distributed Lock   │ │
│  │ Controller  │  │  Management  │  │     Service         │ │
│  └─────────────┘  └──────────────┘  └─────────────────────┘ │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────────┐ │
│  │Orchestration│  │ Response     │  │  Request Tracking   │ │
│  │  Service    │  │ Correlation  │  │     Service         │ │
│  └─────────────┘  └──────────────┘  └─────────────────────┘ │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────────┐ │
│  │ Enhanced    │  │   Listener   │  │   Event & Metrics   │ │
│  │ NATS Msg    │  │   Service    │  │     Observers       │ │
│  │ Service     │  └──────────────┘  └─────────────────────┘ │
│  └─────────────┘                                             │
├─────────────────────────────────────────────────────────────┤
│           Database (Oracle/MySQL/PostgreSQL)                 │
└─────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────┐
│                    NATS JetStream                           │
│  ┌─────────────┐  ┌──────────────┐  ┌─────────────────────┐ │
│  │  REQUEST    │  │   RESPONSE   │  │      TEST           │ │
│  │   STREAM    │  │    STREAM    │  │     STREAM          │ │
│  └─────────────┘  └──────────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

---

## Key Architectural Patterns

### 1. **Service Orchestration Pattern**
- **NatsOrchestrationService**: Central coordinator for all message operations
- Manages the complete lifecycle: publish → track → correlate → update
- Provides unified interface for complex distributed operations

### 2. **Strategy Pattern for Request Tracking**
- **PayloadIdTrackingStrategy**: Handles correlation ID injection and extraction
- **TrackingStrategyFactory**: Creates appropriate tracking strategies
- Extensible design for different correlation mechanisms

### 3. **Observer Pattern for Event Management**
- **NatsEventPublisher**: Publishes domain events
- **LoggingEventObserver**: Handles logging of message events
- **MetricsEventObserver**: Collects metrics for monitoring
- Decoupled event handling and cross-cutting concerns

### 4. **Factory Pattern for Component Creation**
- **MetricsFactory**: Creates and configures metrics components
- **TrackingStrategyFactory**: Instantiates tracking strategies
- Centralized configuration and dependency injection

### 5. **Template Method Pattern**
- **AbstractNatsMessageProcessor**: Common processing workflow
- **NatsPublishProcessor**: Concrete implementation for publishing
- Consistent processing pipeline with customizable steps

---

## Distributed System Design

### Distributed Locking Mechanism
```
Pod 1              Pod 2              Pod 3
  │                  │                  │
  ├── Acquire Lock ──┼── Wait ──────────┼── Wait
  │   (SUCCESS)      │  (BLOCKED)       │  (BLOCKED)
  │                  │                  │
  ├── Recovery ──────┼──────────────────┼──────────
  │   Process        │                  │
  │                  │                  │
  ├── Release Lock ──┼── Acquire Lock ──┼── Wait
  │                  │   (SUCCESS)      │  (BLOCKED)
  └─────────────────┼── Recovery ──────┼──────────
                    │   Process        │
                    │                  │
                    ├── Release Lock ──┼── Acquire Lock
                    │                  │   (SUCCESS)
                    └──────────────────┼── Recovery
                                       │   Process
                                       └── Release Lock
```

**Key Components:**
- **DistributedLockService**: Database-based distributed locking
- **ListenerRecoveryService**: Handles listener recovery on startup
- **ApplicationRunner**: Coordinates startup sequence across pods

### Response Listener Management
```
┌─────────────────────────────────────────────────────────────┐
│                Response Listener Lifecycle                   │
├─────────────────────────────────────────────────────────────┤
│  Request with responseSubject                               │
│           │                                                 │
│           ▼                                                 │
│  Check existing compatible listener                         │
│           │                                                 │
│           ├── Exists ────────────────┐                     │
│           │                          │                     │
│           ▼                          ▼                     │
│  Create new listener            Reuse existing              │
│           │                          │                     │
│           ▼                          │                     │
│  Subscribe to responseSubject        │                     │
│           │                          │                     │
│           ▼◄─────────────────────────┘                     │
│  Publish original message                                   │
│           │                                                 │
│           ▼                                                 │
│  Wait for response (async)                                  │
│           │                                                 │
│           ▼                                                 │
│  Correlate response → Update request status                 │
└─────────────────────────────────────────────────────────────┘
```

---

## Data Flow Architecture

### 1. **Message Publishing Flow**
```
REST Request → Validation → Strategy Creation → 
Listener Setup → NATS Publish → Response Tracking → 
Database Update → REST Response
```

### 2. **Response Correlation Flow**
```
NATS Response → Payload Extraction → ID Correlation → 
Database Lookup → Status Update → Event Publishing → 
Metrics Update
```

### 3. **Recovery Flow (on Pod Restart)**
```
Application Start → Acquire Recovery Lock → 
Query PENDING Requests → Create Missing Listeners → 
Release Lock → Normal Operation
```

---

## Database Design

### Entity Relationships
```
┌─────────────────────┐         ┌────────────────────────┐
│  NATS_REQUEST_LOG   │         │ LISTENER_RECOVERY_LOCK │
├─────────────────────┤         ├────────────────────────┤
│ ID (PK)             │         │ LOCK_KEY (PK)          │
│ REQUEST_ID (UNIQUE) │         │ POD_ID                 │
│ SUBJECT             │         │ ACQUIRED_AT            │
│ REQUEST_PAYLOAD     │         │ EXPIRES_AT             │
│ RESPONSE_PAYLOAD    │         │ STATUS                 │
│ STATUS              │         └────────────────────────┘
│ RESPONSE_SUBJECT    │                    │
│ RESPONSE_ID_FIELD   │                    │ Used for
│ REQUEST_TIMESTAMP   │                    │ distributed
│ RESPONSE_TIMESTAMP  │                    │ coordination
│ ERROR_MESSAGE       │                    │
│ RETRY_COUNT         │                    │
│ TIMEOUT_DURATION    │                    │
└─────────────────────┘                    │
```

### Database Operations Pattern
- **Transactional**: All critical operations wrapped in database transactions
- **Optimistic Locking**: Uses version control for concurrent updates
- **Audit Trail**: Comprehensive logging of all state changes

---

## Configuration Architecture

### Configuration Hierarchy
```
application.yml (base)
├── application-local.yml (local development)
├── application-k8s.yml (Kubernetes deployment)
└── application-test.yml (testing environment)
```

### Key Configuration Areas
1. **NATS Configuration** (`NatsProperties`)
2. **Database Configuration** (JPA/Hibernate)
3. **Listener Recovery** (`ListenerRecoveryProperties`)
4. **Metrics Configuration** (`MetricsConfiguration`)
5. **OpenAPI Documentation** (`OpenApiConfig`)

---

## Error Handling Strategy

### Exception Hierarchy
```
RuntimeException
├── NatsClientException (base for all NATS-related errors)
│   ├── NatsRequestException (request processing errors)
│   ├── NatsTimeoutException (timeout-related errors)
│   └── PayloadProcessingException (payload handling errors)
└── IllegalStateException (state consistency errors)
```

### Error Recovery Mechanisms
1. **Retry Logic**: Configurable retry attempts with exponential backoff
2. **Circuit Breaker**: Prevents cascading failures
3. **Graceful Degradation**: Service continues with reduced functionality
4. **Distributed Recovery**: Automatic listener recovery across pod restarts

---

## Monitoring and Observability

### Metrics Collection
```
┌────────────────────────────────────────────────────────┐
│                    Metrics Pipeline                    │
├────────────────────────────────────────────────────────┤
│ Business Events → MetricsEventObserver →              │
│                                                        │
│ ┌─────────────────┐  ┌─────────────────┐             │
│ │ Request Metrics │  │ Response Metrics │             │
│ ├─────────────────┤  ├─────────────────┤             │
│ │ • Total Count   │  │ • Success Rate  │             │
│ │ • Success Count │  │ • Avg Response  │             │
│ │ • Failure Count │  │   Time          │             │
│ │ • Timeout Count │  │ • Error Rate    │             │
│ └─────────────────┘  └─────────────────┘             │
│                                                        │
│ ┌─────────────────┐  ┌─────────────────┐             │
│ │Listener Metrics │  │ System Metrics  │             │
│ ├─────────────────┤  ├─────────────────┤             │
│ │ • Active Count  │  │ • DB Connection │             │
│ │ • Message Count │  │ • NATS Status   │             │
│ │ • Recovery Time │  │ • JVM Stats     │             │
│ └─────────────────┘  └─────────────────┘             │
└────────────────────────────────────────────────────────┘
```

### Event Logging Strategy
- **Structured Logging**: JSON format for log aggregation
- **Correlation IDs**: Track requests across service boundaries
- **Performance Logging**: Method execution times and resource usage
- **Business Event Logging**: Domain-specific event tracking

---

## Security Considerations

### Current Security Measures
1. **Input Validation**: Comprehensive request validation
2. **Exception Sanitization**: Secure error message handling
3. **Database Security**: Parameterized queries prevent SQL injection
4. **Network Security**: Internal service communication

### Future Security Enhancements
1. **Authentication**: JWT-based API authentication
2. **Authorization**: Role-based access control
3. **Encryption**: Message payload encryption
4. **Audit Logging**: Enhanced security event logging

---

## Scalability Architecture

### Horizontal Scaling
- **Stateless Design**: No server-side session state
- **Database Coordination**: Shared state through database
- **Distributed Locking**: Prevents race conditions across instances
- **Load Balancer Ready**: Health checks and graceful shutdown

### Vertical Scaling
- **Resource Tuning**: JVM heap and thread pool optimization
- **Connection Pooling**: Database and NATS connection management
- **Async Processing**: Non-blocking operations where possible
- **Memory Management**: Efficient object lifecycle management

---

## Deployment Architecture

### Kubernetes Deployment
```
┌─────────────────────────────────────────────────────────┐
│                    K8s Cluster                          │
├─────────────────────────────────────────────────────────┤
│ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐       │
│ │ nats-client │ │ nats-client │ │ nats-client │       │
│ │   pod-1     │ │   pod-2     │ │   pod-3     │       │
│ └─────────────┘ └─────────────┘ └─────────────┘       │
│        │               │               │               │
│        └───────────────┼───────────────┘               │
│                        │                               │
│ ┌─────────────────────────────────────────────────────┐ │
│ │              Load Balancer Service                  │ │
│ └─────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────┤
│ ┌─────────────┐ ┌─────────────┐ ┌─────────────────────┐ │
│ │ NATS Server │ │ Oracle DB   │ │   Monitoring        │ │
│ │ (JetStream) │ │             │ │   (Prometheus)      │ │
│ └─────────────┘ └─────────────┘ └─────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

### Container Strategy
- **Multi-stage Build**: Optimized Docker images
- **Health Checks**: Kubernetes liveness and readiness probes
- **Resource Limits**: CPU and memory constraints
- **Graceful Shutdown**: Proper signal handling for clean termination

---

## Performance Characteristics

### Throughput Specifications
- **Message Publishing**: 1000+ messages/second per pod
- **Response Correlation**: Sub-second response correlation
- **Database Operations**: Optimized for high concurrent access
- **Memory Usage**: < 1GB per pod under normal load

### Latency Targets
- **API Response Time**: < 100ms for publish operations
- **End-to-End Correlation**: < 5 seconds for response tracking
- **Recovery Time**: < 30 seconds for listener recovery
- **Failover Time**: < 10 seconds for pod replacement