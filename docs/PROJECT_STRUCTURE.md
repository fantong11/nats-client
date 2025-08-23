# Enhanced NATS Client 專案結構說明

本文件詳細說明 Enhanced NATS Client Service 專案的資料夾結構和組織方式。此版本使用 Template Method、Observer 和 Factory 設計模式，提供企業級的 NATS 訊息處理能力。

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

##### `service/` - 業務邏輯層 (Enhanced Architecture)
```
service/
├── 📄 NatsClientService.java           # 主要 NATS 客戶端介面
├── 📄 NatsMessageService.java          # 訊息處理介面
├── 📄 NatsOperations.java              # NATS 操作抽象介面
├── 📄 NatsOrchestrationService.java    # 請求編排服務
├── 📄 PayloadProcessor.java            # 載荷處理介面
├── 📄 RequestLogService.java           # 請求記錄介面
├── 📄 ResponseHandler.java             # 回應處理介面
├── 📁 builder/                         # 建造者模式
├── 📁 factory/                         # 工廠模式 (Metrics)
├── 📁 impl/                            # 服務實作 (Enhanced)
├── 📁 observer/                        # 觀察者模式 (Events)
└── 📁 validator/                       # 請求驗證
```

###### 建造者模式 (`service/builder/`)
- **NatsPublishOptionsBuilder.java**: JetStream 發布選項建造者

###### 工廠模式 (`service/factory/`)
- **MetricsFactory.java**: Micrometer 指標工廠
- **MetricsFactory.NatsMetricsSet.java**: 指標集合封裝

###### 服務實作 (`service/impl/`) - Enhanced Architecture
- **AbstractNatsMessageProcessor.java**: Template Method 基礎處理器
- **EnhancedNatsMessageService.java**: 企業級 NATS 訊息服務 (主要服務)
- **HybridNatsOperations.java**: 雙模式操作 (NATS Core + JetStream)
- **JsonPayloadProcessor.java**: JSON 載荷處理實作
- **K8sCredentialServiceImpl.java**: Kubernetes 憑證管理
- **NatsMessageServiceImpl.java**: 原始 NATS 訊息實作 (向下兼容)
- **NatsPublishProcessor.java**: 發布專用處理器 (Template Method)
- **NatsRequestProcessor.java**: 請求專用處理器 (Template Method)
- **RequestLogServiceImpl.java**: 資料庫記錄實作
- **StringResponseHandler.java**: 字串回應處理器

###### 觀察者模式 (`service/observer/`)
- **NatsEventPublisher.java**: 事件發布者 (Observer Pattern)

###### 驗證器 (`service/validator/`)
- **RequestValidator.java**: API 請求的輸入驗證

##### `util/` - 工具類別 (新增)
- **CorrelationIdGenerator.java**: 關聯 ID 生成工具

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

#### 測試組織 (Enhanced Test Suite)

##### `controller/` - 控制器測試
- **NatsControllerTest.java**: 使用 MockMvc 的 REST API 端點測試

##### `demo/` - 示範和探索  
- **NatsFunctionalityDemo.java**: Enhanced NATS 功能完整示範 (包含設計模式展示)

##### `entity/` - 實體測試 (新增)
- 資料庫實體的單元測試

##### `exception/` - 例外處理測試 (新增)
- **GlobalExceptionHandlerTestSimple.java**: 全域例外處理器測試

##### `integration/` - 整合測試
- **NatsIntegrationTest.java**: Enhanced NATS 端到端整合測試

##### `model/` - 模型測試 (新增)
- 資料傳輸物件的測試

##### `performance/` - 效能測試 (Enhanced)
- **NatsPerformanceTest.java**: Enhanced NATS 負載測試、並發測試和記憶體洩漏檢測

##### `service/` - 服務層測試 (Enhanced Architecture Tests)
```
service/
├── 📄 NatsOrchestrationServiceTest.java    # 編排服務測試
├── 📁 factory/                             # 工廠模式測試 (新增)
├── 📁 impl/                                # 實作測試 (Enhanced)
│   ├── 📄 EnhancedNatsMessageServiceTest.java  # 企業級服務測試 (100+ 測試案例)
│   ├── 📄 JsonPayloadProcessorTest.java
│   ├── 📄 NatsMessageServiceImplTest.java      # 原始服務測試
│   └── 📄 RequestLogServiceImplTest.java
└── 📁 validator/                           # 驗證器測試
    └── 📄 RequestValidatorTest.java
```

##### `util/` - 工具類別測試 (新增)
- 工具函數和輔助類別的單元測試

#### Enhanced Testing Features
- **100+ 測試案例**: 全面覆蓋所有設計模式和功能
- **並發測試**: 多執行緒環境下的 Enhanced NATS 服務驗證
- **性能基準**: 回應時間、吞吐量和記憶體使用監控
- **模式測試**: Template Method、Observer、Factory 模式的專門測試
- **錯誤模擬**: 各種失敗情境和例外處理測試

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

