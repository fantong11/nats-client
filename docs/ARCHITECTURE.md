# NATS Client Architecture

## 系統概覽

本NATS客戶端系統採用分層架構設計，整合了多種設計模式，提供高可靠性、可維護性和可擴展性的消息通信解決方案。

## 架構圖

```mermaid
graph TB
    %% 外部接口層
    Client[Client Applications]
    
    %% 控制層
    Controller[NatsController<br/>REST API]
    
    %% 業務編排層
    OrchService[NatsOrchestrationService<br/>Business Logic & Tracking]
    
    %% 核心服務層
    ClientService[NatsClientService<br/>Service Facade]
    EnhancedService[EnhancedNatsMessageService<br/>Primary Implementation]
    
    %% 處理器層 (Template Method Pattern)
    AbstractProcessor[AbstractNatsMessageProcessor<br/>Template Method Base]
    RequestProcessor[NatsRequestProcessor<br/>Request-Response Handler]
    PublishProcessor[NatsPublishProcessor<br/>Publish Handler]
    
    %% 支援服務
    PayloadProcessor[PayloadProcessor<br/>Serialization]
    RequestValidator[RequestValidator<br/>Validation]
    RequestLogService[RequestLogService<br/>Logging]
    RetryService[RetryService<br/>Retry Logic]
    
    %% 觀察者模式組件
    EventPublisher[NatsEventPublisher<br/>Observer Pattern Subject]
    LoggingObserver[LoggingEventObserver<br/>Log Events]
    MetricsObserver[MetricsEventObserver<br/>Metrics Collection]
    
    %% 工廠和建造者
    MetricsFactory[MetricsFactory<br/>Metrics Creation]
    RetryStrategyFactory[RetryStrategyFactory<br/>Strategy Creation]
    PublishOptionsBuilder[NatsPublishOptionsBuilder<br/>Options Builder]
    
    %% 配置層
    NatsConfig[NatsConfig<br/>NATS Connection Setup]
    NatsProperties[NatsProperties<br/>Configuration Properties]
    VaultConfig[VaultConfig<br/>External Config]
    ObserverConfig[ObserverConfiguration<br/>Observer Setup]
    
    %% 基礎設施層
    NatsConnection[NATS Connection]
    JetStream[JetStream Context]
    Database[(Database<br/>H2/PostgreSQL)]
    Vault[HashiCorp Vault<br/>Secrets Management]
    
    %% 數據存取層
    Repository[NatsRequestLogRepository<br/>Data Access]
    Entity[NatsRequestLog<br/>Entity]
    
    %% 異常處理
    GlobalHandler[GlobalExceptionHandler<br/>Error Handling]
    CustomExceptions[Custom Exceptions<br/>NatsClientException, etc.]
    
    %% 連接關係
    Client --> Controller
    Controller --> OrchService
    OrchService --> ClientService
    ClientService --> EnhancedService
    ClientService --> RetryService
    
    EnhancedService --> RequestProcessor
    EnhancedService --> PublishProcessor
    
    RequestProcessor --> AbstractProcessor
    PublishProcessor --> AbstractProcessor
    
    AbstractProcessor --> PayloadProcessor
    AbstractProcessor --> RequestValidator
    AbstractProcessor --> RequestLogService
    AbstractProcessor --> EventPublisher
    
    RequestProcessor --> JetStream
    PublishProcessor --> JetStream
    
    EventPublisher --> LoggingObserver
    EventPublisher --> MetricsObserver
    
    EnhancedService --> MetricsFactory
    EnhancedService --> PublishOptionsBuilder
    RetryService --> RetryStrategyFactory
    
    RequestLogService --> Repository
    Repository --> Entity
    Repository --> Database
    
    NatsConfig --> NatsConnection
    NatsConfig --> JetStream
    NatsConfig --> NatsProperties
    NatsConfig --> VaultConfig
    NatsConfig --> Vault
    
    ObserverConfig --> EventPublisher
    ObserverConfig --> LoggingObserver
    ObserverConfig --> MetricsObserver
    
    Controller --> GlobalHandler
    GlobalHandler --> CustomExceptions
    
    %% 樣式設定
    classDef controller fill:#e1f5fe
    classDef service fill:#f3e5f5
    classDef processor fill:#fff3e0
    classDef observer fill:#e8f5e8
    classDef config fill:#fce4ec
    classDef infrastructure fill:#f1f8e9
    classDef data fill:#fff8e1
    classDef exception fill:#ffebee
    
    class Controller controller
    class OrchService,ClientService,EnhancedService service
    class AbstractProcessor,RequestProcessor,PublishProcessor processor
    class EventPublisher,LoggingObserver,MetricsObserver observer
    class NatsConfig,NatsProperties,VaultConfig,ObserverConfig config
    class NatsConnection,JetStream,Database,Vault infrastructure
    class Repository,Entity data
    class GlobalHandler,CustomExceptions exception
```

