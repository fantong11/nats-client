# Kubernetes 部署實戰指南 (JetStream 版本)

本指南記錄了 NATS Client Service 在 Kubernetes 環境中的實際部署、測試流程和最佳實踐。此版本包含完整的 JetStream 支援，提供持久化訊息存儲和增強的可靠性。

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

#### 測試 Publish API
```bash
# 進入應用容器測試 publish
kubectl exec deployment/nats-client-app -- \
curl -X POST http://localhost:8080/api/nats/publish \
-H "Content-Type: application/json" \
-d '{
  "subject": "test.publish",
  "payload": {
    "message": "Hello from K8s",
    "timestamp": "'$(date -Iseconds)'"
  }
}'

# 期望回應:
# {
#   "requestId": "uuid-123",
#   "status": "PUBLISHED",
#   "trackingUrl": "/api/nats/status/uuid-123",
#   ...
# }
```

#### 測試 Request API
```bash
# 測試有回應的 subject (test.echo)
kubectl exec deployment/nats-client-app -- \
curl -X POST http://localhost:8080/api/nats/request \
-H "Content-Type: application/json" \
-d '{
  "subject": "test.echo",
  "payload": {
    "message": "Hello Echo Test from K8s",
    "requestId": "k8s-test-001"
  }
}'

# 期望成功回應:
# {
#   "correlationId": "CORR-xxx",
#   "subject": "test.echo",
#   "success": true,
#   "responsePayload": {...},
#   "errorMessage": null,
#   "timestamp": "..."
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

### 3. 健康檢查測試
```bash
# 檢查應用健康狀態
kubectl exec deployment/nats-client-app -- \
curl http://localhost:8080/actuator/health

# 檢查 NATS 特定健康檢查
kubectl exec deployment/nats-client-app -- \
curl http://localhost:8080/api/nats/health
```

### 4. 效能測試
```bash
# 檢查資源使用情況
kubectl top pods

# 查看應用統計資訊
kubectl exec deployment/nats-client-app -- \
curl http://localhost:8080/api/nats/statistics
```

## 🚀 JetStream 功能測試

### 1. JetStream 發布測試
```bash
# 測試 JetStream 發布功能
kubectl exec deployment/nats-client-app -- \
curl -X POST http://localhost:8080/api/nats/jetstream/publish \
-H "Content-Type: application/json" \
-d '{
  "subject": "jetstream.test.publish",
  "streamName": "K8S_STREAM", 
  "payload": {
    "message": "Hello JetStream from K8s",
    "timestamp": "'$(date -Iseconds)'"
  }
}'

# 期望回應包含 PublishAck 資訊:
# {
#   "requestId": "uuid-123",
#   "subject": "jetstream.test.publish",
#   "streamName": "K8S_STREAM",
#   "sequenceNumber": 1,
#   "success": true,
#   "timestamp": "..."
# }
```

### 2. JetStream 請求/回應測試
```bash
# 測試 JetStream 增強的請求/回應
kubectl exec deployment/nats-client-app -- \
curl -X POST http://localhost:8080/api/nats/jetstream/request \
-H "Content-Type: application/json" \
-d '{
  "subject": "jetstream.test.echo",
  "payload": {
    "message": "JetStream Echo Test",
    "requestId": "js-k8s-test-001"
  }
}'

# JetStream 提供更可靠的訊息傳遞保證
```

### 3. JetStream vs NATS Core 比較測試
```bash
# 1. 傳統 NATS Core publish
kubectl exec deployment/nats-client-app -- \
curl -X POST http://localhost:8080/api/nats/publish \
-H "Content-Type: application/json" \
-d '{"subject": "core.test", "payload": {"message": "NATS Core"}}'

# 2. JetStream publish (提供 ACK 和持久性)
kubectl exec deployment/nats-client-app -- \
curl -X POST http://localhost:8080/api/nats/jetstream/publish \
-H "Content-Type: application/json" \
-d '{
  "subject": "jetstream.test", 
  "streamName": "K8S_STREAM",
  "payload": {"message": "JetStream with persistence"}
}'

# 比較回應差異：
# - NATS Core: 簡單的成功/失敗狀態
# - JetStream: 包含 sequence number, stream name, 和 ACK 確認
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

## 📝 部署檢查清單

部署前確認：
- [ ] Docker 鏡像已構建並加載到集群
- [ ] Kubernetes 集群資源足夠 (至少 8GB RAM)
- [ ] kubectl 能正常連接到集群

部署後驗證：
- [ ] 所有 Pod 都是 Running 狀態
- [ ] NATS Server 連接成功 
- [ ] Oracle Database 完全啟動
- [ ] 應用程式健康檢查通過
- [ ] Publish API 測試成功
- [ ] Request/Response API 測試成功
- [ ] 超時處理測試正常

## 🎯 測試結果範例

### 成功的 Request API 回應
```json
{
  "correlationId": "CORR-6515a0cc-32b0-43e1-bf9f-88c1d7b5d7ae",
  "subject": "test.echo",
  "success": true,
  "responsePayload": {
    "original_payload": "{\"message\":\"Hello Echo Test from K8s\",\"requestId\":\"k8s-test-001\"}",
    "processed_by": "nats-test-subscriber",
    "message": "Echo response",
    "status": "success",
    "timestamp": "2025-08-21T17:22:32.495810522"
  },
  "errorMessage": null,
  "timestamp": "2025-08-21T17:22:32.504295144"
}
```

### 超時的 Request API 回應
```json
{
  "correlationId": "CORR-190b92f4-a98a-4648-a042-f10bd1d1bcdb",
  "subject": "test.request",
  "success": false,
  "responsePayload": null,
  "errorMessage": "com.example.natsclient.exception.NatsTimeoutException: No response received within timeout period",
  "timestamp": "2025-08-21T17:22:02.672436672"
}
```

## 💡 最佳實踐

1. **資源管理**: 所有容器都設定了適當的 resource limits 和 requests
2. **健康檢查**: 使用 livenessProbe 和 readinessProbe 確保服務健康
3. **依賴順序**: Init containers 確保依賴服務先啟動
4. **日誌管理**: 集中化日誌收集便於問題診斷
5. **監控指標**: 暴露 Prometheus metrics 用於監控
6. **優雅關閉**: 正確處理 SIGTERM 信號以實現零停機部署

這份指南基於實際部署經驗，提供了從構建到測試的完整流程，確保 NATS Client Service 能在 Kubernetes 環境中穩定運行。