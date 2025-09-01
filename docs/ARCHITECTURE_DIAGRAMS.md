# NATS Client Architecture Diagrams

本文檔包含了 NATS Client 應用程序的詳細架構圖表，使用 Mermaid 格式繪製。

---

## 1. 系統整體架構

```mermaid
graph TB
    subgraph "Client Layer"
        CLIENT[外部客戶端]
    end

    subgraph "Kubernetes Cluster"
        subgraph "Load Balancer"
            LB[NodePort Service<br/>30080]
        end
        
        subgraph "NATS Client Pods"
            POD1[NATS Client Pod 1<br/>nats-client-app]
            POD2[NATS Client Pod 2<br/>nats-client-app]
            POD3[NATS Client Pod 3<br/>nats-client-app]
        end
        
        subgraph "Infrastructure Services"
            NATS[NATS Server<br/>JetStream Enabled]
            DB[Oracle Database]
        end
        
        subgraph "Monitoring"
            METRICS[Metrics Endpoint]
        end
    end

    CLIENT -->|HTTP Requests| LB
    LB --> POD1
    LB --> POD2
    LB --> POD3
    
    POD1 -->|Publish/Subscribe| NATS
    POD2 -->|Publish/Subscribe| NATS
    POD3 -->|Publish/Subscribe| NATS
    
    POD1 -->|Request Tracking| DB
    POD2 -->|Request Tracking| DB
    POD3 -->|Request Tracking| DB
    
    POD1 -->|Distributed Lock| DB
    POD2 -->|Distributed Lock| DB
    POD3 -->|Distributed Lock| DB
    
    NATS -->|Monitoring| METRICS

    style CLIENT fill:#e1f5fe
    style POD1 fill:#f3e5f5
    style POD2 fill:#f3e5f5
    style POD3 fill:#f3e5f5
    style NATS fill:#fff3e0
    style DB fill:#e8f5e8
```

---

## 2. 應用程序內部架構

```mermaid
graph TB
    subgraph "NATS Client Application"
        subgraph "Presentation Layer"
            CTRL[NatsController<br/>REST API Endpoints]
        end
        
        subgraph "Service Layer"
            ORCH[NatsOrchestrationService<br/>Main Coordinator]
            ENHANCED[EnhancedNatsMessageService<br/>Core NATS Operations]
            LISTENER[NatsListenerService<br/>Response Listeners]
            RECOVERY[ListenerRecoveryService<br/>Startup Recovery]
            LOCK[DistributedLockService<br/>Multi-pod Coordination]
        end
        
        subgraph "Strategy & Processing"
            STRATEGY[PayloadIdTrackingStrategy<br/>Correlation Logic]
            PROCESSOR[NatsPublishProcessor<br/>Message Processing]
            VALIDATOR[RequestValidator<br/>Input Validation]
        end
        
        subgraph "Event System"
            PUBLISHER[NatsEventPublisher<br/>Event Distribution]
            LOG_OBS[LoggingEventObserver<br/>Logging Events]
            METRICS_OBS[MetricsEventObserver<br/>Metrics Collection]
        end
        
        subgraph "Data Layer"
            REPO_LOG[NatsRequestLogRepository<br/>Request Data]
            REPO_LOCK[ListenerRecoveryLockRepository<br/>Lock Data]
        end
        
        subgraph "External Systems"
            NATS_EXT[NATS JetStream<br/>Message Broker]
            DB_EXT[Oracle Database<br/>Persistence]
        end
    end

    CTRL --> ORCH
    ORCH --> ENHANCED
    ORCH --> LISTENER
    ENHANCED --> STRATEGY
    ENHANCED --> PROCESSOR
    ENHANCED --> VALIDATOR
    
    RECOVERY --> LOCK
    RECOVERY --> LISTENER
    LOCK --> REPO_LOCK
    
    ENHANCED --> PUBLISHER
    PUBLISHER --> LOG_OBS
    PUBLISHER --> METRICS_OBS
    
    ENHANCED --> REPO_LOG
    LISTENER --> NATS_EXT
    ENHANCED --> NATS_EXT
    
    REPO_LOG --> DB_EXT
    REPO_LOCK --> DB_EXT

    style CTRL fill:#e3f2fd
    style ORCH fill:#f1f8e9
    style ENHANCED fill:#fff3e0
    style LISTENER fill:#fce4ec
    style RECOVERY fill:#e8eaf6
    style LOCK fill:#fff9c4
```