## 架構分層

### 1. 控制層 (Controller Layer)
- **NatsController**: 提供REST API接口，處理HTTP請求
- 支援的操作：
  - 發送請求 (`/api/nats/request`)
  - 發布消息 (`/api/nats/publish`)
  - 查詢狀態 (`/api/nats/status`)
  - 獲取統計 (`/api/nats/statistics`)

### 2. 業務編排層 (Orchestration Layer)
- **NatsOrchestrationService**: 業務邏輯編排和請求追蹤
- 功能：
  - 請求驗證和預處理
  - 請求ID生成和管理
  - 統計數據收集
  - 錯誤處理和響應封裝

### 3. 服務層 (Service Layer)
- **NatsClientService**: 服務門面模式，統一對外接口
- **EnhancedNatsMessageService**: 主要實現，使用JetStream進行可靠消息處理
- **RetryService**: 重試機制實現

### 4. 處理器層 (Processor Layer)
採用**Template Method模式**：
- **AbstractNatsMessageProcessor**: 定義消息處理的通用流程
- **NatsRequestProcessor**: 專門處理請求-響應模式
- **NatsPublishProcessor**: 專門處理發布模式

### 5. 支援服務層 (Support Services)
- **PayloadProcessor**: 負責序列化/反序列化
- **RequestValidator**: 請求驗證邏輯
- **RequestLogService**: 請求日誌記錄
- **事件系統**: Observer模式實現的事件通知機制

### 6. 基礎設施層 (Infrastructure Layer)
- **NATS Connection**: 基本NATS連接
- **JetStream**: 提供持久性和可靠性
- **Database**: 請求日誌持久化
- **Vault**: 安全配置管理

## 設計模式應用

### 1. Template Method Pattern
```java
// AbstractNatsMessageProcessor 定義處理流程模板
public abstract class AbstractNatsMessageProcessor<T> {
    public final CompletableFuture<T> processMessage(String subject, Object payload) {
        // 1. 生成requestId
        // 2. 驗證
        // 3. 預處理  
        // 4. 執行具體處理 (抽象方法)
        // 5. 後處理
        // 6. 事件發布
    }
    
    protected abstract CompletableFuture<T> executeSpecificProcessing(String requestId, String subject, Object payload, Instant startTime);
}
```

### 2. Observer Pattern
```java
// 事件發布者
@Component
public class NatsEventPublisher {
    private final List<NatsMessageEventObserver> observers;
    
    public void publishEvent(NatsMessageEvent event) {
        // 通知所有觀察者
    }
}

// 具體觀察者
@Component
public class LoggingEventObserver implements NatsMessageEventObserver {
    public void onEvent(NatsMessageEvent event) {
        // 記錄事件日誌
    }
}
```

