# Telegram 机器人申请和配置指南

## 1. 申请 Telegram 机器人

### 步骤 1: 创建机器人
1. 打开 Telegram，搜索 `@BotFather`
2. 点击开始对话，发送 `/newbot` 命令
3. 按照提示设置机器人名称（例如：`Polymarket Bot`）
4. 设置机器人用户名（必须以 `bot` 结尾，例如：`polymarket_notification_bot`）
5. BotFather 会返回一个 **Bot Token**，格式类似：`123456789:ABCdefGHIjklMNOpqrsTUVwxyz`

### 步骤 2: 获取 Chat ID
有两种方式获取 Chat ID：

#### 方式 1: 通过 @userinfobot
1. 在 Telegram 中搜索 `@userinfobot`
2. 点击开始对话，它会自动返回你的 Chat ID（例如：`123456789`）

#### 方式 2: 通过 API 获取
1. 使用你的 Bot Token，访问以下 URL：
   ```
   https://api.telegram.org/bot<YOUR_BOT_TOKEN>/getUpdates
   ```
2. 向你的机器人发送一条消息（例如：`/start`）
3. 再次访问上面的 URL，在返回的 JSON 中找到 `chat.id` 字段

### 步骤 3: 配置环境变量
将 Bot Token 和 Chat ID 配置到系统环境变量或配置文件中：

```properties
# Telegram Bot 配置
telegram.bot.token=YOUR_BOT_TOKEN
telegram.bot.chat-id=YOUR_CHAT_ID
```

## 2. 测试机器人
使用 curl 测试机器人是否正常工作：

```bash
curl -X POST "https://api.telegram.org/bot<YOUR_BOT_TOKEN>/sendMessage" \
  -H "Content-Type: application/json" \
  -d '{
    "chat_id": "<YOUR_CHAT_ID>",
    "text": "测试消息"
  }'
```

如果返回 `{"ok":true,"result":{...}}`，说明配置成功。

## 3. 分享机器人给其他用户

### 3.1 分享方式
机器人创建后是公开的，可以通过以下方式分享：

1. **分享用户名**：`@your_bot_name`
2. **分享链接**：`https://t.me/your_bot_name`
3. **分享二维码**：在 Telegram 中生成机器人二维码

### 3.2 用户添加步骤
其他用户需要：
1. 点击分享的链接或搜索机器人用户名
2. 点击"开始"按钮或发送 `/start` 命令
3. 向机器人发送任意消息（如：`hello`）
4. 获取用户的 Chat ID（见下方说明）

### 3.3 获取其他用户的 Chat ID
**方式 1: 通过 @userinfobot（推荐）**
- 让用户搜索 `@userinfobot` 并开始对话
- 机器人会自动返回用户的 Chat ID

**方式 2: 通过你的机器人获取**
- 用户向你的机器人发送消息后
- 访问：`https://api.telegram.org/bot<YOUR_BOT_TOKEN>/getUpdates`
- 在返回的 JSON 中找到 `message.chat.id` 字段

**方式 3: 在代码中实现获取（高级）**
- 实现 Webhook 接收用户消息
- 从消息中提取 `chat.id`

### 3.4 多用户配置
支持多个用户接收通知，配置方式：

```properties
# 单个用户（旧方式，兼容）
telegram.bot.chat-id=123456789

# 多个用户（新方式，推荐）
telegram.bot.chat-ids=123456789,987654321,111222333
```

多个 Chat ID 用逗号分隔。

## 4. 安全建议
- **不要将 Bot Token 提交到 Git**：使用环境变量或配置文件（不提交到版本控制）
- **限制机器人权限**：只允许特定用户使用
- **定期更换 Token**：如果 Token 泄露，可以通过 BotFather 重新生成
- **保护 Chat ID**：Chat ID 是私密信息，不要公开分享

