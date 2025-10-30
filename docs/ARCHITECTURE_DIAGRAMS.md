# NATS Client Architecture Diagrams

This document contains detailed architectural diagrams for the NATS Client application using Mermaid format.

---

## 1. Application Internal Architecture

```mermaid
graph TB
    subgraph "NATS Client Application"
        subgraph "Presentation Layer"
            CTRL[NatsController<br/>REST API Endpoints]
        end

        subgraph "Service Layer"
            ORCH[NatsOrchestrationService<br/>Main Coordinator]
            LISTENER_IMPL[NatsListenerServiceImpl<br/>Listener Management]
            ENHANCED[EnhancedNatsMessageService<br/>Core NATS Operations]
        end

        subgraph "Pull Consumer Components"
            FETCHER[PullMessageFetcher<br/>Active Message Pulling]
            CONFIG_FACTORY[ConsumerConfigurationFactory<br/>Pull Consumer Config]
            MESSAGE_PROC[MessageProcessor<br/>Message Handling]
            REGISTRY[ListenerRegistry<br/>Listener Lifecycle Management]
            THREAD_POOL[ExecutorService<br/>Fetcher Thread Pool]
        end

        subgraph "Strategy & Processing"
            STRATEGY[PayloadIdTrackingStrategy<br/>Correlation Logic]
            VALIDATOR[RequestValidator<br/>Input Validation]
            CORRELATION[RequestResponseCorrelationService<br/>Response Handling]
        end

        subgraph "Event System"
            PUBLISHER[NatsEventPublisher<br/>Event Distribution]
            LOG_OBS[LoggingEventObserver<br/>Logging Events]
            METRICS_OBS[MetricsEventObserver<br/>Metrics Collection]
        end

        subgraph "Data Layer"
            REPO_LOG[NatsRequestLogRepository<br/>Request Data Access]
        end

        subgraph "External Systems"
            NATS_EXT[NATS JetStream<br/>Message Broker]
            DB_EXT[Database<br/>Request Persistence]
        end
    end

    CTRL --> ORCH
    CTRL --> LISTENER_IMPL

    ORCH --> ENHANCED
    ORCH --> CORRELATION

    LISTENER_IMPL --> CONFIG_FACTORY
    LISTENER_IMPL --> FETCHER
    LISTENER_IMPL --> REGISTRY
    LISTENER_IMPL --> THREAD_POOL

    FETCHER --> MESSAGE_PROC
    FETCHER -->|Runs in| THREAD_POOL
    CONFIG_FACTORY -->|Creates Pull Consumer Config| NATS_EXT
    MESSAGE_PROC -->|Process & ACK| NATS_EXT
    REGISTRY -->|Manages Future & AtomicBoolean| LISTENER_IMPL

    ENHANCED --> STRATEGY
    ENHANCED --> VALIDATOR
    ENHANCED --> PUBLISHER

    PUBLISHER --> LOG_OBS
    PUBLISHER --> METRICS_OBS

    ENHANCED --> REPO_LOG
    REPO_LOG --> DB_EXT

    style CTRL fill:#e3f2fd
    style LISTENER_IMPL fill:#e8f5e8
    style FETCHER fill:#f3e5f5
    style CONFIG_FACTORY fill:#fff3e0
    style MESSAGE_PROC fill:#f3e5f5
    style REGISTRY fill:#e1f5fe
    style THREAD_POOL fill:#fce4ec
    style ORCH fill:#f1f8e9
```

---

## 2. Pull Consumer Message Publishing Flow (Publish & Response Tracking)