---

## 3. 消息流程架構

```mermaid
sequenceDiagram
    participant C as Client
    participant API as REST API
    participant O as Orchestration Service
    participant S as Strategy
    participant L as Listener Manager
    participant N as NATS JetStream
    participant D as Database
    participant R as Response Listener

    Note over C,R: Message Publishing with Response Tracking

    C->>API: POST /api/nats/publish
    API->>O: publishMessageWithTracking()
    
    O->>S: createTrackingContext()
    S-->>O: context with correlationId
    
    O->>L: ensureListenerActive()
    L->>N: subscribe to response subject
    L-->>O: listener created/reused
    
    O->>N: publish message with correlationId
    N-->>O: publish acknowledgment
    
    O->>D: save request log (PENDING)
    D-->>O: request saved
    
    O-->>API: requestId
    API-->>C: 200 OK with requestId

    Note over N,R: Response Processing (Async)
    
    N->>R: response message received
    R->>S: extractPayloadId()
    S-->>R: correlationId
    
    R->>D: findByRequestId()
    D-->>R: request log
    
    R->>D: update status to SUCCESS
    D-->>R: updated

    Note over C,D: Status Checking
    
    C->>API: GET /status/{requestId}
    API->>O: getRequestStatus()
    O->>D: findByRequestId()
    D-->>O: request status
    O-->>API: status details
    API-->>C: 200 OK with status
```

---

## 4. 分散式協調架構

```mermaid
graph TB
    subgraph "Pod Startup Coordination"
        subgraph "Pod 1 Lifecycle"
            P1_START[Pod 1 Starts]
            P1_LOCK[Acquire Recovery Lock]
            P1_RECOVERY[Perform Recovery]
            P1_RELEASE[Release Lock]
            P1_READY[Pod 1 Ready]
        end
        
        subgraph "Pod 2 Lifecycle"
            P2_START[Pod 2 Starts]
            P2_WAIT[Wait for Lock]
            P2_LOCK[Acquire Recovery Lock]
            P2_RECOVERY[Perform Recovery]
            P2_RELEASE[Release Lock]
            P2_READY[Pod 2 Ready]
        end
        
        subgraph "Pod 3 Lifecycle"
            P3_START[Pod 3 Starts]
            P3_WAIT[Wait for Lock]
            P3_LOCK[Acquire Recovery Lock]
            P3_RECOVERY[Perform Recovery]
            P3_RELEASE[Release Lock]
            P3_READY[Pod 3 Ready]
        end
        
        subgraph "Shared Database"
            LOCK_TABLE[(LISTENER_RECOVERY_LOCK Table)]
            LOG_TABLE[(NATS_REQUEST_LOG Table)]
        end
    end

    P1_START --> P1_LOCK
    P1_LOCK -->|SUCCESS| P1_RECOVERY
    P1_RECOVERY --> P1_RELEASE
    P1_RELEASE --> P1_READY
    
    P2_START --> P2_WAIT
    P1_RELEASE -.->|Lock Available| P2_LOCK
    P2_LOCK -->|SUCCESS| P2_RECOVERY
    P2_RECOVERY --> P2_RELEASE
    P2_RELEASE --> P2_READY
    
    P3_START --> P3_WAIT
    P2_RELEASE -.->|Lock Available| P3_LOCK
    P3_LOCK -->|SUCCESS| P3_RECOVERY
    P3_RECOVERY --> P3_RELEASE
    P3_RELEASE --> P3_READY

    P1_LOCK --> LOCK_TABLE
    P2_LOCK --> LOCK_TABLE
    P3_LOCK --> LOCK_TABLE
    
    P1_RECOVERY --> LOG_TABLE
    P2_RECOVERY --> LOG_TABLE
    P3_RECOVERY --> LOG_TABLE

    style P1_RECOVERY fill:#c8e6c9
    style P2_RECOVERY fill:#c8e6c9
    style P3_RECOVERY fill:#c8e6c9
    style LOCK_TABLE fill:#ffecb3
    style LOG_TABLE fill:#e1f5fe
```

