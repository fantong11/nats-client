# 專案結構說明

本文件詳細說明 NATS Client Service 專案的資料夾結構和組織方式。

## 📁 根目錄結構

```
nats-client/
├── 📁 apache-maven-3.9.6/          # 內建 Maven 工具
├── 📁 docs/                        # 專案文件
├── 📁 src/                         # 原始碼
├── 📁 target/                      # 編譯輸出 (自動產生)
├── 📄 docker-compose-with-app.yml  # Docker Compose 配置
├── 📄 Dockerfile                   # Docker 映像檔定義
├── 📄 k8s-deploy-all.yml          # Kubernetes 部署配置
├── 📄 pom.xml                      # Maven 專案配置
├── 📄 README.md                    # 主要專案說明
└── 📄 test-api.http                # API 測試腳本
```

## 📚 文件資料夾 (`docs/`)

完整的專案文件，按主題分類組織：

```
docs/
├── 📄 API.md                       # 完整 API 參考文件
├── 📄 DEPLOYMENT.md                # 部署策略指南
├── 📄 DEVELOPMENT.md               # 開發環境設定
├── 📄 PROJECT_STRUCTURE.md         # 本文件 - 專案結構說明
├── 📄 TESTING.md                   # 測試策略和範例
└── 📄 TROUBLESHOOTING.md           # 問題診斷和解決方案
```

### 各文件用途
- **API.md**: 完整的 API 文件，包含多種程式語言範例
- **DEPLOYMENT.md**: Docker 和 Kubernetes 的產品環境部署指南
- **DEVELOPMENT.md**: 本地開發環境設定和生產力工具
- **TESTING.md**: 全面的測試策略和範例
- **TROUBLESHOOTING.md**: 常見問題、診斷方法和解決方案

## 🔧 建置工具 (`apache-maven-3.9.6/`)

內建的 Maven 安裝，確保跨環境的一致性建置：

```
apache-maven-3.9.6/
├── 📁 bin/                         # Maven 執行檔
├── 📁 boot/                        # 啟動載入庫
├── 📁 conf/                        # Maven 配置
└── 📁 lib/                         # Maven 執行時庫
```

### 用途
- 確保開發團隊使用一致的 Maven 版本
- 消除 "在我的機器上可以運行" 的建置問題
- 包含所有必要的 Maven 相依性

## 💻 原始碼 (`src/`)

標準的 Maven 專案結構，包含主應用程式和測試碼：

```
src/
├── 📁 main/                        # 應用程式原始碼
│   ├── 📁 java/                    # Java 原始檔
│   └── 📁 resources/               # 配置檔和靜態資源
└── 📁 test/                        # 測試原始碼
    ├── 📁 java/                    # 測試 Java 檔案
    └── 📁 resources/               # 測試配置檔
```

### 主應用程式 (`src/main/java/`)

```
src/main/java/com/example/natsclient/
├── 📄 NatsClientApplication.java   # Spring Boot 主類別
├── 📁 config/                     # 配置類別
├── 📁 controller/                 # REST API 控制器
├── 📁 entity/                     # 資料庫實體
├── 📁 exception/                  # 自定義例外和處理器
├── 📁 metrics/                    # 指標和監控
├── 📁 model/                      # 資料傳輸物件
├── 📁 repository/                 # 資料庫存取層
└── 📁 service/                    # 業務邏輯服務
```

#### 套件詳細說明

##### `config/` - 配置類別
- **NatsConfig.java**: NATS 連線配置
- **NatsProperties.java**: NATS 配置屬性綁定
- **VaultConfig.java**: HashiCorp Vault 整合
- **VaultProperties.java**: Vault 配置屬性

##### `controller/` - REST API 層
- **NatsController.java**: NATS 操作的主要 REST API 端點

##### `entity/` - 資料庫實體
- **NatsRequestLog.java**: 請求/回應記錄的 JPA 實體

##### `exception/` - 錯誤處理
- **GlobalExceptionHandler.java**: 使用 @ControllerAdvice 的全域例外處理
- **NatsClientException.java**: NATS 客戶端錯誤的基礎例外
- **NatsRequestException.java**: 請求特定例外
- **NatsTimeoutException.java**: 逾時處理例外
- **PayloadProcessingException.java**: JSON 載荷處理錯誤

##### `metrics/` - 監控和指標
- **NatsMetricsCollector.java**: 自定義指標收集
- **NatsMetricsConfiguration.java**: 指標配置

##### `model/` - 資料傳輸物件
- **NatsCredentials.java**: NATS 認證憑證模型

##### `repository/` - 資料存取層
- **NatsRequestLogRepository.java**: 請求記錄的 JPA 存取庫

##### `service/` - 業務邏輯層
```
service/
├── 📄 NatsClientService.java           # 主要 NATS 客戶端介面
├── 📄 NatsMessageService.java          # 訊息處理介面
├── 📄 NatsOrchestrationService.java    # 請求編排服務
├── 📄 NatsResponseHandler.java         # 回應處理
├── 📄 NatsTestSubscriber.java          # 測試訊息訂閱器
├── 📄 PayloadProcessor.java            # 載荷處理介面
├── 📄 RequestLogService.java           # 請求記錄介面
├── 📄 RetryService.java                # 重試邏輯介面
├── 📁 impl/                            # 服務實作
└── 📁 validator/                       # 請求驗證
```