### 3. Factory Pattern
```java
@Component
public class MetricsFactory {
    public Counter createCounter(String name, String description) {
        // 創建計數器指標
    }
    
    public Timer createTimer(String name, String description) {
        // 創建計時器指標
    }
}
```

### 4. Builder Pattern
```java
@Component
public class NatsPublishOptionsBuilder {
    public PublishOptions createCritical() {
        // 構建關鍵操作的發布選項
    }
    
    public PublishOptions createStandard() {
        // 構建標準操作的發布選項
    }
}
```

### 5. Strategy Pattern
```java
public interface RetryStrategy {
    long calculateDelay(int attemptNumber);
}

@Component
public class ExponentialBackoffRetryStrategy implements RetryStrategy {
    public long calculateDelay(int attemptNumber) {
        return (long) Math.pow(2, attemptNumber) * baseDelay;
    }
}
```

## 核心功能特性

### 1. JetStream 支援
- 消息持久化存儲
- 自動重試和確認機制
- 消息去重功能
- 流式處理能力

### 2. 異步處理模型
- 基於CompletableFuture的異步操作
- 非阻塞式消息處理
- 事件驅動的通知機制

### 3. 請求追蹤系統
- 唯一請求ID (REQ-{UUID} 格式)
- 完整的請求生命週期記錄
- 狀態查詢和統計功能
- 簡化的單一ID系統，提高追蹤效率

### 4. 監控和指標
- Micrometer集成
- 自定義業務指標
- 系統健康檢查

### 5. 配置管理
- 多環境配置支援
- HashiCorp Vault集成
- Kubernetes配置映射支援

### 6. 容錯機制
- 連接重試和斷線重連
- 消息重試策略
- 全局異常處理
- 慢消費者檢測

## 數據流程序列圖

### 1. 請求處理序列圖

```mermaid
sequenceDiagram
    participant Client as 客戶端
    participant Controller as NatsController
    participant Orchestration as NatsOrchestrationService
    participant ClientService as NatsClientService
    participant Enhanced as EnhancedNatsMessageService
    participant RequestProcessor as NatsRequestProcessor
    participant Validator as RequestValidator
    participant PayloadProcessor as PayloadProcessor
    participant LogService as RequestLogService
    participant EventPublisher as NatsEventPublisher
    participant Observer as LoggingEventObserver
    participant JetStream as JetStream
    participant Database as Database
    
    Client->>Controller: POST /api/nats/request
    Controller->>Controller: 驗證請求參數 (@Valid)
    
    Controller->>Orchestration: sendRequestWithTracking(request)
    Orchestration->>Orchestration: generateRequestId()
    Orchestration->>Orchestration: validateRequest(request)
    
    Orchestration->>ClientService: sendRequest(subject, payload)
    ClientService->>Enhanced: sendRequest(subject, payload)
    
    Enhanced->>RequestProcessor: processMessage(subject, payload)
    
    Note over RequestProcessor: Template Method Pattern 開始
    RequestProcessor->>Validator: validateRequest(subject, payload)
    RequestProcessor->>PayloadProcessor: serialize(payload)
    RequestProcessor->>LogService: createRequestLog(requestId, subject, payload)
    LogService->>Database: save(requestLog)
    
    RequestProcessor->>EventPublisher: publishEvent(MessageStartedEvent)
    EventPublisher->>Observer: onEvent(MessageStartedEvent)
    
    RequestProcessor->>JetStream: publish(subject, headers, payload, options)
    JetStream-->>RequestProcessor: PublishAck
    
    RequestProcessor->>LogService: updateWithSuccess(requestId, response)
    LogService->>Database: update(requestLog)
    
    RequestProcessor->>EventPublisher: publishEvent(MessageCompletedEvent)
    EventPublisher->>Observer: onEvent(MessageCompletedEvent)
    
    RequestProcessor-->>Enhanced: CompletableFuture<String>
    Enhanced-->>ClientService: CompletableFuture<String>
    ClientService-->>Orchestration: CompletableFuture<String>
    
    Orchestration->>Orchestration: 處理響應和錯誤封裝
    Orchestration-->>Controller: CompletableFuture<NatsRequestResponse>
    Controller-->>Client: ResponseEntity<NatsRequestResponse>
```