---

## 5. 數據庫設計架構

```mermaid
erDiagram
    NATS_REQUEST_LOG {
        NUMBER ID PK
        VARCHAR2 REQUEST_ID UK "Unique Request ID"
        VARCHAR2 SUBJECT "NATS Subject"
        CLOB REQUEST_PAYLOAD "Request Data"
        CLOB RESPONSE_PAYLOAD "Response Data"
        VARCHAR2 STATUS "PENDING/SUCCESS/FAILED/TIMEOUT"
        VARCHAR2 RESPONSE_SUBJECT "Response Subject"
        VARCHAR2 RESPONSE_ID_FIELD "Correlation Field"
        TIMESTAMP REQUEST_TIMESTAMP "Request Time"
        TIMESTAMP RESPONSE_TIMESTAMP "Response Time"
        CLOB ERROR_MESSAGE "Error Details"
        NUMBER RETRY_COUNT "Retry Attempts"
        NUMBER TIMEOUT_DURATION "Timeout Duration"
        VARCHAR2 CREATED_BY "Creator"
        VARCHAR2 UPDATED_BY "Updater"
        TIMESTAMP CREATED_DATE "Creation Time"
        TIMESTAMP UPDATED_DATE "Update Time"
    }
    
    LISTENER_RECOVERY_LOCK {
        VARCHAR2 LOCK_KEY PK "Lock Identifier"
        VARCHAR2 POD_ID "Pod Identifier"
        TIMESTAMP ACQUIRED_AT "Lock Acquisition Time"
        TIMESTAMP EXPIRES_AT "Lock Expiration Time"
        VARCHAR2 STATUS "ACTIVE/COMPLETED/EXPIRED"
    }
    
    NATS_REQUEST_LOG ||--o{ LISTENER_RECOVERY_LOCK : "coordinated by"
```

---

## 6. 事件驅動架構

```mermaid
graph LR
    subgraph "Event Sources"
        MSG_START[Message Started]
        MSG_COMPLETE[Message Completed]
        MSG_FAILED[Message Failed]
        MSG_RETRY[Message Retry]
    end
    
    subgraph "Event Publisher"
        PUBLISHER[NatsEventPublisher]
    end
    
    subgraph "Event Observers"
        LOG_OBSERVER[LoggingEventObserver]
        METRICS_OBSERVER[MetricsEventObserver]
    end
    
    subgraph "Outputs"
        LOGS[Structured Logs]
        METRICS[Application Metrics]
        ALERTS[Monitoring Alerts]
    end

    MSG_START --> PUBLISHER
    MSG_COMPLETE --> PUBLISHER
    MSG_FAILED --> PUBLISHER
    MSG_RETRY --> PUBLISHER
    
    PUBLISHER -->|Notify| LOG_OBSERVER
    PUBLISHER -->|Notify| METRICS_OBSERVER
    
    LOG_OBSERVER --> LOGS
    METRICS_OBSERVER --> METRICS
    METRICS_OBSERVER --> ALERTS

    style PUBLISHER fill:#e8f5e8
    style LOG_OBSERVER fill:#fff3e0
    style METRICS_OBSERVER fill:#f3e5f5
```