```mermaid
sequenceDiagram
    participant C as Client
    participant API as REST API
    participant O as Orchestration Service
    participant L as Listener Service
    participant CF as Config Factory
    participant R as Registry
    participant TP as Thread Pool
    participant F as PullMessageFetcher
    participant N as NATS JetStream
    participant DB as Database

    Note over C,DB: Message Publishing with Pull Consumer Response Tracking

    C->>API: POST /api/nats/publish
    API->>O: publishMessageWithTracking()

    O->>L: ensureListenerActive(responseSubject)
    L->>CF: createPullConsumerConfig(responseSubject)
    CF-->>L: pull consumer config

    L->>N: pullSubscribe with consumer config
    Note over L,N: Pull Consumer for active message fetching
    N-->>L: subscription created

    L->>TP: submit fetcher task
    TP->>F: startFetchingLoop()
    Note over F: Fetcher runs in background thread<br/>AtomicBoolean controls loop

    L->>R: registerListener(subscription, future, running)
    Note over R: Stores Future and AtomicBoolean<br/>for lifecycle control
    R-->>L: listenerId
    L-->>O: listener ready

    O->>N: publish original message with correlationId
    N-->>O: message published

    O->>DB: save request log (PENDING)
    DB-->>O: request saved

    O-->>API: requestId
    API-->>C: 200 OK with requestId and trackingUrl

    Note over F,DB: Background Pull Consumer Processing

    loop Active Pulling (while running.get())
        F->>N: subscription.iterate(batchSize, maxWait)
        N-->>F: batch of messages
        F->>F: processMessage() for each
        F->>DB: correlate and update status
        F->>N: ack message
    end

    Note over C,DB: Status Checking

    C->>API: GET /api/nats/status/{requestId}
    API->>O: getRequestStatus()
    O->>DB: query request by ID
    DB-->>O: request status
    O-->>API: status details
    API-->>C: 200 OK with status
```

---

## 3. Pull Consumer Lifecycle Management

```mermaid
sequenceDiagram
    participant Client as Client
    participant API as NatsController
    participant LS as NatsListenerService
    participant R as ListenerRegistry
    participant TP as Thread Pool
    participant F as PullMessageFetcher
    participant N as NATS

    Note over Client,N: Start Listener

    Client->>API: POST /api/nats/listener/start
    API->>LS: startListener(subject, idFieldName, handler)

    LS->>N: pullSubscribe(subject, pullOptions)
    N-->>LS: subscription

    LS->>LS: Create AtomicBoolean(true)
    LS->>TP: submit(() -> fetcher.startFetchingLoop(...))
    TP-->>LS: Future<?>

    LS->>R: registerListener(subscription, future, running)
    R-->>LS: listenerId
    LS-->>API: Success(listenerId)
    API-->>Client: 200 OK

    Note over TP,F: Background Thread Active
    TP->>F: startFetchingLoop()

    loop while running.get()
        F->>N: iterate(batchSize, maxWait)
        N-->>F: messages
        F->>F: process each message
    end

    Note over Client,N: Stop Listener

    Client->>API: POST /api/nats/listener/stop
    API->>LS: stopListener(listenerId)

    LS->>R: unregisterListener(listenerId)
    R-->>LS: ListenerInfo(future, running)

    LS->>LS: running.set(false)
    Note over LS: Signals fetcher to stop

    LS->>LS: future.cancel(true)
    Note over LS: Cancels fetcher thread

    LS->>N: subscription.unsubscribe()
    N-->>LS: unsubscribed

    LS-->>API: Success
    API-->>Client: 200 OK

    Note over F: Fetcher loop exits gracefully
```

---

## 4. Pull Consumer Configuration Architecture

```mermaid
graph LR
    subgraph "Pull Consumer Configuration"
        CF[ConsumerConfigurationFactory]

        subgraph "Configuration Parameters"
            NAME["Consumer Name:<br/>pull-consumer-{subject}"]
            DURABLE["Durable Name:<br/>pull-consumer-{subject}"]
            POLICY[DeliverPolicy:<br/>New]
            ACK[AckPolicy:<br/>Explicit]
            ACK_WAIT[AckWait:<br/>30 seconds]
            MAX_DELIVER[MaxDeliver:<br/>3 attempts]
            MAX_ACK_PENDING[MaxAckPending:<br/>1000 messages]
        end

        subgraph "Pull Fetcher Settings"
            BATCH[Batch Size:<br/>10 messages]
            MAX_WAIT[Max Wait:<br/>1 second]
            POLL[Poll Interval:<br/>100ms]
        end
    end

    CF --> NAME
    CF --> DURABLE
    CF --> POLICY
    CF --> ACK
    CF --> ACK_WAIT
    CF --> MAX_DELIVER
    CF --> MAX_ACK_PENDING

    CF -.->|Used by| BATCH
    CF -.->|Used by| MAX_WAIT
    CF -.->|Used by| POLL

    style CF fill:#fff3e0
    style NAME fill:#e8f5e9
    style ACK fill:#ffebee
    style BATCH fill:#f3e5f5
```

