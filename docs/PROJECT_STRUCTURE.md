# NATS Client Project Structure Documentation

## Overview
This document provides a comprehensive guide to the project structure, explaining the organization of packages, classes, and their responsibilities within the NATS Client application following Clean Code + SOLID principles.

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

## Main Source Code Structure (Clean Architecture)

### Package Organization
```
com.example.natsclient/
├── config/                        # Configuration classes
├── controller/                    # REST API controllers
├── entity/                        # JPA entities
├── exception/                     # Custom exceptions
├── model/                         # Data models and DTOs
├── repository/                    # Data access layer
├── service/                       # Business logic layer (Clean + SOLID)
│   ├── config/                    # Configuration factories (SRP)
│   ├── handler/                   # Message processors (SRP)
│   ├── impl/                      # Service implementations
│   ├── registry/                  # State management (SRP)
│   ├── contract/                  # Service interfaces
│   ├── event/                     # Event models
│   ├── factory/                   # Factory classes
│   ├── listener/                  # Listener management
│   ├── observer/                  # Observer pattern implementations
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
├── NatsConfig.java                # Main NATS configuration
├── NatsProperties.java            # NATS connection properties
├── ObserverConfiguration.java     # Observer pattern setup
└── OpenApiConfig.java             # Swagger/OpenAPI configuration
```

**Key Classes:**
- **NatsConfig**: Primary NATS connection and JetStream configuration
- **NatsProperties**: Connection settings and timeouts

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
└── NatsRequestLog.java            # Main request tracking entity
```

**Entity Details:**
- **NatsRequestLog**: Stores request lifecycle information

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

### 5. Clean Code Service Layer Architecture

#### Configuration Services (`service/config/`) - **SRP Implementation**
**Purpose**: Factory for creating NATS consumer configurations

```
service/config/
└── ConsumerConfigurationFactory.java    # Durable consumer config factory
```

**Responsibilities:**
- Creates durable consumer configurations for load balancing
- Generates unique consumer names per subject
- Configures delivery policies and acknowledgment settings

#### Message Handling (`service/handler/`) - **SRP Implementation**
**Purpose**: Message processing and transformation

```
service/handler/
└── MessageProcessor.java               # Message transformation and ACK
```

**Responsibilities:**
- Processes incoming NATS messages
- Handles message acknowledgment
- Manages error scenarios during processing

#### Service Registry (`service/registry/`) - **SRP Implementation**
**Purpose**: Centralized listener lifecycle management

```
service/registry/
└── ListenerRegistry.java               # Listener state management
```

**Responsibilities:**
- Registers and unregisters listeners
- Tracks listener status and metadata
- Provides thread-safe operations using concurrent collections
- Manages immutable listener records

#### Service Implementations (`service/impl/`)
**Purpose**: Core business logic implementations following DIP

```
service/impl/
├── NatsListenerServiceImpl.java        # Clean listener orchestration
├── EnhancedNatsMessageService.java      # Enhanced NATS operations
├── RequestLogServiceImpl.java          # Request logging service
└── (other existing implementations)
```

**Key Components:**
- **NatsListenerServiceImpl**: Orchestrates dependencies via constructor injection
- **EnhancedNatsMessageService**: Advanced NATS operations with tracking
- **RequestLogServiceImpl**: Database operations for request tracking

#### Service Orchestration
**Purpose**: High-level service coordination

```
service/
├── NatsOrchestrationService.java        # Main orchestration service
├── NatsMessageService.java             # Core NATS message service
├── NatsListenerService.java            # Listener management interface
├── PayloadProcessor.java               # Payload processing service
├── RequestLogService.java              # Request logging interface
└── RequestResponseCorrelationService.java # Response correlation
```

#### Specialized Services (Legacy Components)

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
└── NatsRequestLogRepository.java       # Request log data access
```

**Custom Queries:**
- Status-based request queries
- Cleanup and maintenance queries

### 7. Model Package (`model/`)
**Purpose**: Data transfer objects and domain models

```
model/
├── ListenerResult.java                 # Listener operation results
├── NatsCredentials.java               # NATS authentication model
└── PublishResult.java                 # Publishing operation results
```

### 8. Utility Package (`util/`)
**Purpose**: Common utility classes and helpers

```
util/
├── JsonIdExtractor.java               # JSON ID extraction utilities
├── NatsMessageHeaders.java            # NATS header management
└── NatsMessageUtils.java              # NATS message utilities
```

---

## Configuration Files Structure

### Application Configuration
```
src/main/resources/
├── application.yml                   # Base configuration
└── schema.sql                        # Database schema
```
