#!/bin/bash

# Kubernetes 快速部署腳本
# 適用於 NATS Client Service

set -e

# 顏色設定
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日誌函數
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 檢查必要工具
check_prerequisites() {
    log_info "檢查前置要求..."
    
    if ! command -v docker &> /dev/null; then
        log_error "Docker 未安裝或不在 PATH 中"
        exit 1
    fi
    
    if ! command -v kubectl &> /dev/null; then
        log_error "kubectl 未安裝或不在 PATH 中"
        exit 1
    fi
    
    # 檢查 kubectl 連接
    if ! kubectl cluster-info &> /dev/null; then
        log_error "kubectl 無法連接到 Kubernetes 集群"
        exit 1
    fi
    
    log_success "前置要求檢查通過"
}

# 構建 Docker 鏡像
build_image() {
    log_info "開始構建 Docker 鏡像..."
    
    # 檢查是否在專案根目錄
    if [ ! -f "pom.xml" ]; then
        log_error "請在專案根目錄執行此腳本"
        exit 1
    fi
    
    # 編譯應用程式
    log_info "編譯應用程式..."
    ./apache-maven-3.9.6/bin/mvn clean compile -DskipTests
    
    # 構建 Docker 鏡像
    log_info "構建 Docker 鏡像..."
    docker build -t nats-client:latest .
    
    # 檢查是否使用 Minikube
    if command -v minikube &> /dev/null && minikube status &> /dev/null; then
        log_info "檢測到 Minikube，加載鏡像..."
        minikube image load nats-client:latest
    fi
    
    log_success "Docker 鏡像構建完成"
}

# 部署到 Kubernetes
deploy_to_k8s() {
    log_info "部署到 Kubernetes..."
    
    # 檢查 k8s-deploy-all.yml 是否存在
    if [ ! -f "k8s-deploy-all.yml" ]; then
        log_error "k8s-deploy-all.yml 文件不存在"
        exit 1
    fi
    
    # 部署所有資源
    kubectl apply -f k8s-deploy-all.yml
    
    log_success "Kubernetes 資源部署完成"
}

# 等待部署完成
wait_for_deployment() {
    log_info "等待部署完成..."
    
    # 等待 Pod 啟動
    log_info "等待 NATS Server 啟動..."
    kubectl wait --for=condition=ready pod -l app=nats-server --timeout=300s
    
    log_info "等待 Oracle Database 啟動..."
    kubectl wait --for=condition=ready pod -l app=oracle-db --timeout=600s
    
    log_info "等待 NATS Client Application 啟動..."
    kubectl wait --for=condition=ready pod -l app=nats-client-app --timeout=300s
    
    log_success "所有服務已啟動"
}

# 檢查服務狀態
check_services() {
    log_info "檢查服務狀態..."
    
    echo ""
    log_info "Pod 狀態:"
    kubectl get pods -o wide
    
    echo ""
    log_info "Service 狀態:"
    kubectl get services
    
    echo ""
    log_info "Deployment 狀態:"
    kubectl get deployments
}

# 測試應用程式
test_application() {
    log_info "測試應用程式功能..."
    
    # 獲取 nats-client-app pod 名稱
    POD_NAME=$(kubectl get pods -l app=nats-client-app -o jsonpath='{.items[0].metadata.name}')
    
    if [ -z "$POD_NAME" ]; then
        log_error "找不到 nats-client-app pod"
        return 1
    fi
    
    log_info "使用 Pod: $POD_NAME"
    
    # 測試健康檢查
    log_info "測試健康檢查..."
    if kubectl exec $POD_NAME -- curl -f -s http://localhost:8080/actuator/health > /dev/null; then
        log_success "健康檢查通過"
    else
        log_warning "健康檢查失敗"
    fi
    
    # 測試 Publish API (JetStream 底層)
    log_info "測試 Publish API (JetStream 底層實現)..."
    PUBLISH_RESULT=$(kubectl exec $POD_NAME -- curl -s -X POST http://localhost:8080/api/nats/publish \
        -H "Content-Type: application/json" \
        -d '{"subject": "test.publish", "payload": {"message": "Hello JetStream from K8s script", "timestamp": "'$(date -Iseconds)'"}}')
    
    if echo "$PUBLISH_RESULT" | grep -q "PUBLISHED"; then
        log_success "Publish API 測試通過 (JetStream 底層)"
    else
        log_warning "Publish API 測試失敗: $PUBLISH_RESULT"
    fi
    
    # 測試 Request API (JetStream 底層)
    log_info "測試 Request API (JetStream 底層實現)..."
    REQUEST_RESULT=$(kubectl exec $POD_NAME -- curl -s -X POST http://localhost:8080/api/nats/request \
        -H "Content-Type: application/json" \
        -d '{"subject": "test.echo", "payload": {"message": "Hello Echo with JetStream from K8s script", "requestId": "script-test-001"}}')
    
    if echo "$REQUEST_RESULT" | grep -q '"success":true'; then
        log_success "Request API 測試通過 (JetStream 底層)"
        echo "回應: $(echo "$REQUEST_RESULT" | jq -r '.responsePayload' 2>/dev/null || echo "$REQUEST_RESULT")"
    else
        log_warning "Request API 測試結果: $REQUEST_RESULT"
    fi
}

