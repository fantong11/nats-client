# NATS Client Project Structure Documentation

## Overview
This document provides a comprehensive guide to the project structure, explaining the organization of packages, classes, and their responsibilities within the NATS Client application.

---

## Root Project Structure
```
nats-client/
├── docs/                           # Documentation
│   ├── API_DOCUMENTATION.md
│   ├── ARCHITECTURE.md
│   └── PROJECT_STRUCTURE.md
├── src/
│   ├── main/
│   │   ├── java/com/example/natsclient/
│   │   └── resources/
│   └── test/
│       └── java/com/example/natsclient/
├── k8s-deploy-all.yml             # Kubernetes deployment
├── schema.sql                     # Database schema
├── pom.xml                        # Maven configuration
└── README.md                      # Basic project info
```

---

## Main Source Code Structure

### Package Organization
```
com.example.natsclient/
├── config/                        # Configuration classes
├── controller/                    # REST API controllers
├── entity/                        # JPA entities
├── exception/                     # Custom exceptions
├── metrics/                       # Metrics collection
├── model/                         # Data models and DTOs
├── repository/                    # Data access layer
├── service/                       # Business logic layer
│   ├── contract/                  # Service interfaces
│   ├── event/                     # Event models
│   ├── factory/                   # Factory classes
│   ├── impl/                      # Service implementations
│   ├── listener/                  # Listener management
│   ├── observer/                  # Observer pattern implementations
│   ├── startup/                   # Application startup services
│   ├── strategy/                  # Strategy pattern implementations
│   └── validator/                 # Input validation
├── util/                          # Utility classes
└── NatsClientApplication.java     # Main application class
```

---

## Detailed Package Analysis

### 1. Configuration Package (`config/`)
**Purpose**: Application configuration and setup classes

```
config/
├── InfoProperties.java            # Application info properties
├── ListenerRecoveryProperties.java # Listener recovery configuration
├── NatsConfig.java                # Main NATS configuration
├── NatsProperties.java            # NATS connection properties
├── ObserverConfiguration.java     # Observer pattern setup
└── OpenApiConfig.java             # Swagger/OpenAPI configuration
```

**Key Classes:**
- **NatsConfig**: Primary NATS connection and JetStream configuration
- **ListenerRecoveryProperties**: Distributed recovery settings

### 2. Controller Package (`controller/`)
**Purpose**: REST API endpoint definitions

```
controller/
└── NatsController.java            # Main REST API controller
```

**Endpoints Provided:**
- `POST /api/nats/publish` - Message publishing
- `GET /api/nats/status/{requestId}` - Request status tracking
- `GET /api/nats/requests/{status}` - Query requests by status
- `GET /api/nats/statistics` - Service statistics
- `GET /api/nats/health` - Health check
- `GET /api/nats/listeners/status` - Listener status

### 3. Entity Package (`entity/`)
**Purpose**: JPA entities for database persistence

```
entity/
├── NatsRequestLog.java            # Main request tracking entity
└── ListenerRecoveryLock.java      # Distributed locking entity
```

**Entity Details:**
- **NatsRequestLog**: Stores request lifecycle information
- **ListenerRecoveryLock**: Manages distributed coordination

### 4. Exception Package (`exception/`)
**Purpose**: Custom exception hierarchy and error handling

```
exception/
├── GlobalExceptionHandler.java    # Global exception handler
├── NatsClientException.java       # Base exception class
├── NatsRequestException.java      # Request-specific exceptions
├── NatsTimeoutException.java      # Timeout-related exceptions
└── PayloadProcessingException.java # Payload handling exceptions
```

### 5. Service Layer Architecture

#### Service Contracts (`service/contract/`)
**Purpose**: Define service interfaces and contracts

```
service/contract/
├── RequestTrackingContext.java    # Context for tracking operations
├── RequestTrackingStrategy.java   # Strategy interface
└── ResponseListenerManager.java   # Listener management interface
```

#### Service Implementations (`service/impl/`)
**Purpose**: Core business logic implementations

```
service/impl/
├── AbstractNatsMessageProcessor.java    # Base message processor
├── EnhancedNatsMessageService.java      # Enhanced NATS operations
├── NatsListenerServiceImpl.java        # Listener service implementation
├── NatsPublishProcessor.java           # Message publishing logic
└── RequestLogServiceImpl.java          # Request logging service
```

**Key Components:**
- **EnhancedNatsMessageService**: Advanced NATS operations with tracking
- **AbstractNatsMessageProcessor**: Template for message processing
- **RequestLogServiceImpl**: Database operations for request tracking

#### Service Orchestration
**Purpose**: High-level service coordination

```
service/
├── NatsOrchestrationService.java        # Main orchestration service
├── NatsMessageService.java             # Core NATS message service
├── NatsListenerService.java            # Listener management service
├── PayloadProcessor.java               # Payload processing service
├── RequestLogService.java              # Request logging service
└── RequestResponseCorrelationService.java # Response correlation
```

#### Specialized Services

