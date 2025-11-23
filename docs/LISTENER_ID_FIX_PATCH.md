# Listener ID 修正補丁

## 問題
`NatsListenerServiceImpl.doStartListener()` 中使用了兩個不同的 ID：
- Line 146: `tempListenerId` = "listener-" + 時間戳  
- Line 154: `listenerId` = registry 生成的 UUID

導致 fetcher log 和 registry 使用不同 ID，造成追蹤困難。

## 修正方式

### 檔案: `NatsListenerServiceImpl.java`

找到 `doStartListener` 方法 (約 Line 130-160)，進行以下修改：

#### 步驟 1: 刪除 Line 145-151
```java
// ❌ 刪除這段
// 4. Start message fetching loop in a separate thread
String tempListenerId = "listener-" + System.currentTimeMillis();
Future<?> fetcherFuture = fetcherExecutorService.submit(() -> {
    pullMessageFetcher.startFetchingLoop(
        tempListenerId, subject, idFieldName, subscription, messageHandler, running
    );
});
```

#### 步驟 2: 刪除 Line 153-156
```java
// ❌ 刪除這段
// 5. Register the listener
String listenerId = listenerRegistry.registerListener(
    subject, idFieldName, subscription, messageHandler, fetcherFuture, running
);
```

#### 步驟 3: 在 Line 144 後插入新代碼
```java
// 3. Create running flag (to control the pull loop)
AtomicBoolean running = new AtomicBoolean(true);

// ✅ 新增：4. Generate listener ID FIRST to ensure consistency
String listenerId = listenerRegistry.generateListenerId();

// ✅ 新增：5. Start message fetching loop using the consistent ID
Future<?> fetcherFuture = fetcherExecutorService.submit(() -> {
    pullMessageFetcher.startFetchingLoop(
        listenerId, subject, idFieldName, subscription, messageHandler, running
    );
});

// ✅ 新增：6. Register the listener with the pre-generated ID
listenerRegistry.registerListenerWithId(
    listenerId, subject, idFieldName, subscription, messageHandler, fetcherFuture, running
);
```

## 完整的 doStartListener 方法 (修正後)

```java
/**
 * Core logic for starting a Pull Consumer listener.
 */
private String doStartListener(String subject, String idFieldName,
                              Consumer<ListenerResult.MessageReceived> messageHandler) throws Exception {
    // 1. Create Pull Consumer configuration
    ConsumerConfiguration config = configFactory.createPullConsumerConfig(subject);
    String consumerName = configFactory.generateDurableConsumerName(subject);

    logger.info("Starting Pull Consumer listener for subject '{}' with ID field '{}' and consumer '{}'",
               subject, idFieldName, consumerName);

    // 2. Create Pull subscription
    JetStreamSubscription subscription = createPullSubscription(subject, config);

    // 3. Create running flag (to control the pull loop)
    AtomicBoolean running = new AtomicBoolean(true);

    // 4. Generate listener ID FIRST to ensure consistency across all components
    String listenerId = listenerRegistry.generateListenerId();
    
    // 5. Start message fetching loop in a separate thread using the consistent ID
    Future<?> fetcherFuture = fetcherExecutorService.submit(() -> {
        pullMessageFetcher.startFetchingLoop(
            listenerId, subject, idFieldName, subscription, messageHandler, running
        );
    });

    // 6. Register the listener with the pre-generated ID
    listenerRegistry.registerListenerWithId(
        listenerId, subject, idFieldName, subscription, messageHandler, fetcherFuture, running
    );

    logger.info("Successfully started Pull Consumer listener '{}' for subject '{}'", listenerId, subject);
    return listenerId;
}
```

## 驗證

修改完成後，執行：
```powershell
.\apache-maven-3.9.6\bin\mvn.cmd compile
```

應該編譯成功！
