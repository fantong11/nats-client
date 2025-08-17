# NATS Client Local Testing Guide

## 快速開始

### 1. 啟動環境

```bash
# 啟動 NATS 和 Oracle 服務
docker-compose up -d

# 檢查服務狀態
docker-compose ps

# 或使用提供的批次檔 (Windows)
start-local.bat
```

### 2. 啟動 Spring Boot 應用

```bash
# 使用 local profile
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 或設定環境變數
set SPRING_PROFILES_ACTIVE=local
mvn spring-boot:run
```

### 3. 驗證服務運行

- **NATS 監控**: http://localhost:8222
- **應用健康檢查**: http://localhost:8080/api/nats/health
- **Actuator**: http://localhost:8080/actuator/health

## 測試 API

### 使用 curl

```bash
# 1. Echo 測試
curl -X POST http://localhost:8080/api/nats/test/echo \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello NATS!", "metadata": "test"}'

# 2. 發送自定義請求
curl -X POST http://localhost:8080/api/nats/request \
  -H "Content-Type: application/json" \
  -d '{"subject": "demo.test", "payload": {"data": "test message"}}'

# 3. 發布消息
curl -X POST http://localhost:8080/api/nats/publish \
  -H "Content-Type: application/json" \
  -d '{"subject": "api.notification", "payload": {"message": "Hello World"}}'

# 4. 查看統計
curl http://localhost:8080/api/nats/statistics
```

### 使用 HTTP 檔案

打開 `test-api.http` 並在 IDE 中執行測試請求。

## 測試場景

### 1. 正常請求-回應流程
- 發送 echo 請求
- 驗證收到正確回應
- 檢查請求日誌記錄

### 2. 超時測試
- 發送 timeout 測試
- 觀察超時處理

### 3. 錯誤處理
- 發送 error 測試
- 驗證錯誤回應和日誌

### 4. 發布訂閱
- 發布消息到不同主題
- 觀察訂閱者處理

## 監控和調試

### NATS 監控
訪問 http://localhost:8222 查看：
- 連接狀態
- 消息統計
- 主題訂閱

### 應用日誌
```bash
# 查看 Docker 日誌
docker-compose logs -f nats-client

# 如果直接運行 Spring Boot
# 日誌會顯示在控制台
```

### 資料庫查詢
```sql
-- 查看請求記錄
SELECT * FROM NATS_REQUEST_LOG ORDER BY CREATED_DATE DESC;

-- 查看成功的請求
SELECT * FROM NATS_REQUEST_LOG WHERE STATUS = 'SUCCESS';

-- 查看失敗的請求
SELECT * FROM NATS_REQUEST_LOG WHERE STATUS IN ('ERROR', 'TIMEOUT');
```

## 故障排除

### NATS 連接問題
1. 檢查 Docker 容器狀態: `docker-compose ps`
2. 檢查 NATS 日誌: `docker-compose logs nats`
3. 測試 NATS 連接: `telnet localhost 4222`

### 資料庫連接問題
1. 檢查 Oracle 容器: `docker-compose logs oracle-db`
2. 等待資料庫完全啟動 (可能需要幾分鐘)
3. 檢查連接參數

### 應用啟動問題
1. 確認使用正確的 profile: `local`
2. 檢查端口是否被占用
3. 查看完整的錯誤堆疊

## 清理環境

```bash
# 停止服務
docker-compose down

# 清理資料 (注意：會刪除所有資料)
docker-compose down -v

# 清理 Docker 映像
docker-compose down --rmi all
```