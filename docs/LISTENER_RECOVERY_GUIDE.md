# Listener Recovery 整合說明

## 已創建的組件

✅ `ListenerConfigStore` - 存儲 listener 配置  
✅ `MessageHandlerRegistry` - 管理 handler 工廠  
✅ `ListenerRecoveryManager` - 自動恢復管理器  

## 整合步驟

### 1. 修改 `NatsListenerServiceImpl.java`

#### 添加依賴注入 (Line 43-58)

```java
private final JetStream jetStream;
private final ConsumerConfigurationFactory configFactory;
private final PullMessageFetcher pullMessageFetcher;
private final ListenerRegistry listenerRegistry;
private final ListenerConfigStore configStore;  // ✅ 新增

// Dedicated thread pool for Pull Consumer
private final ExecutorService fetcherExecutorService;

public NatsListenerServiceImpl(JetStream jetStream,
                              ConsumerConfigurationFactory configFactory,
                              PullMessageFetcher pullMessageFetcher,
                              ListenerRegistry listenerRegistry,
                              ListenerConfigStore configStore) {  // ✅ 新增參數
    this.jetStream = jetStream;
    this.configFactory = configFactory;
    this.pullMessageFetcher = pullMessageFetcher;
    this.listenerRegistry = listenerRegistry;
    this.configStore = configStore;  //  ✅ 新增
    
    // ... rest of constructor
}
```

#### 新增帶 handlerType 的 startListener 方法 (在 Line 84 後新增)

```java
@Override
public CompletableFuture<String> startListener(String subject, String idFieldName, 
                                              String handlerType,
                                              Consumer<ListenerResult.MessageReceived> messageHandler) {
    return CompletableFuture.supplyAsync(() -> {
        validateInputs(subject, idFieldName, messageHandler);
        
        try {
            // Save configuration for recovery
            configStore.saveConfig(subject, idFieldName, handlerType);
            
            return doStartListener(subject, idFieldName, messageHandler);
        } catch (Exception e) {
            logger.error("Failed to start listener for subject '{}'", subject, e);
            throw new ListenerStartupException("Failed to start listener for subject: " + subject, e);
        }
    });
}

// 保留原有的方法 (不帶 handlerType - 不可恢復)
@Override
public CompletableFuture<String> startListener(String subject, String idFieldName,
                                              Consumer<ListenerResult.MessageReceived> messageHandler) {
    logger.warn("Starting listener without handlerType - will not be auto-recovered after redeploy");
    return startListener(subject, idFieldName, "UNKNOWN", messageHandler);
}
```

#### 修改 stopListener - 可選移除配置 (Line 86-98)

```java
@Override
public CompletableFuture<Void> stopListener(String listenerId) {
    return CompletableFuture.runAsync(() -> {
        validateListenerId(listenerId);
        
        try {
            doStopListener(listenerId);
            
            // Optional: remove config if permanently stopping
            // configStore.removeConfig(subject);  // 看需求決定是否移除
        } catch (Exception e) {
            logger.error("Failed to stop listener '{}'", listenerId, e);
            throw new ListenerStopException("Failed to stop listener: " + listenerId, e);
        }
    });
}
```

#### 修改 @PreDestroy - 不要刪除 durable consumer (Line 254-278)

```java
@PreDestroy
public void shutdown() {
    logger.info("Shutting down NatsListenerService - preserving durable consumers for recovery");

    // Stop listener threads but DON'T delete durable consumers
    try {
        // Set all running flags to false
        List<String> listenerIds = listenerRegistry.getAllListenerIds();
        for (String listenerId : listenerIds) {
            try {
                // Just stop the thread, don't unsubscribe from durable consumer
                doStopListenerThreadOnly(listenerId);
            } catch (Exception e) {
                logger.error("Error stopping listener '{}'", listenerId, e);
            }
        }
    } catch (Exception e) {
        logger.error("Error during listener shutdown", e);
    }

    // Shutdown thread pool
    fetcherExecutorService.shutdown();
    try {
        if (!fetcherExecutorService.awaitTermination(10, TimeUnit.SECONDS)) {
            logger.warn("Fetcher thread pool did not terminate in time, forcing shutdown");
            fetcherExecutorService.shutdownNow();
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        fetcherExecutorService.shutdownNow();
    }

    logger.info("NatsListenerService shutdown complete - durable consumers preserved");
}

// 新增方法：只停止線程，不 unsubscribe
private void doStopListenerThreadOnly(String listenerId) {
    ListenerRegistry.ListenerInfo listener = listenerRegistry.unregisterListener(listenerId);
    
    if (listener != null) {
        // Set running flag to false
        listener.running().set(false);
        
        // Cancel fetcher task
        if (listener.fetcherFuture() != null) {
            try {
                listener.fetcherFuture().get(2, TimeUnit.SECONDS);
            } catch (Exception e) {
                listener.fetcherFuture().cancel(true);
            }
        }
        
        // DON'T unsubscribe - let NATS keep the durable consumer
        logger.info("Stopped listener thread '{}' - durable consumer preserved", listenerId);
    }
}
```

### 2. 註冊 Handler (在你的應用中)

在你的業務代碼中註冊 handlers：

```java
@Component
public class MessageHandlerInitializer {
    
    @Autowired
    private MessageHandlerRegistry handlerRegistry;
    
    @Autowired
    private RequestResponseCorrelationService correlationService;
    
    @PostConstruct
    public void registerHandlers() {
        // 註冊 response handler
        handlerRegistry.registerHandler("RESPONSE_HANDLER", () -> {
            return messageResult -> {
                logger.info("Response received - Subject: '{}', Sequence: {}", 
                           messageResult.subject(), messageResult.sequence());
                correlationService.processResponse(messageResult, "id");
            };
        });
        
        // 註冊其他 handler 類型...
    }
}
```

### 3. 使用新的 API 啟動 listener

```java
// 舊 API (不可恢復)
listenerService.startListener(subject, idFieldName, handler);

// 新 API (可恢復)
listenerService.startListener(subject, idFieldName, "RESPONSE_HANDLER", handler);
```

## 工作原理

1. **應用啟動**: `ListenerRecoveryManager` 的 `@PostConstruct` 自動執行
2. **發現 consumers**: 掃描 NATS Server 上的 durable consumers
3. **讀取配置**: 從 `ListenerConfigStore` 獲取 listener 配置
4. **重建 handler**: 從 `MessageHandlerRegistry` 根據 type 創建 handler
5. **重連 listener**: 調用 `startListener` 重新訂閱 durable consumer

## 測試步驟

1. 啟動應用，創建 listener
2. 確認 listener 正在運行
3. 重啟應用 (redeploy)
4. 檢查日誌，應該看到 "Successfully reconnected listener..."
5. 確認 listener 繼續處理訊息

## 注意事項

⚠️ **重要**: 目前 `ListenerConfigStore` 是記憶體存儲，如果要100%可靠，建議：
- 方案 A: 改用數據庫存儲 (但需要額外表)
- 方案 B: 在 NATS consumer 的 description 中存儲 metadata
- 方案 C: 使用配置文件預定義 listeners