### 2. 發布處理序列圖

```mermaid
sequenceDiagram
    participant Client as 客戶端
    participant Controller as NatsController
    participant Orchestration as NatsOrchestrationService
    participant ClientService as NatsClientService
    participant Enhanced as EnhancedNatsMessageService
    participant PublishProcessor as NatsPublishProcessor
    participant Validator as RequestValidator
    participant PayloadProcessor as PayloadProcessor
    participant Builder as NatsPublishOptionsBuilder
    participant EventPublisher as NatsEventPublisher
    participant JetStream as JetStream
    
    Client->>Controller: POST /api/nats/publish
    Controller->>Controller: 驗證請求參數
    
    Controller->>Orchestration: publishMessageWithTracking(request)
    Orchestration->>Orchestration: validatePublishRequest(request)
    
    Orchestration->>ClientService: publishMessage(subject, payload)
    ClientService->>Enhanced: publishMessage(subject, payload)
    
    Enhanced->>PublishProcessor: processMessage(subject, payload)
    
    Note over PublishProcessor: Template Method Pattern
    PublishProcessor->>Validator: validateRequest(subject, payload)
    PublishProcessor->>PayloadProcessor: serialize(payload)
    PublishProcessor->>Builder: createStandard()
    
    PublishProcessor->>EventPublisher: publishEvent(MessageStartedEvent)
    
    PublishProcessor->>JetStream: publish(subject, headers, payload, options)
    JetStream-->>PublishProcessor: PublishAck
    
    PublishProcessor->>EventPublisher: publishEvent(MessageCompletedEvent)
    
    PublishProcessor-->>Enhanced: CompletableFuture<Void>
    Enhanced-->>ClientService: CompletableFuture<Void>
    ClientService-->>Orchestration: CompletableFuture<Void>
    
    Orchestration->>Orchestration: 生成requestId
    Orchestration-->>Controller: CompletableFuture<String>
    Controller->>Controller: 封裝響應對象
    Controller-->>Client: ResponseEntity<NatsPublishResponse>
```

### 3. 錯誤處理序列圖

```mermaid
sequenceDiagram
    participant Client as 客戶端
    participant Controller as NatsController
    participant Orchestration as NatsOrchestrationService
    participant Enhanced as EnhancedNatsMessageService
    participant Processor as NatsRequestProcessor
    participant LogService as RequestLogService
    participant EventPublisher as NatsEventPublisher
    participant Observer as MetricsEventObserver
    participant GlobalHandler as GlobalExceptionHandler
    participant JetStream as JetStream
    
    Client->>Controller: POST /api/nats/request
    Controller->>Orchestration: sendRequestWithTracking(request)
    Orchestration->>Enhanced: sendRequest(subject, payload)
    Enhanced->>Processor: processMessage(subject, payload)
    
    Processor->>JetStream: publish(subject, headers, payload, options)
    JetStream-->>Processor: Exception (連接錯誤)
    
    Note over Processor: 錯誤處理開始
    Processor->>LogService: updateWithError(requestId, errorMessage)
    Processor->>EventPublisher: publishEvent(MessageFailedEvent)
    EventPublisher->>Observer: onEvent(MessageFailedEvent)
    Observer->>Observer: 更新失敗指標
    
    Processor-->>Enhanced: CompletableFuture.completeExceptionally(NatsRequestException)
    Enhanced-->>Orchestration: Exception
    
    Orchestration->>Orchestration: exceptionally() 處理異常
    Orchestration-->>Controller: NatsRequestResponse (success=false)
    
    alt 如果是未處理的異常
        Controller-->>GlobalHandler: 異常拋出
        GlobalHandler->>GlobalHandler: 記錄錯誤日誌
        GlobalHandler-->>Client: ErrorResponse
    else 正常錯誤響應
        Controller-->>Client: ResponseEntity<NatsRequestResponse> (500)
    end
```