## 🏗️ Enhanced NATS 設計模式架構

### Template Method Pattern
```
AbstractNatsMessageProcessor (抽象基類)
├── NatsRequestProcessor (請求處理專門化)
└── NatsPublishProcessor (發布處理專門化)
```

- **目的**: 定義訊息處理的標準演算法骨架，允許子類別特化特定步驟
- **實現**: 共同的驗證、日誌記錄和錯誤處理，專門化的實際 NATS 操作
- **優勢**: 程式碼重用、一致性、易於擴展

### Observer Pattern 
```
NatsEventPublisher (事件發布者)
└── 監聽器們 (各種監控、日誌、指標收集器)
```

- **目的**: 實現鬆耦合的事件驅動架構
- **實現**: 訊息處理事件的發布和訂閱
- **優勢**: 可擴展的監控、容易添加新的事件處理器

### Factory Pattern
```
MetricsFactory (指標工廠)
└── NatsMetricsSet (指標集合)
    ├── 請求計數器
    ├── 成功計數器  
    ├── 錯誤計數器
    └── 回應時間計時器
```

- **目的**: 集中管理 Micrometer 指標的創建
- **實現**: 按服務類型創建標準化的指標集合
- **優勢**: 一致的指標命名、集中配置、易於維護

### Hybrid Operations (雙模式操作)
```
HybridNatsOperations
├── NATS Core (用於請求-回應)
└── JetStream (用於發布操作)
```

- **目的**: 根據操作類型選擇最適合的 NATS 模式
- **實現**: 請求使用 NATS Core (效能)，發布使用 JetStream (可靠性)
- **優勢**: 效能和可靠性的最佳平衡

## 🎯 Enhanced 開發指導原則

### 開發工作流程 (Enhanced)
1. **設計模式優先**: 新功能應遵循已建立的 Template Method、Observer、Factory 模式
2. **測試驅動**: 每個新功能都需要對應的單元測試和整合測試
3. **指標監控**: 所有業務邏輯都應該包含適當的 Micrometer 指標
4. **事件發布**: 重要操作應發布事件以支援監控和審計
5. **錯誤處理**: 使用標準化的例外處理和錯誤回應格式

### Enhanced 檔案組織原則
- **模式分離**: 按設計模式組織程式碼 (factory/, observer/, impl/)
- **職責專門化**: Template Method 子類別各有明確的專門職責
- **事件驱動**: Observer 模式支援松耦合的功能擴展
- **指標標準化**: Factory 模式確保一致的監控指標
- **向下兼容**: 保留原始實現以支援現有集成

### Enhanced 最佳實務
- **Enterprise Patterns**: 使用企業級設計模式確保可維護性
- **Comprehensive Testing**: 100+ 測試案例覆蓋所有設計模式和邊緣情況
- **Performance Monitoring**: 內建性能基準和並發測試
- **Memory Management**: 自動檢測記憶體洩漏和資源管理
- **Event-Driven Architecture**: 使用 Observer 模式支援可擴展的功能

這個 Enhanced 結構確保了企業級的可維護性、可擴展性和高性能，同時提供全面的監控和測試覆蓋。

## 🔍 Enhanced NATS 架構變更摘要

### 新增的 Enhanced Features (v0.0.1-SNAPSHOT)

#### 設計模式實現
- **Template Method Pattern**: `AbstractNatsMessageProcessor` + 專門化子類別
- **Observer Pattern**: `NatsEventPublisher` 事件驅動架構
- **Factory Pattern**: `MetricsFactory` 集中指標管理
- **Builder Pattern**: `NatsPublishOptionsBuilder` JetStream 配置建造

#### 新增核心類別
- `EnhancedNatsMessageService.java` - 主要企業級服務
- `HybridNatsOperations.java` - 雙模式 NATS 操作
- `NatsRequestProcessor.java` - 請求專用處理器
- `NatsPublishProcessor.java` - 發布專用處理器
- `MetricsFactory.java` - Micrometer 指標工廠
- `NatsEventPublisher.java` - 事件發布者

#### Enhanced 測試套件
- **100+ 測試案例**: 全面覆蓋所有設計模式
- **並發測試**: 多執行緒環境驗證
- **性能基準**: 吞吐量和回應時間測試
- **記憶體洩漏檢測**: 自動資源管理驗證

#### 監控和可觀測性
- **Micrometer 整合**: 實時指標收集
- **成功率計算**: 動態統計分析
- **關聯 ID 追蹤**: 端到端請求追蹤
- **事件驅動監控**: Observer 模式支援的可擴展監控

### 向下相容性
- 保留原始 `NatsMessageServiceImpl` 以支援現有集成
- API 端點保持不變
- 配置格式向下相容

---

**版本**: Enhanced NATS Client v0.0.1-SNAPSHOT  
**更新日期**: 2025年8月23日  
**架構**: Template Method + Observer + Factory + Hybrid Operations  
**測試覆蓋**: 100+ 測試案例，包含企業級性能和可靠性驗證