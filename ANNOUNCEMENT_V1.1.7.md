# 🎉 PolyHermes v1.1.7 发布公告

**发布日期：2026年1月7日**

---

## ✨ 新功能

### 💰 支持 Maker Rebates Program 费率

我们新增了对 Polymarket Maker Rebates Program 的支持！系统现在会自动获取并应用最新的费率，帮助您享受更优惠的交易成本。

**这意味着什么？**
- 系统会自动查询并应用最新的费率
- 所有订单（买入、卖出）都会使用正确的费率
- 无需手动配置，系统会自动处理

### 🔧 Docker 部署更灵活

现在您可以通过环境变量轻松配置日志级别，无需修改配置文件！

**新增配置项：**
- `LOG_LEVEL_ROOT` - 系统日志级别（默认：INFO）
- `LOG_LEVEL_APP` - 应用日志级别（默认：DEBUG）

只需在 `.env` 文件中添加这些配置，重启服务即可生效。

---

## 🐛 问题修复

### 修复市场价格查询问题

修复了某些市场无法正确查询价格的问题。现在系统会：
- 优先从链上查询市场价格
- 如果链上查询失败，自动降级到其他数据源
- 提高系统的稳定性和容错性

### 修复自动卖出误判问题

修复了在某些情况下系统会误判市场已卖出，导致创建错误记录的问题。现在系统会：
- 更准确地判断市场状态
- 避免误判导致的错误记录
- 提高仓位管理的准确性

---

## 📝 文档更新

### 更新联系方式

- **Telegram 群组**：https://t.me/polyhermes
  - 欢迎加入我们的 Telegram 群组，获取最新资讯和技术支持！

### 版本信息更清晰

- README 中新增了 Docker 版本徽章
- 可以一目了然地看到最新的 Docker 镜像版本

---

## 📊 本次更新统计

- **5 个提交**
- **16 个文件变更**
- **主要改进**：费率支持、错误修复、部署优化

---

## 🚀 如何升级

### Docker 部署用户（推荐）

```bash
# 1. 停止当前服务
docker-compose -f docker-compose.prod.yml down

# 2. 拉取最新镜像
docker pull wrbug/polyhermes:latest

# 3. 重新启动服务
docker-compose -f docker-compose.prod.yml up -d

# 4. 查看日志确认升级成功
docker-compose -f docker-compose.prod.yml logs -f
```

### 本地构建用户

```bash
# 1. 拉取最新代码
git pull origin dev

# 2. 切换到 v1.1.7 标签
git checkout v1.1.7

# 3. 重新构建并启动
./deploy.sh
```

---

## ⚠️ 重要提示

### 数据库迁移

**本次更新无需数据库迁移**，可以直接升级，不会影响现有数据。

### 配置变更

- 新增的日志级别配置为**可选配置**
- 如果不配置，系统会使用默认值（INFO/DEBUG）
- 不影响现有功能

---

## 🙏 感谢

感谢所有用户的支持和反馈！如果您在使用过程中遇到任何问题，欢迎：

- 📧 提交 Issue：https://github.com/WrBug/PolyHermes/issues
- 💬 加入 Telegram 群组：https://t.me/polyhermes
- 🐦 关注 Twitter：@polyhermes

---

## 📚 相关链接

- **GitHub 仓库**：https://github.com/WrBug/PolyHermes
- **完整更新日志**：https://github.com/WrBug/PolyHermes/releases/tag/v1.1.7
- **Docker Hub**：https://hub.docker.com/r/wrbug/polyhermes

---

**祝您交易顺利！** 🚀


