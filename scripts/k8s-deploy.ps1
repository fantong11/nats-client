# Kubernetes 快速部署腳本 (Windows PowerShell 版本)
# 適用於 NATS Client Service

param(
    [switch]$SkipBuild = $false,
    [switch]$SkipTest = $false,
    [switch]$Help = $false
)

# 顏色函數
function Write-Info {
    param([string]$Message)
    Write-Host "[INFO] $Message" -ForegroundColor Blue
}

function Write-Success {
    param([string]$Message)
    Write-Host "[SUCCESS] $Message" -ForegroundColor Green
}

function Write-Warning {
    param([string]$Message)
    Write-Host "[WARNING] $Message" -ForegroundColor Yellow
}

function Write-Error {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

# 顯示幫助信息
function Show-Help {
    Write-Host "NATS Client Service - Kubernetes 部署腳本"
    Write-Host ""
    Write-Host "用法: .\k8s-deploy.ps1 [參數]"
    Write-Host ""
    Write-Host "參數:"
    Write-Host "  -SkipBuild    跳過 Docker 鏡像構建"
    Write-Host "  -SkipTest     跳過應用程式測試"
    Write-Host "  -Help         顯示此幫助信息"
    Write-Host ""
    Write-Host "範例:"
    Write-Host "  .\k8s-deploy.ps1                # 完整部署"
    Write-Host "  .\k8s-deploy.ps1 -SkipBuild     # 跳過構建"
    Write-Host "  .\k8s-deploy.ps1 -SkipTest      # 跳過測試"
}

# 檢查必要工具
function Test-Prerequisites {
    Write-Info "檢查前置要求..."
    
    # 檢查 Docker
    try {
        $null = docker --version
    }
    catch {
        Write-Error "Docker 未安裝或不在 PATH 中"
        exit 1
    }
    
    # 檢查 kubectl
    try {
        $null = kubectl version --client=true
    }
    catch {
        Write-Error "kubectl 未安裝或不在 PATH 中"
        exit 1
    }
    
    # 檢查 kubectl 連接
    try {
        $null = kubectl cluster-info 2>$null
        if ($LASTEXITCODE -ne 0) { throw }
    }
    catch {
        Write-Error "kubectl 無法連接到 Kubernetes 集群"
        exit 1
    }
    
    Write-Success "前置要求檢查通過"
}

# 構建 Docker 鏡像
function Build-Image {
    Write-Info "開始構建 Docker 鏡像..."
    
    # 檢查是否在專案根目錄
    if (-not (Test-Path "pom.xml")) {
        Write-Error "請在專案根目錄執行此腳本"
        exit 1
    }
    
    # 編譯應用程式
    Write-Info "編譯應用程式..."
    & .\apache-maven-3.9.6\bin\mvn clean compile -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Maven 編譯失敗"
        exit 1
    }
    
    # 構建 Docker 鏡像
    Write-Info "構建 Docker 鏡像..."
    docker build -t nats-client:latest .
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Docker 鏡像構建失敗"
        exit 1
    }
    
    # 檢查是否使用 Minikube
    try {
        $minikubeStatus = minikube status 2>$null
        if ($LASTEXITCODE -eq 0) {
            Write-Info "檢測到 Minikube，加載鏡像..."
            minikube image load nats-client:latest
        }
    }
    catch {
        Write-Info "未使用 Minikube 或 Minikube 未啟動"
    }
    
    Write-Success "Docker 鏡像構建完成"
}

# 部署到 Kubernetes
function Deploy-ToK8s {
    Write-Info "部署到 Kubernetes..."
    
    # 檢查 k8s-deploy-all.yml 是否存在
    if (-not (Test-Path "k8s-deploy-all.yml")) {
        Write-Error "k8s-deploy-all.yml 文件不存在"
        exit 1
    }
    
    # 部署所有資源
    kubectl apply -f k8s-deploy-all.yml
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Kubernetes 部署失敗"
        exit 1
    }
    
    Write-Success "Kubernetes 資源部署完成"
}

# 等待部署完成
function Wait-ForDeployment {
    Write-Info "等待部署完成..."
    
    # 等待 Pod 啟動
    Write-Info "等待 NATS Server 啟動..."
    kubectl wait --for=condition=ready pod -l app=nats-server --timeout=300s
    
    Write-Info "等待 Oracle Database 啟動..."
    kubectl wait --for=condition=ready pod -l app=oracle-db --timeout=600s
    
    Write-Info "等待 NATS Client Application 啟動..."
    kubectl wait --for=condition=ready pod -l app=nats-client-app --timeout=300s
    
    Write-Success "所有服務已啟動"
}

# 檢查服務狀態
function Test-Services {
    Write-Info "檢查服務狀態..."
    
    Write-Host ""
    Write-Info "Pod 狀態:"
    kubectl get pods -o wide
    
    Write-Host ""
    Write-Info "Service 狀態:"
    kubectl get services
    
    Write-Host ""
    Write-Info "Deployment 狀態:"
    kubectl get deployments
}