# 顯示訪問信息
show_access_info() {
    log_info "服務訪問信息:"
    
    # 檢查是否是 Minikube
    if command -v minikube &> /dev/null && minikube status &> /dev/null; then
        MINIKUBE_IP=$(minikube ip)
        echo ""
        echo "🌐 Minikube 服務訪問地址:"
        echo "  📡 NATS Client API:     http://$MINIKUBE_IP:30080"
        echo "  📚 Swagger UI:          http://$MINIKUBE_IP:30080/swagger-ui.html"
        echo "  💚 健康檢查:            http://$MINIKUBE_IP:30080/actuator/health"
        echo "  📊 統計信息:            http://$MINIKUBE_IP:30080/api/nats/statistics"
        echo "  📈 NATS 監控:           http://$MINIKUBE_IP:30822"
        echo ""
        echo "🧪 API 測試命令範例:"
        echo ""
        echo "# 發布消息 (JetStream)"
        echo "curl -X POST http://$MINIKUBE_IP:30080/api/nats/publish \\"
        echo "  -H 'Content-Type: application/json' \\"
        echo "  -d '{\"subject\": \"test.k8s.publish\", \"payload\": {\"message\": \"Hello from K8s!\", \"timestamp\": \"'$(date -Iseconds)'\"}}'"
        echo ""
        echo "# 請求響應測試"
        echo "curl -X POST http://$MINIKUBE_IP:30080/api/nats/request \\"
        echo "  -H 'Content-Type: application/json' \\"
        echo "  -d '{\"subject\": \"test.echo\", \"payload\": {\"message\": \"Hello Echo!\", \"requestId\": \"k8s-test-001\"}}'"
        echo ""
        echo "# 查看統計數據"
        echo "curl http://$MINIKUBE_IP:30080/api/nats/statistics"
    else
        echo ""
        echo "📡 使用 Port Forward 訪問:"
        echo "  kubectl port-forward service/nats-client-external 8080:8080"
        echo ""
        echo "然後訪問以下地址:"
        echo "  📡 API服務:             http://localhost:8080"
        echo "  📚 Swagger UI:          http://localhost:8080/swagger-ui.html"
        echo "  💚 健康檢查:            http://localhost:8080/actuator/health"
    fi
    
    echo ""
    echo "🔧 內部測試命令:"
    echo "kubectl exec deployment/nats-client-app -- curl -X POST http://localhost:8080/api/nats/request \\"
    echo "  -H 'Content-Type: application/json' \\"
    echo "  -d '{\"subject\": \"test.echo\", \"payload\": {\"message\": \"Internal test\"}}'"
    
    echo ""
    echo "📊 監控命令:"
    echo "kubectl logs -f deployment/nats-client-app          # 查看應用日誌"
    echo "kubectl get pods -w                                # 監控Pod狀態"
    echo "kubectl top pods                                   # 資源使用情況"
}

# 主函數
main() {
    echo "=========================================="
    echo "NATS Client Service - Kubernetes 部署腳本"
    echo "=========================================="
    echo ""
    
    # 解析參數
    SKIP_BUILD=false
    SKIP_TEST=false
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            --skip-build)
                SKIP_BUILD=true
                shift
                ;;
            --skip-test)
                SKIP_TEST=true
                shift
                ;;
            -h|--help)
                echo "用法: $0 [選項]"
                echo ""
                echo "選項:"
                echo "  --skip-build    跳過 Docker 鏡像構建"
                echo "  --skip-test     跳過應用程式測試"
                echo "  -h, --help      顯示此幫助信息"
                exit 0
                ;;
            *)
                log_error "未知參數: $1"
                echo "使用 -h 或 --help 查看用法"
                exit 1
                ;;
        esac
    done
    
    # 執行部署步驟
    check_prerequisites
    
    if [ "$SKIP_BUILD" = false ]; then
        build_image
    else
        log_info "跳過鏡像構建"
    fi
    
    deploy_to_k8s
    wait_for_deployment
    check_services
    
    if [ "$SKIP_TEST" = false ]; then
        test_application
    else
        log_info "跳過應用程式測試"
    fi
    
    show_access_info
    
    echo ""
    log_success "部署完成！"
    echo ""
    echo "後續操作:"
    echo "  查看日誌: kubectl logs -f deployment/nats-client-app"
    echo "  查看狀態: kubectl get pods"
    echo "  清理環境: kubectl delete -f k8s-deploy-all.yml"
}

# 處理 Ctrl+C
trap 'echo ""; log_warning "部署被中斷"; exit 130' INT

# 執行主函數
main "$@"