---

## 5. Database Design Architecture

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
```

---

## 6. Pull Consumer Thread Pool Management

```mermaid
graph TB
    subgraph "NatsListenerServiceImpl"
        START[startListener]
        STOP[stopListener]
        SHUTDOWN[PreDestroy shutdown]

        subgraph "Thread Pool"
            EXECUTOR[ExecutorService<br/>Cached Thread Pool]
            TASK1[Fetcher Task 1]
            TASK2[Fetcher Task 2]
            TASK3[Fetcher Task N]
        end
    end

    START -->|submit task| EXECUTOR
    EXECUTOR -->|manages| TASK1
    EXECUTOR -->|manages| TASK2
    EXECUTOR -->|manages| TASK3

    STOP -->|running.set false| TASK1
    STOP -->|future.cancel true| TASK1

    SHUTDOWN -->|shutdownNow| EXECUTOR
    EXECUTOR -->|terminates all| TASK1
    EXECUTOR -->|terminates all| TASK2
    EXECUTOR -->|terminates all| TASK3

    style START fill:#e8f5e9
    style STOP fill:#ffebee
    style SHUTDOWN fill:#fff3e0
    style EXECUTOR fill:#e3f2fd
    style TASK1 fill:#f3e5f5
```

---

## 7. Error Handling and Retry Flow

```mermaid
sequenceDiagram
    participant F as PullMessageFetcher
    participant N as NATS
    participant MP as MessageProcessor
    participant DB as Database

    Note over F,DB: Normal Processing Flow

    F->>N: iterate(batchSize, maxWait)
    N-->>F: messages

    loop For each message
        F->>MP: processMessage()
        MP->>DB: update status
        MP->>N: ack()
    end

    Note over F,DB: Error Handling Flow

    F->>N: iterate(batchSize, maxWait)
    alt Network Error
        N-->>F: Exception
        F->>F: Log error
        F->>F: Sleep(pollInterval)
        F->>N: Retry iterate()
    end

    N-->>F: messages

    loop For each message
        F->>MP: processMessage()
        alt Processing Error
            MP-->>F: RuntimeException
            F->>F: Log error
            F->>F: Continue to next message
            Note over F: Message not ACKed<br/>Will be redelivered
        else Success
            MP->>DB: update status
            MP->>N: ack()
        end
    end

    Note over F,N: Max Deliver Exceeded

    N->>F: Message (attempt 4)
    Note over N: MaxDeliver=3 exceeded<br/>Message moved to dead letter
```

---

## Key Architectural Characteristics

### Pull Consumer Advantages

1. **Active Flow Control**: Client controls message fetching rate
2. **Batch Processing**: Process multiple messages efficiently (10 per batch)
3. **Backpressure Management**: Application can slow down when overloaded
4. **Resource Control**: Thread pool manages concurrent fetcher tasks
5. **Graceful Shutdown**: AtomicBoolean + Future.cancel() ensures clean termination

### Thread Safety

- `ConcurrentHashMap` in ListenerRegistry for thread-safe listener management
- `AtomicBoolean` for safe cross-thread communication
- `Future` for async task lifecycle control
- Immutable `ListenerInfo` record pattern

### SOLID Principles Applied

- **Single Responsibility**: Each component has one clear purpose
  - `PullMessageFetcher`: Message pulling logic
  - `MessageProcessor`: Message processing logic
  - `ListenerRegistry`: Listener lifecycle management
  - `ConsumerConfigurationFactory`: Configuration creation

- **Dependency Inversion**: Dependencies injected via constructor, rely on abstractions

- **Interface Segregation**: Services expose minimal required interfaces