# 測試應用程式
function Test-Application {
    Write-Info "測試應用程式功能..."
    
    # 獲取 nats-client-app pod 名稱
    $podName = kubectl get pods -l app=nats-client-app -o jsonpath='{.items[0].metadata.name}'
    
    if (-not $podName) {
        Write-Error "找不到 nats-client-app pod"
        return
    }
    
    Write-Info "使用 Pod: $podName"
    
    # 測試健康檢查
    Write-Info "測試健康檢查..."
    $healthResult = kubectl exec $podName -- curl -f -s http://localhost:8080/actuator/health 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Success "健康檢查通過"
    } else {
        Write-Warning "健康檢查失敗"
    }
    
    # 測試 Publish API
    Write-Info "測試 Publish API..."
    $publishPayload = @{
        subject = "test.publish"
        payload = @{
            message = "Hello from K8s PowerShell script"
            timestamp = (Get-Date -Format "yyyy-MM-ddTHH:mm:ssZ")
        }
    } | ConvertTo-Json -Depth 3 -Compress
    
    $publishResult = kubectl exec $podName -- curl -s -X POST http://localhost:8080/api/nats/publish -H "Content-Type: application/json" -d $publishPayload
    
    if ($publishResult -match "PUBLISHED") {
        Write-Success "Publish API 測試通過"
    } else {
        Write-Warning "Publish API 測試失敗: $publishResult"
    }
    
    # 測試 Request API (使用 test.echo subject)
    Write-Info "測試 Request API..."
    $requestPayload = @{
        subject = "test.echo"
        payload = @{
            message = "Hello Echo from K8s PowerShell script"
            requestId = "ps-script-test-001"
        }
    } | ConvertTo-Json -Depth 3 -Compress
    
    $requestResult = kubectl exec $podName -- curl -s -X POST http://localhost:8080/api/nats/request -H "Content-Type: application/json" -d $requestPayload
    
    if ($requestResult -match '"success":true') {
        Write-Success "Request API 測試通過"
        try {
            $resultObj = $requestResult | ConvertFrom-Json
            Write-Host "回應: $($resultObj.responsePayload)"
        } catch {
            Write-Host "回應: $requestResult"
        }
    } else {
        Write-Warning "Request API 測試結果: $requestResult"
    }
}

# 顯示訪問信息
function Show-AccessInfo {
    Write-Info "服務訪問信息:"
    
    # 檢查是否是 Minikube
    try {
        $minikubeIp = minikube ip 2>$null
        if ($LASTEXITCODE -eq 0 -and $minikubeIp) {
            Write-Host ""
            Write-Host "使用 Minikube，外部訪問地址:" -ForegroundColor Cyan
            Write-Host "  NATS Client API: http://$minikubeIp:30080" -ForegroundColor White
            Write-Host "  NATS Server Monitor: http://$minikubeIp:30822" -ForegroundColor White
            Write-Host ""
            Write-Host "測試命令範例:" -ForegroundColor Cyan
            Write-Host "curl -X POST http://$minikubeIp:30080/api/nats/publish \" -ForegroundColor White
            Write-Host "  -H 'Content-Type: application/json' \" -ForegroundColor White
            Write-Host "  -d '{`"subject`": `"test.publish`", `"payload`": {`"message`": `"Hello from external`"}}'" -ForegroundColor White
        }
    }
    catch {
        Write-Host ""
        Write-Host "使用 Port Forward 訪問:" -ForegroundColor Cyan
        Write-Host "  kubectl port-forward service/nats-client-external 8080:8080" -ForegroundColor White
        Write-Host "  然後訪問: http://localhost:8080" -ForegroundColor White
    }
    
    Write-Host ""
    Write-Host "內部測試命令:" -ForegroundColor Cyan
    Write-Host "kubectl exec deployment/nats-client-app -- curl -X POST http://localhost:8080/api/nats/request \" -ForegroundColor White
    Write-Host "  -H 'Content-Type: application/json' \" -ForegroundColor White
    Write-Host "  -d '{`"subject`": `"test.echo`", `"payload`": {`"message`": `"Internal test`"}}'" -ForegroundColor White
}

# 主函數
function Main {
    if ($Help) {
        Show-Help
        return
    }
    
    Write-Host "==========================================" -ForegroundColor Magenta
    Write-Host "NATS Client Service - Kubernetes 部署腳本" -ForegroundColor Magenta
    Write-Host "==========================================" -ForegroundColor Magenta
    Write-Host ""
    
    try {
        # 執行部署步驟
        Test-Prerequisites
        
        if (-not $SkipBuild) {
            Build-Image
        } else {
            Write-Info "跳過鏡像構建"
        }
        
        Deploy-ToK8s
        Wait-ForDeployment
        Test-Services
        
        if (-not $SkipTest) {
            Test-Application
        } else {
            Write-Info "跳過應用程式測試"
        }
        
        Show-AccessInfo
        
        Write-Host ""
        Write-Success "部署完成！"
        Write-Host ""
        Write-Host "後續操作:" -ForegroundColor Cyan
        Write-Host "  查看日誌: kubectl logs -f deployment/nats-client-app" -ForegroundColor White
        Write-Host "  查看狀態: kubectl get pods" -ForegroundColor White
        Write-Host "  清理環境: kubectl delete -f k8s-deploy-all.yml" -ForegroundColor White
        
    }
    catch {
        Write-Error "部署過程中發生錯誤: $($_.Exception.Message)"
        exit 1
    }
}

# 處理 Ctrl+C
$null = Register-EngineEvent -SourceIdentifier PowerShell.Exiting -SupportEvent -Action {
    Write-Host ""
    Write-Warning "部署被中斷"
}

# 執行主函數
Main