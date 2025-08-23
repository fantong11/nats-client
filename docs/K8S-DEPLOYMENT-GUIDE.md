# Kubernetes 部署實戰指南 (Enhanced NATS 版本)

本指南記錄了 NATS Client Service 在 Kubernetes 環境中的實際部署、測試流程和最佳實踐。此版本使用 Enhanced NATS Message Service 和 Template Method 設計模式，提供企業級的 JetStream 支援、完整的監控指標和可靠的訊息處理。

## 📋 目錄

- [環境準備](#環境準備)
- [構建 Docker 鏡像](#構建-docker-鏡像)
- [部署到 Kubernetes](#部署到-kubernetes)
- [服務測試](#服務測試)
- [JetStream 功能測試](#jetstream-功能測試)
- [故障排除](#故障排除)
- [清理環境](#清理環境)

## 🔧 環境準備

### 前置要求
- Docker Desktop (with Kubernetes enabled) 或 Minikube
- kubectl 已配置並能連接到集群
- 足夠的系統資源 (至少 8GB RAM 推薦)

### 驗證環境
```bash
# 檢查 Docker
docker --version
docker images

# 檢查 Kubernetes
kubectl cluster-info
kubectl get nodes

# 檢查可用資源
kubectl top nodes  # 如果有 metrics-server
```

### 啟動 Minikube (如果使用)
```bash
# 啟動 Minikube with Docker driver
minikube start --driver=docker

# 驗證狀態
minikube status

# 獲取 Minikube IP (稍後測試需要)
minikube ip
# 輸出範例: 192.168.49.2
```

## 🏗️ 構建 Docker 鏡像

### 1. 編譯應用程式
```bash
# 進入專案目錄
cd /path/to/nats-client

# 清理並編譯 (跳過測試以節省時間)
./apache-maven-3.9.6/bin/mvn clean compile -DskipTests
```

### 2. 構建 Docker 鏡像
```bash
# 構建鏡像
docker build -t nats-client:latest .

# 驗證鏡像
docker images | grep nats-client
```

### 3. 加載鏡像到 Minikube (如果使用 Minikube)
```bash
# 將鏡像加載到 Minikube
minikube image load nats-client:latest

# 驗證鏡像已加載
minikube image ls | grep nats-client
```

## ☸️ 部署到 Kubernetes

### 1. 部署所有服務
```bash
# 使用現有的 k8s-deploy-all.yml 部署
kubectl apply -f k8s-deploy-all.yml
```

### 2. 檢查部署狀態
```bash
# 檢查所有 Pod 狀態
kubectl get pods

# 實時監控 Pod 狀態變化
kubectl get pods -w

# 檢查服務狀態
kubectl get services

# 檢查部署詳情
kubectl get deployments -o wide
```

### 3. 期望的部署結果
成功部署後應該看到：
```bash
NAME                               READY   STATUS    RESTARTS   AGE
nats-client-app-5f8678c8cd-7r4nr   1/1     Running   0          2m
nats-server-648688c789-w2p9v       1/1     Running   0          2m
oracle-db-9644944c6-hbrjx          1/1     Running   0          2m
```

### 4. 檢查應用程式日誌
```bash
# 檢查 nats-client-app 日誌
kubectl logs -f deployment/nats-client-app

# 尋找以下成功訊息:
# "Connected to NATS server: nats://nats:4222"
# "Started NatsClientApplication in X.X seconds"
# "NATS test subscribers started"
```

## 🧪 服務測試

### 1. 內部測試 (推薦)
由於網路配置可能複雜，建議先從集群內部測試：

#### 測試 Publish API (Enhanced Service)
```bash
# 進入應用容器測試 publish
kubectl exec deployment/nats-client-app -- \
curl -X POST http://localhost:8080/api/nats/publish \
-H "Content-Type: application/json" \
-d '{
  "subject": "test.publish",
  "payload": {
    "message": "Hello from K8s Enhanced NATS",
    "timestamp": "'$(date -Iseconds)'",
    "processingMode": "JetStream"
  }
}'

# 期望回應 (Enhanced Service 使用 JetStream):
# Message published successfully
```

#### 測試 Request API (Enhanced Service with Template Method)
```bash
# 測試 Enhanced NATS 請求 (通過 NatsRequestProcessor)
kubectl exec deployment/nats-client-app -- \
curl -X POST http://localhost:8080/api/nats/request \
-H "Content-Type: application/json" \
-d '{
  "subject": "test.k8s",
  "payload": {
    "message": "Hello Enhanced NATS from K8s",
    "requestId": "k8s-test-001",
    "processingMode": "Template Method Pattern"
  },
  "correlationId": "K8S-TEST-001"
}'

# 期望回應 (可能會超時因為沒有訂閱者):
# {
#   "correlationId": "K8S-TEST-001",
#   "subject": "test.k8s",
#   "success": false,
#   "responsePayload": null,
#   "errorMessage": "com.example.natsclient.exception.NatsTimeoutException: No response received within timeout period",
#   "timestamp": "2025-08-23T07:04:26.718973818"
# }
```

#### 測試無回應情境
```bash
# 測試沒有監聽者的 subject
kubectl exec deployment/nats-client-app -- \
curl -X POST http://localhost:8080/api/nats/request \
-H "Content-Type: application/json" \
-d '{
  "subject": "test.nobody.listens",
  "payload": {"message": "This will timeout"}
}'

# 期望超時回應:
# {
#   "correlationId": "CORR-xxx",
#   "subject": "test.nobody.listens", 
#   "success": false,
#   "responsePayload": null,
#   "errorMessage": "...NatsTimeoutException: No response received within timeout period",
#   "timestamp": "..."
# }
```

### 2. 外部測試 (如果需要)

#### 使用 NodePort 測試 (Minikube)
```bash
# 獲取 Minikube IP
MINIKUBE_IP=$(minikube ip)
echo "Minikube IP: $MINIKUBE_IP"

# 測試外部連接 (使用 NodePort 30080)
curl -X POST http://$MINIKUBE_IP:30080/api/nats/publish \
-H "Content-Type: application/json" \
-d '{"subject": "external.test", "payload": {"source": "external"}}'
```

#### 使用 Port Forward 測試
```bash
# 建立端口轉發
kubectl port-forward service/nats-client-external 8080:8080 &

# 在另一個終端測試
curl -X POST http://localhost:8080/api/nats/request \
-H "Content-Type: application/json" \
-d '{"subject": "test.echo", "payload": {"message": "Port forward test"}}'

# 結束端口轉發
kill %1
```

### 3. 健康檢查測試 (Spring Boot Actuator)
```bash
# 檢查應用健康狀態 (主要健康端點)
kubectl exec deployment/nats-client-app -- \
curl http://localhost:8080/actuator/health

# 檢查 Kubernetes liveness 探測
kubectl exec deployment/nats-client-app -- \
curl http://localhost:8080/actuator/health/liveness

# 檢查 Kubernetes readiness 探測
kubectl exec deployment/nats-client-app -- \
curl http://localhost:8080/actuator/health/readiness

# 期望回應:
# {"status":"UP","groups":["liveness","readiness"]}
```

### 4. 效能和監控測試 (Enhanced Metrics)
```bash
# 檢查資源使用情況
kubectl top pods

# 查看 Enhanced NATS 統計資訊 (Micrometer 指標)
kubectl exec deployment/nats-client-app -- \
curl http://localhost:8080/api/nats/statistics

# 期望回應:
# {
#   "totalRequests": 34,
#   "pendingRequests": 0,
#   "successfulRequests": 29,
#   "failedRequests": 0,
#   "timeoutRequests": 5,
#   "errorRequests": 0,
#   "successRate": 85.29411764705883
# }

# 檢查特定請求狀態
kubectl exec deployment/nats-client-app -- \
curl http://localhost:8080/api/nats/status/K8S-TEST-001
```

## 🚀 Enhanced NATS Architecture 功能測試

### 1. Template Method Pattern 測試
Enhanced NATS Message Service 使用 Template Method 模式，自動選擇適當的處理器：

```bash
# 測試發布操作 (使用 NatsPublishProcessor + JetStream)
kubectl exec deployment/nats-client-app -- \
curl -X POST http://localhost:8080/api/nats/publish \
-H "Content-Type: application/json" \
-d '{
  "subject": "enhanced.jetstream.publish",
  "payload": {
    "message": "Enhanced NATS with JetStream from K8s",
    "timestamp": "'$(date -Iseconds)'",
    "architecture": "Template Method + JetStream"
  }
}'

# Enhanced Service 自動使用 JetStream 進行可靠發布
# 期望回應: "Message published successfully"
```

### 2. 雙重架構測試 (Hybrid Operations)
Enhanced Service 使用智能路由：NATS Core 用於請求-回應，JetStream 用於發布操作

```bash
# 請求操作 (使用 NatsRequestProcessor + JetStream)
kubectl exec deployment/nats-client-app -- \
curl -X POST http://localhost:8080/api/nats/request \
-H "Content-Type: application/json" \
-d '{
  "subject": "enhanced.hybrid.request",
  "payload": {
    "message": "Hybrid Architecture Test",
    "requestType": "JetStream Request"
  },
  "correlationId": "HYBRID-K8S-001"
}'

# Enhanced Service 通過 Template Method 自動處理
```

### 3. 監控指標驗證 (Micrometer Integration)
```bash
# 執行幾個測試請求來生成指標數據
for i in {1..5}; do
  kubectl exec deployment/nats-client-app -- \
  curl -s -X POST http://localhost:8080/api/nats/publish \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "metrics.test.'${i}'",
    "payload": {"test": "metrics generation", "iteration": '${i}'}
  }' > /dev/null
done

# 查看累積的指標數據
kubectl exec deployment/nats-client-app -- \
curl -s http://localhost:8080/api/nats/statistics

# 應該顯示增加的請求計數和成功率更新
```

### 4. 檢查 JetStream 狀態
```bash
# 檢查 NATS Server JetStream 狀態
kubectl exec deployment/nats-server -- \
nats stream list

# 檢查特定 stream 資訊
kubectl exec deployment/nats-server -- \
nats stream info K8S_STREAM

# 查看 stream 中的訊息
kubectl exec deployment/nats-server -- \
nats stream view K8S_STREAM
```

### 5. JetStream 效能測試
```bash
# 批量發布測試 JetStream 效能
for i in {1..10}; do
  kubectl exec deployment/nats-client-app -- \
  curl -s -X POST http://localhost:8080/api/nats/jetstream/publish \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "jetstream.perf.test",
    "streamName": "K8S_STREAM",
    "payload": {"batch": '${i}', "message": "Performance test"}
  }' &
done
wait

echo "JetStream 批量發布完成"
```

### 6. JetStream 配置驗證
```bash
# 檢查應用程式 JetStream 配置
kubectl exec deployment/nats-client-app -- \
curl http://localhost:8080/actuator/env | jq '.propertySources[] | select(.name | contains("application")) | .properties | to_entries | map(select(.key | startswith("nats.jet-stream"))) | from_entries'

# 或檢查特定配置
kubectl exec deployment/nats-client-app -- \
env | grep NATS_JETSTREAM
```

## 🔍 故障排除

### 常見問題及解決方案

#### 1. Pod 無法啟動
```bash
# 檢查 Pod 詳細狀態
kubectl describe pod <pod-name>

# 檢查 Init Container 日誌
kubectl logs <pod-name> -c wait-for-nats
kubectl logs <pod-name> -c wait-for-oracle

# 常見解決方案:
# - 等待依賴服務完全啟動
# - 檢查鏡像是否正確加載
# - 驗證資源配額是否足夠
```

#### 2. Oracle 資料庫鎖定問題
```bash
# 症狀: ORA-27086: unable to lock file - already in use
# 解決方案: 刪除並重新啟動 Oracle Pod
kubectl delete pod -l app=oracle-db

# 等待新 Pod 啟動
kubectl get pods -w
```

#### 3. 應用程式 CrashLoopBackOff
```bash
# 檢查最近的錯誤日誌
kubectl logs <pod-name> --previous

# 常見原因和解決方案:
# - 資料庫連接失敗: 確保 Oracle DB 完全啟動
# - NATS 連接失敗: 檢查 NATS 服務狀態
# - 記憶體不足: 調整 resources.limits
```

#### 4. 外部網路訪問問題
```bash
# 檢查 Service 配置
kubectl get service nats-client-external -o yaml

# 檢查 NodePort 是否正確開放
kubectl get service nats-client-external

# Minikube 特定檢查
minikube service nats-client-external --url
```

#### 5. 重新部署應用
```bash
# 重新啟動部署 (不改變配置)
kubectl rollout restart deployment/nats-client-app

# 強制重新部署 (如果更新了鏡像)
kubectl rollout restart deployment/nats-client-app
kubectl rollout status deployment/nats-client-app
```

### 調試命令集合
```bash
# 檢查整體狀態
kubectl get all

# 檢查事件
kubectl get events --sort-by=.metadata.creationTimestamp

# 進入 Pod 進行調試
kubectl exec -it deployment/nats-client-app -- /bin/bash

# 檢查網路連通性
kubectl exec deployment/nats-client-app -- nc -zv nats 4222
kubectl exec deployment/nats-client-app -- nc -zv oracle-db 1521

# 檢查 DNS 解析
kubectl exec deployment/nats-client-app -- nslookup nats
kubectl exec deployment/nats-client-app -- nslookup oracle-db
```

## 🧹 清理環境

### 完整清理
```bash
# 刪除所有部署的資源
kubectl delete -f k8s-deploy-all.yml

# 確認清理完成
kubectl get all
kubectl get pvc
kubectl get pv

# 清理掛起的 Pod (如果有)
kubectl delete pods --field-selector=status.phase=Failed
```

### 部分清理 (保留資料)
```bash
# 僅刪除應用程式 (保留資料庫)
kubectl delete deployment nats-client-app
kubectl delete service nats-client-external

# 僅重新啟動有問題的服務
kubectl delete pod -l app=nats-client-app
```

## 📝 Enhanced NATS 部署檢查清單

部署前確認：
- [ ] Docker 鏡像已構建並加載到集群 (`nats-client:latest`)
- [ ] Kubernetes 集群資源足夠 (至少 8GB RAM)
- [ ] kubectl 能正常連接到集群
- [ ] Maven 編譯成功 (`mvn clean compile -DskipTests`)

部署後驗證：
- [ ] 所有 Pod 都是 Running 狀態 (nats-client-app, nats-server, oracle-db)
- [ ] NATS Server JetStream 功能啟用
- [ ] Oracle Database 完全啟動並可連接
- [ ] Spring Boot Actuator 健康檢查通過 (`/actuator/health`)
- [ ] Enhanced NATS Publish API 測試成功 (JetStream 後端)
- [ ] Enhanced NATS Request API 測試成功 (Template Method 處理)
- [ ] 超時處理和錯誤處理正常工作
- [ ] Micrometer 指標收集正常 (`/api/nats/statistics`)

Enhanced Features 驗證：
- [ ] Template Method Pattern 正確路由請求到對應處理器
- [ ] Observer Pattern 事件發布功能正常
- [ ] Factory Pattern 指標創建和管理正常
- [ ] 數據庫審計日誌記錄完整
- [ ] 關聯 ID 追蹤功能正常

## 🎯 測試結果範例

### Enhanced NATS Request API 回應範例

#### 成功的 JetStream 處理回應 (Template Method)
```json
{
  "correlationId": "CORR-6515a0cc-32b0-43e1-bf9f-88c1d7b5d7ae",
  "subject": "test.k8s",
  "success": true,
  "responsePayload": "Message published to JetStream successfully - processing asynchronously",
  "errorMessage": null,
  "timestamp": "2025-08-23T07:04:26.718973818"
}
```

#### 超時的 Enhanced NATS 回應
```json
{
  "correlationId": "CORR-190b92f4-a98a-4648-a042-f10bd1d1bcdb",
  "subject": "test.k8s",
  "success": false,
  "responsePayload": null,
  "errorMessage": "com.example.natsclient.exception.NatsTimeoutException: No response received within timeout period",
  "timestamp": "2025-08-23T07:04:26.718973818"
}
```

#### Enhanced NATS 統計回應範例
```json
{
  "totalRequests": 34,
  "pendingRequests": 0,
  "successfulRequests": 29,
  "failedRequests": 0,
  "timeoutRequests": 5,
  "errorRequests": 0,
  "successRate": 85.29411764705883
}
```

## 💡 Enhanced NATS 最佳實踐

### 架構設計最佳實踐
1. **Template Method Pattern**: Enhanced Service 使用專用處理器 (NatsRequestProcessor, NatsPublishProcessor) 確保責任分離
2. **Observer Pattern**: 事件驅動架構通過 NatsEventPublisher 實現松耦合監控
3. **Factory Pattern**: MetricsFactory 集中管理 Micrometer 指標創建和配置
4. **雙重操作模式**: 智能路由使用 NATS Core 處理請求-回應，JetStream 處理發布操作

### 運維最佳實踐
1. **資源管理**: 所有容器都設定了適當的 resource limits 和 requests
2. **健康檢查**: 使用 Spring Boot Actuator 的 liveness 和 readiness 探測
3. **依賴順序**: Init containers 確保 NATS 和 Oracle DB 先啟動
4. **監控指標**: Micrometer 集成提供實時性能指標和成功率統計
5. **優雅關閉**: 正確處理 SIGTERM 信號以實現零停機部署
6. **關聯追蹤**: 自動生成和管理關聯 ID 用於端到端追蹤

### 測試最佳實踐
1. **分層測試**: 單元測試 (100+ 測試案例) + 集成測試 + 性能測試
2. **壓力測試**: 內建並發請求處理驗證和記憶體洩漏檢測
3. **監控驗證**: 確認指標收集、成功率計算和錯誤追蹤正常工作
4. **容器內測試**: 優先使用 `kubectl exec` 進行內部測試以避免網路問題

### 生產部署建議
1. **數據持久化**: Oracle DB 使用 PVC 確保數據持久性
2. **JetStream 配置**: 確保 NATS Server 正確配置 JetStream 功能
3. **指標監控**: 集成 Prometheus 收集 Micrometer 指標
4. **日誌集中化**: 使用 ELK Stack 或類似工具收集應用日誌

---

這份指南基於 **Enhanced NATS Message Service** 的實際部署經驗，包含 Template Method、Observer 和 Factory 設計模式的最佳實踐，確保企業級 NATS Client Service 能在 Kubernetes 環境中穩定高效運行。

**版本**: Enhanced NATS v0.0.1-SNAPSHOT (2025年8月)  
**架構**: Template Method + Observer + Factory Patterns + JetStream Integration