###### 服務實作 (`service/impl/`)
- **EnhancedNatsMessageService.java**: 進階 NATS 訊息功能
- **JsonPayloadProcessor.java**: JSON 載荷處理實作
- **K8sCredentialServiceImpl.java**: Kubernetes 憑證管理
- **NatsMessageServiceImpl.java**: 核心 NATS 訊息實作
- **RequestLogServiceImpl.java**: 資料庫記錄實作
- **RetryServiceImpl.java**: 重試邏輯實作

###### 驗證器 (`service/validator/`)
- **RequestValidator.java**: API 請求的輸入驗證

### 應用程式資源 (`src/main/resources/`)

```
src/main/resources/
├── 📄 application.yml              # Spring Boot 配置
└── 📄 schema.sql                   # 資料庫架構建立腳本
```

- **application.yml**: 多環境 Spring Boot 配置 (default, local, kubernetes)
- **schema.sql**: Oracle 資料庫架構建立腳本

### 測試程式碼 (`src/test/java/`)

遵循測試最佳實務的良好組織測試結構：

```
src/test/java/com/example/natsclient/
├── 📁 controller/                  # 控制器層測試
├── 📁 demo/                       # 示範和探索性測試
├── 📁 integration/                # 整合測試
├── 📁 performance/                # 效能測試
└── 📁 service/                    # 服務層測試
```

#### 測試組織

##### `controller/` - 控制器測試
- **NatsControllerTest.java**: 使用 MockMvc 的 REST API 端點測試

##### `demo/` - 示範和探索
- **NatsFunctionalityDemo.java**: NATS 功能示範

##### `integration/` - 整合測試
- **NatsIntegrationTest.java**: 使用真實元件的端到端整合測試

##### `performance/` - 效能測試
- **NatsPerformanceTest.java**: 負載測試和效能基準測試

##### `service/` - 服務層測試
```
service/
├── 📄 NatsOrchestrationServiceTest.java    # 編排服務測試
├── 📁 impl/                               # 實作測試
│   ├── 📄 EnhancedNatsMessageServiceTest.java
│   ├── 📄 JsonPayloadProcessorTest.java
│   ├── 📄 NatsMessageServiceImplTest.java
│   └── 📄 RequestLogServiceImplTest.java
└── 📁 validator/                          # 驗證器測試
    └── 📄 RequestValidatorTest.java
```

### 測試資源 (`src/test/resources/`)

```
src/test/resources/
└── 📄 application-test.yml         # 測試專用配置
```

## 🏗️ 建置輸出 (`target/`)

Maven 產生的建置產物 (不提交至版本控制)：

```
target/
├── 📁 classes/                     # 編譯的主類別
├── 📁 test-classes/                # 編譯的測試類別
├── 📁 generated-sources/           # 產生的原始碼
├── 📁 generated-test-sources/      # 產生的測試原始碼
├── 📁 maven-archiver/              # Maven 封存器中繼資料
├── 📁 maven-status/                # Maven 外掛程式狀態
├── 📁 surefire-reports/            # 測試執行報告
└── 📄 nats-client-0.0.1-SNAPSHOT.jar  # 可執行 JAR 檔案
```

### 用途
- **classes/**: 主應用程式的編譯 Java 位元組碼
- **test-classes/**: 編譯的測試位元組碼
- **surefire-reports/**: JUnit 測試執行結果
- **JAR 檔案**: 可執行的 Spring Boot 應用程式

## 🐳 部署配置

### Docker 配置
- **Dockerfile**: 產品就緒容器的多階段 Docker 建置
- **docker-compose-with-app.yml**: 完整堆疊部署 (應用程式 + 相依性)

### Kubernetes 配置
- **k8s-deploy-all.yml**: 包含所有元件的完整 Kubernetes 部署

## 📋 專案檔案

### 建置配置
- **pom.xml**: Maven 專案配置，包含相依性、外掛程式和設定檔

### API 測試
- **test-api.http**: 包含 25+ API 測試情境的 HTTP 客戶端檔案

### 文件
- **README.md**: 主要專案文件，包含快速開始和導覽

## 🎯 資料夾使用指導原則

### 開發工作流程
1. **原始碼**: 所有業務邏輯放在 `src/main/java/`
2. **配置**: 環境配置放在 `src/main/resources/`
3. **測試**: 完整測試放在 `src/test/java/`
4. **文件**: 所有文件放在 `docs/` 資料夾
5. **建置**: 使用 `apache-maven-3.9.6/bin/mvn` 確保一致性建置

### 檔案組織原則
- **單一職責**: 每個類別/套件都有一個明確的目的
- **分層架構**: 控制器、服務和存取庫之間清楚分離
- **測試覆蓋**: 測試結構反映主程式碼結構
- **配置管理**: 依環境分離的配置設定檔
- **文件完整**: 專案所有面向的完整文件

### 最佳實務
- 保持套件專注和內聚
- 遵循 Maven 標準目錄結構
- 維持所有層的測試覆蓋
- 記錄所有主要元件和 API
- 使用配置設定檔應對不同環境

這個結構確保了可維護性、可擴展性，並方便新開發人員上手。

## 🔍 整理後的變更

在本次整理中，我們移除了以下不必要的檔案：
- `k8s-nats-client.yml` - 個別的 Kubernetes 配置 (已整合到 k8s-deploy-all.yml)
- `k8s-nats.yml` - 個別的 NATS Kubernetes 配置
- `k8s-oracle.yml` - 個別的 Oracle Kubernetes 配置
- `src/main/java/com/example/natsclient/health/` - 空的資料夾

現在專案結構更加乾淨，只保留實際使用的部署配置檔案。