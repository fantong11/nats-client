# NATS 請求-響應追蹤使用示例（使用 Payload ID 匹配）

## 完整工作流程

### 1. 發布請求並開始追蹤響應

**POST** `/api/nats/publish`

```json
{
  "subject": "requests.user.create",
  "payload": {
    "userId": "12345",
    "name": "John Doe",
    "email": "john@example.com",
    "department": "Engineering"
  },
  "responseSubject": "responses.user.create",
  "responseIdField": "userId"
}
```

**系統自動處理：**
- 生成唯一的 requestId（如：`REQ-123e4567-e89b-12d3-a456-426614174000`）
- **保持 payload 不變** - 不注入額外字段
- 提取 payload 中指定的 ID 字段值（`userId: "12345"`）用於後續匹配
- 發送的消息保持原樣：

```json
{
  "userId": "12345",
  "name": "John Doe",
  "email": "john@example.com",
  "department": "Engineering"
}
```

**響應：**
```json
{
  "requestId": "REQ-123e4567-e89b-12d3-a456-426614174000",
  "status": "PUBLISHED",
  "message": "Message published successfully, use trackingUrl to check status",
  "subject": "requests.user.create",
  "trackingUrl": "/api/nats/status/REQ-123e4567-e89b-12d3-a456-426614174000",
  "timestamp": "2024-01-01T10:00:00Z"
}
```

### 2. 系統自動處理

當你發布請求時，系統會：
1. 生成唯一的 requestId 用於追蹤
2. 在 `nats_request_log` 表中插入一筆記錄，狀態為 `PENDING`
3. 提取 payload 中的指定 ID 字段（例如：`userId: "12345"`）
4. 自動啟動監聽器監聽 `responses.user.create` 主題
5. 等待外部系統的響應消息

### 3. 外部系統處理並響應

**外部系統收到的請求（payload 保持不變）：**
```json
{
  "userId": "12345",
  "name": "John Doe",
  "email": "john@example.com",
  "department": "Engineering"
}
```

**外部系統處理完成後，向 `responses.user.create` 發布響應（必須包含相同的 userId）：**

```json
{
  "userId": "12345",
  "success": true,
  "data": {
    "name": "John Doe",
    "email": "john@example.com",
    "status": "created",
    "createdAt": "2024-01-01T10:00:05Z"
  },
  "message": "User created successfully"
}
```

### 4. 系統自動關聯響應

監聽器接收到響應後：
1. 提取響應中的指定 ID 字段（`userId: "12345"`）
2. 在資料庫中找到狀態為 PENDING 且 payload 中包含相同 userId 的請求記錄
3. 更新該記錄的 `response_payload` 和 `status` 字段為 SUCCESS
4. 設置 `response_timestamp`

### 5. 用戶查詢結果

**GET** `/api/nats/status/REQ-123e4567-e89b-12d3-a456-426614174000`

**響應：**
```json
{
  "requestId": "REQ-123e4567-e89b-12d3-a456-426614174000",
  "subject": "requests.user.create",
  "status": "SUCCESS",
  "requestTimestamp": "2024-01-01T10:00:00",
  "responseTimestamp": "2024-01-01T10:00:05",
  "retryCount": 0,
  "errorMessage": null
}
```

## 數據庫表結構

`nats_request_log` 表會包含：

| 字段 | 值 | 說明 |
|------|-----|------|
| request_id | REQ-123e4567-e89b-12d3-a456-426614174000 | 請求唯一標識（也是 correlationId）|
| subject | requests.user.create | 發布的主題 |
| request_payload | {"name": "John Doe", ..., "correlationId": "REQ-..."} | 增強後的請求負載（包含 correlationId）|
| response_payload | {"correlationId": "REQ-...", "success": true, ...} | 響應負載（包含 correlationId）|
| status | SUCCESS | 請求狀態 |
| request_timestamp | 2024-01-01T10:00:00 | 請求時間 |
| response_timestamp | 2024-01-01T10:00:05 | 響應時間 |

## 監聽器管理

### 查看活躍監聽器

**GET** `/api/nats/listeners/status`

```json
[
  {
    "listenerId": "listener-789abc12-def3-45gh-6789-ijklmnopqrst",
    "subject": "responses.user.create",
    "idField": "requestId",
    "status": "ACTIVE",
    "messagesReceived": 42,
    "startTime": "2024-01-01T09:00:00Z",
    "lastMessageTime": "2024-01-01T10:00:05Z"
  }
]
```

### 手動管理監聽器（可選）

如果需要手動控制監聽器：

**啟動監聽器：**
```json
POST /api/nats/listeners/start
{
  "subject": "responses.order.process",
  "idField": "orderId"
}
```

**停止監聽器：**
```
POST /api/nats/listeners/{listenerId}/stop
```

## 使用場景

### 場景 1：用戶創建
1. 前端調用 `/publish` 發送用戶創建請求
2. 後端服務處理用戶創建，發送響應到 response subject
3. 前端定期調用 `/status/{requestId}` 查看是否完成

### 場景 2：批量處理
1. 發送多個請求，每個都有獨立的 requestId
2. 系統並行處理所有請求
3. 用戶可以分別查詢每個請求的狀態

### 場景 3：長時間運行的任務
1. 發送複雜任務請求
2. 任務可能需要幾分鐘或更長時間
3. 系統會在任務完成時自動更新狀態
4. 用戶按需查詢，不需要維持長連接

## 錯誤處理

如果響應中沒有找到對應的 requestId，系統會記錄警告日誌，但不會影響其他請求的處理。

如果長時間沒有響應，可以手動標記請求為失敗或超時（需要額外的超時處理機制）。