### 4. 重試機制序列圖

```mermaid
sequenceDiagram
    participant Scheduler as 定時任務
    participant RetryService as RetryServiceImpl
    participant Repository as NatsRequestLogRepository
    participant RetryExecutor as RetryExecutor
    participant StrategyFactory as RetryStrategyFactory
    participant Strategy as ExponentialBackoffRetryStrategy
    participant Enhanced as EnhancedNatsMessageService
    participant EventPublisher as NatsEventPublisher
    participant Database as Database
    
    Scheduler->>RetryService: retryFailedRequests() (定時觸發)
    
    RetryService->>Repository: findByStatusAndCreatedDateBefore(FAILED, cutoffTime)
    Repository-->>RetryService: List<NatsRequestLog>
    
    loop 每個失敗的請求
        RetryService->>RetryExecutor: executeRetry(requestLog)
        
        RetryExecutor->>StrategyFactory: createStrategy(EXPONENTIAL_BACKOFF)
        StrategyFactory-->>RetryExecutor: ExponentialBackoffRetryStrategy
        
        RetryExecutor->>Strategy: canRetry(attemptCount, maxAttempts)
        Strategy-->>RetryExecutor: true/false
        
        alt 可以重試
            RetryExecutor->>Strategy: calculateDelay(attemptCount)
            Strategy-->>RetryExecutor: delayMs
            
            RetryExecutor->>RetryExecutor: Thread.sleep(delayMs)
            
            RetryExecutor->>Enhanced: sendRequest(subject, payload)
            
            alt 重試成功
                Enhanced-->>RetryExecutor: 成功響應
                RetryExecutor->>Repository: updateStatus(requestId, SUCCESS)
                RetryExecutor->>EventPublisher: publishEvent(MessageCompletedEvent)
            else 重試失敗
                Enhanced-->>RetryExecutor: 異常
                RetryExecutor->>Repository: incrementRetryCount(requestId)
                RetryExecutor->>EventPublisher: publishEvent(MessageRetryEvent)
            end
            
        else 超過最大重試次數
            RetryExecutor->>Repository: updateStatus(requestId, MAX_RETRIES_EXCEEDED)
            RetryExecutor->>EventPublisher: publishEvent(MessageFailedEvent)
        end
        
        Repository->>Database: 更新請求狀態
    end
```

### 5. 觀察者模式事件流程圖

```mermaid
sequenceDiagram
    participant Processor as AbstractNatsMessageProcessor
    participant EventPublisher as NatsEventPublisher
    participant LoggingObserver as LoggingEventObserver
    participant MetricsObserver as MetricsEventObserver
    participant CustomObserver as 自定義觀察者
    participant MeterRegistry as MeterRegistry
    participant Logger as Logger
    
    Note over Processor: 消息處理過程中
    
    Processor->>EventPublisher: publishEvent(MessageStartedEvent)
    
    Note over EventPublisher: 異步通知所有觀察者
    par 並行通知觀察者
        EventPublisher->>LoggingObserver: onEvent(MessageStartedEvent)
        LoggingObserver->>Logger: info("Message processing started: {}", event.getRequestId())
    and
        EventPublisher->>MetricsObserver: onEvent(MessageStartedEvent)
        MetricsObserver->>MeterRegistry: counter.increment("nats.message.started")
    and
        EventPublisher->>CustomObserver: onEvent(MessageStartedEvent)
        CustomObserver->>CustomObserver: 自定義處理邏輯
    end
    
    Note over Processor: 處理完成
    
    Processor->>EventPublisher: publishEvent(MessageCompletedEvent)
    
    par 並行通知觀察者
        EventPublisher->>LoggingObserver: onEvent(MessageCompletedEvent)
        LoggingObserver->>Logger: info("Message processing completed: {}", event.getRequestId())
    and
        EventPublisher->>MetricsObserver: onEvent(MessageCompletedEvent)
        MetricsObserver->>MeterRegistry: timer.record(duration)
        MetricsObserver->>MeterRegistry: counter.increment("nats.message.success")
    and
        EventPublisher->>CustomObserver: onEvent(MessageCompletedEvent)
        CustomObserver->>CustomObserver: 自定義完成處理
    end
```

