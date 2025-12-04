# Telegram 订单通知实现方案

## 1. 架构设计

### 1.1 整体架构
```
订单处理流程
    ↓
订单成功/失败事件
    ↓
TelegramNotificationService (异步发送)
    ↓
Telegram Bot API
    ↓
用户 Telegram 客户端
```

### 1.2 核心组件
1. **TelegramNotificationService**: 负责发送 Telegram 消息
2. **TelegramConfig**: 配置类，读取 Bot Token 和 Chat ID
3. **订单处理服务集成**: 在订单成功/失败时调用通知服务

## 2. 实现方案

### 2.1 方案选择
- **方案 A（推荐）**: 使用 OkHttp 直接调用 Telegram Bot API
  - 优点：轻量级，无需额外依赖，项目已有 OkHttp
  - 缺点：需要手动处理 API 调用
  
- **方案 B**: 使用 Telegram Bot Java 库（如 `telegrambots`）
  - 优点：功能完整，支持更多特性
  - 缺点：增加依赖，可能功能过于复杂

**推荐使用方案 A**，因为：
1. 项目已有 OkHttp 依赖
2. 只需要发送消息功能，不需要接收消息
3. 减少依赖，保持项目轻量

### 2.2 消息格式设计

#### 订单成功消息
```
✅ 订单创建成功

📊 订单信息：
• 订单ID: order_123456
• 市场: Market Title
• 方向: BUY
• 价格: 0.50
• 数量: 100 USDC
• 账户: Account 1 (0x1234...5678)

⏰ 时间: 2024-01-01 12:00:00
```

#### 订单失败消息
```
❌ 订单创建失败

📊 订单信息：
• 市场: Market Title
• 方向: BUY
• 价格: 0.50
• 数量: 100 USDC
• 账户: Account 1 (0x1234...5678)

⚠️ 错误信息: 余额不足

⏰ 时间: 2024-01-01 12:00:00
```

### 2.3 集成点设计

需要在以下位置集成通知功能：

1. **CopyOrderTrackingService.createOrder()**
   - 订单创建成功时发送成功通知
   - 订单创建失败时发送失败通知

2. **AccountService.createPositionSellOrder()**
   - 卖出订单成功/失败时发送通知

3. **PolymarketClobService.createSignedOrder()**
   - 手动订单创建成功/失败时发送通知（可选）

### 2.4 异步处理
- 使用 Kotlin Coroutines 异步发送消息
- 不阻塞订单处理流程
- 发送失败不影响订单处理结果

## 3. 配置设计

### 3.1 配置文件
在 `application.properties` 中添加：

```properties
# Telegram Bot 配置
telegram.bot.enabled=true
telegram.bot.token=${TELEGRAM_BOT_TOKEN:}
telegram.bot.chat-id=${TELEGRAM_CHAT_ID:}  # 单个用户（兼容旧配置）
telegram.bot.chat-ids=${TELEGRAM_CHAT_IDS:}  # 多个用户，逗号分隔（推荐）
telegram.bot.timeout=5000
```

**配置说明**：
- `telegram.bot.chat-id`: 单个用户 Chat ID（兼容旧配置）
- `telegram.bot.chat-ids`: 多个用户 Chat ID，用逗号分隔（如：`123456789,987654321`）
- 如果同时配置了 `chat-id` 和 `chat-ids`，会同时发送给所有用户

### 3.2 功能开关
- 支持通过配置开启/关闭通知功能
- 如果未配置 Token 或 Chat ID，自动禁用通知

## 4. 错误处理

### 4.1 发送失败处理
- 记录错误日志，但不抛出异常
- 不影响订单处理流程
- 支持重试机制（可选）

### 4.2 网络超时
- 设置合理的超时时间（5秒）
- 超时后记录日志，不重试

## 5. 扩展功能（可选）

### 5.1 消息类型扩展
- 订单状态变更通知（filled, cancelled）
- 每日统计报告
- 风险告警通知

### 5.2 多用户支持
- **支持多个 Chat ID**：配置多个用户接收通知
  - 配置格式：`telegram.bot.chat-ids=123456789,987654321,111222333`
  - 所有用户都会收到相同的通知
- **按账户配置不同的通知接收者**（高级功能）
  - 在账户表中添加 `telegram_chat_id` 字段
  - 不同账户的订单通知发送给不同的用户

### 5.3 消息模板
- 支持自定义消息模板
- 支持多语言消息

## 6. 实现步骤

1. **创建 TelegramNotificationService**
   - 实现发送消息方法
   - 实现消息格式化方法

2. **创建 TelegramConfig**
   - 读取配置
   - 验证配置有效性

3. **集成到订单处理服务**
   - 在订单成功/失败时调用通知服务
   - 异步发送，不阻塞主流程

4. **添加配置项**
   - 在 application.properties 中添加配置
   - 支持环境变量

5. **测试**
   - 单元测试
   - 集成测试
   - 端到端测试

