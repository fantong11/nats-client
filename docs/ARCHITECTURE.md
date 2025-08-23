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
  - 相關ID生成和管理
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
    public final CompletableFuture<T> processMessage(String subject, Object payload, String correlationId) {
        // 1. 驗證
        // 2. 預處理  
        // 3. 執行具體處理 (抽象方法)
        // 4. 後處理
        // 5. 事件發布
    }
    
    protected abstract CompletableFuture<T> executeSpecificProcessing(...);
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
- 唯一請求ID和相關ID
- 完整的請求生命週期記錄
- 狀態查詢和統計功能

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

## 數據流程

### 1. 請求處理流程
```
客戶端請求 → NatsController → NatsOrchestrationService → 
NatsClientService → EnhancedNatsMessageService → 
NatsRequestProcessor → JetStream → 目標服務
```

### 2. 發布處理流程
```
發布請求 → NatsController → NatsOrchestrationService → 
NatsClientService → EnhancedNatsMessageService → 
NatsPublishProcessor → JetStream → 消息隊列
```

### 3. 事件流程
```
處理器操作 → NatsEventPublisher → 觀察者們 → 
[日誌記錄, 指標收集, 自定義處理]
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