### 6. 系統初始化序列圖

```mermaid
sequenceDiagram
    participant SpringBoot as Spring Boot Application
    participant NatsConfig as NatsConfig
    participant VaultService as K8sCredentialServiceImpl
    participant Vault as HashiCorp Vault
    participant NatsConnection as NATS Connection
    participant JetStreamMgmt as JetStreamManagement
    participant ObserverConfig as ObserverConfiguration
    participant EventPublisher as NatsEventPublisher
    participant Observers as Event Observers
    
    SpringBoot->>NatsConfig: @Bean natsConnection()
    NatsConfig->>VaultService: loadNatsCredentials()
    VaultService->>Vault: 獲取NATS認證信息
    Vault-->>VaultService: NatsCredentials
    VaultService-->>NatsConfig: NatsCredentials
    
    NatsConfig->>NatsConfig: 構建Options (URL, 認證, 超時等)
    NatsConfig->>NatsConnection: Nats.connect(options)
    NatsConnection-->>NatsConfig: Connection
    
    SpringBoot->>NatsConfig: @Bean jetStreamManagement()
    NatsConfig->>NatsConnection: jetStreamManagement()
    NatsConnection-->>NatsConfig: JetStreamManagement
    NatsConfig->>JetStreamMgmt: createDefaultStreamIfNotExists()
    
    SpringBoot->>NatsConfig: @Bean jetStream()
    NatsConfig->>NatsConnection: jetStream(jsOptions)
    NatsConnection-->>NatsConfig: JetStream
    
    SpringBoot->>ObserverConfig: @Bean eventPublisher()
    SpringBoot->>ObserverConfig: @Bean loggingObserver()
    SpringBoot->>ObserverConfig: @Bean metricsObserver()
    
    ObserverConfig->>EventPublisher: registerObserver(loggingObserver)
    ObserverConfig->>EventPublisher: registerObserver(metricsObserver)
    EventPublisher->>Observers: onRegistered()
    
    Note over SpringBoot: 系統初始化完成，準備處理請求
```

## 配置要點

### NATS連接配置
```yaml
nats:
  url: nats://localhost:4222
  connection-name: nats-client
  jetstream:
    enabled: true
    domain: default
    stream:
      default-name: NATS_STREAM
```

### JetStream配置
```yaml
nats:
  jetstream:
    stream:
      subjects: ["requests.*", "events.*"]
      storage: FILE
      max-age: 86400000  # 24小時
      replicas: 1
```

## 擴展指南

### 1. 新增處理器
繼承`AbstractNatsMessageProcessor`並實現`executeSpecificProcessing`方法。

### 2. 新增觀察者
實現`NatsMessageEventObserver`接口並註冊到`NatsEventPublisher`。

### 3. 自定義重試策略
實現`RetryStrategy`接口並註冊到`RetryStrategyFactory`。

### 4. 新增指標
使用`MetricsFactory`創建新的業務指標。

## 安全考慮

- 使用Vault管理敏感配置
- 支援多種NATS認證方式
- 請求驗證和數據清理
- 異常信息過濾，避免敏感信息洩露

## 性能優化

- 連接池管理
- 異步非阻塞處理
- JetStream批處理
- 配置調優建議

這個架構設計旨在提供一個可靠、可維護、可擴展的NATS客戶端解決方案，適用於企業級微服務通信場景。