**Event Management (`service/event/`)**
```
service/event/
├── NatsMessageEvent.java               # Base event class
└── impl/
    ├── MessageCompletedEvent.java       # Message completion event
    ├── MessageFailedEvent.java          # Message failure event
    ├── MessageRetryEvent.java           # Retry attempt event
    └── MessageStartedEvent.java         # Message initiation event
```

**Factory Pattern (`service/factory/`)**
```
service/factory/
├── MetricsFactory.java                  # Metrics component factory
└── TrackingStrategyFactory.java         # Strategy creation factory
```

**Observer Pattern (`service/observer/`)**
```
service/observer/
├── NatsEventPublisher.java             # Event publishing
├── NatsMessageEventObserver.java       # Observer interface
└── impl/
    ├── LoggingEventObserver.java        # Logging observer
    └── MetricsEventObserver.java        # Metrics collection observer
```

**Startup Services (`service/startup/`)**
```
service/startup/
├── DistributedLockService.java         # Distributed coordination
└── ListenerRecoveryService.java        # Startup recovery logic
```

**Strategy Pattern (`service/strategy/`)**
```
service/strategy/
└── PayloadIdTrackingStrategy.java      # Payload ID tracking strategy
```

**Validation (`service/validator/`)**
```
service/validator/
└── RequestValidator.java               # Request validation logic
```

### 6. Repository Package (`repository/`)
**Purpose**: Data access layer with JPA repositories

```
repository/
├── NatsRequestLogRepository.java       # Request log data access
└── ListenerRecoveryLockRepository.java # Lock data access
```

**Custom Queries:**
- Status-based request queries
- Distributed lock operations
- Cleanup and maintenance queries

### 7. Model Package (`model/`)
**Purpose**: Data transfer objects and domain models

```
model/
├── ListenerResult.java                 # Listener operation results
├── NatsCredentials.java               # NATS authentication model
└── PublishResult.java                 # Publishing operation results
```

### 8. Metrics Package (`metrics/`)
**Purpose**: Application metrics and monitoring

```
metrics/
├── NatsMetricsCollector.java          # Metrics collection logic
└── NatsMetricsConfiguration.java      # Metrics configuration
```

### 9. Utility Package (`util/`)
**Purpose**: Common utility classes and helpers

```
util/
├── JsonIdExtractor.java               # JSON ID extraction utilities
├── NatsMessageHeaders.java            # NATS header management
└── NatsMessageUtils.java              # NATS message utilities
```

---

## Test Structure

### Test Package Organization
```
src/test/java/com/example/natsclient/
├── config/
│   ├── ConfigurationPropertiesTest.java
│   └── TestNatsConfig.java
├── entity/
│   └── NatsRequestLogTest.java
├── model/
│   └── NatsCredentialsTest.java
├── service/
│   ├── factory/
│   │   └── MetricsFactoryTestSimple.java
│   ├── impl/
│   │   └── RequestLogServiceImplTest.java
│   ├── startup/
│   │   ├── DistributedLockServiceTest.java
│   │   └── ListenerRecoveryServiceTest.java
│   ├── validator/
│   │   └── RequestValidatorTest.java
│   └── NatsOrchestrationServiceTest.java
└── util/
    └── NatsMessageHeadersTest.java
```

**Test Categories:**
- **Unit Tests**: Individual component testing
- **Integration Tests**: Component interaction testing
- **Configuration Tests**: Spring configuration validation
- **Repository Tests**: Database operation testing

---

## Key Design Patterns Used

### 1. **Dependency Injection Pattern**
- Spring Framework's IoC container
- Constructor-based injection preferred
- Interface-based programming

### 2. **Repository Pattern**
- Clean separation of data access logic
- JPA/Hibernate implementation
- Custom query methods

### 3. **Strategy Pattern**
- `RequestTrackingStrategy` for different tracking approaches
- `PayloadIdTrackingStrategy` for correlation logic
- Pluggable algorithms

### 4. **Observer Pattern**
- Event-driven architecture
- `NatsEventPublisher` and observers
- Cross-cutting concerns handling

### 5. **Template Method Pattern**
- `AbstractNatsMessageProcessor` base class
- Common processing workflow
- Customizable processing steps

### 6. **Factory Pattern**
- `TrackingStrategyFactory` for strategy creation
- `MetricsFactory` for metrics components
- Centralized object creation

---

## Configuration Files Structure

### Application Configuration
```
src/main/resources/
├── application.yml                   # Configuration
└── schema.sql                        # Database schema
```

### Configuration Hierarchy
1. **application.yml**: Base configuration
2. **Profile-specific**: Environment overrides
3. **Environment variables**: Runtime configuration
4. **Command-line args**: Deployment-time overrides

---

## Build Configuration

### Maven Structure
```
pom.xml
├── Properties
│   ├── Java version (17)
│   ├── Spring Boot version (3.5.0)
│   └── Dependency versions
├── Dependencies
│   ├── Spring Boot starters
│   ├── NATS Java client
│   ├── Database drivers
│   ├── Testing framework
│   └── Documentation tools
├── Build Plugins
│   ├── Spring Boot Maven Plugin
│   ├── Surefire (testing)
│   └── Failsafe (integration tests)
└── Profiles
    ├── Local development
    ├── Testing
    └── Production
```