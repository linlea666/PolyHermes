# PolyHermes

[![GitHub](https://img.shields.io/badge/GitHub-WrBug%2FPolyHermes-blue?logo=github)](https://github.com/WrBug/PolyHermes)
[![Twitter](https://img.shields.io/badge/Twitter-@quant__tr-blue?logo=twitter)](https://x.com/quant_tr)

一个功能强大的 Polymarket 预测市场跟单交易系统，支持自动化跟单、多账户管理、实时订单推送和统计分析。

## ✨ 功能特性

### 核心功能
- 🔐 **多账户管理**：支持通过私钥导入多个钱包账户，统一管理
- 👥 **Leader 管理**：添加和管理被跟单者（Leader），支持分类筛选（sports/crypto）
- 📊 **跟单配置**：灵活的跟单模板配置，支持比例跟单和固定金额跟单
- 🔄 **自动跟单**：实时监控 Leader 交易，自动复制订单（支持买入和卖出）
- 📈 **订单跟踪**：完整的订单生命周期跟踪，包括买入、卖出和匹配记录
- 📊 **统计分析**：全局统计、Leader 统计、分类统计，支持时间范围筛选
- 💼 **仓位管理**：实时查看和管理持仓，支持仓位推送
- ⚙️ **系统管理**：代理配置、API 健康检查、实时状态监控

### 技术特性
- 🌐 **WebSocket 实时推送**：订单和仓位数据实时推送
- 🔒 **安全存储**：私钥和 API 凭证加密存储
- 📱 **响应式设计**：完美支持移动端和桌面端
- 🚀 **高性能**：异步处理、并发优化
- 🛡️ **风险控制**：每日亏损限制、订单数限制、价格容忍度等

## 🏗️ 技术栈

### 后端
- **框架**: Spring Boot 3.2.0
- **语言**: Kotlin 1.9.20
- **数据库**: MySQL 8.2.0
- **ORM**: Spring Data JPA
- **数据库迁移**: Flyway
- **HTTP 客户端**: Retrofit 2.9.0 + OkHttp 4.12.0
- **WebSocket**: Spring WebSocket

### 前端
- **框架**: React 18 + TypeScript
- **构建工具**: Vite
- **UI 库**: Ant Design 5.12.0
- **HTTP 客户端**: axios
- **状态管理**: Zustand
- **路由**: React Router 6
- **以太坊库**: ethers.js 6.9.0

## 📦 项目结构

```
polymarket-bot/
├── backend/                 # 后端服务
│   ├── src/main/kotlin/    # Kotlin 源代码
│   │   ├── api/            # Polymarket API 接口定义
│   │   ├── controller/     # REST 控制器
│   │   ├── service/        # 业务逻辑服务
│   │   ├── entity/         # 数据库实体
│   │   ├── repository/     # 数据访问层
│   │   ├── dto/            # 数据传输对象
│   │   ├── websocket/      # WebSocket 处理
│   │   └── util/           # 工具类
│   └── src/main/resources/
│       ├── application.properties
│       └── db/migration/   # 数据库迁移脚本
├── frontend/                # 前端应用
│   ├── src/
│   │   ├── components/     # 公共组件
│   │   ├── pages/          # 页面组件
│   │   ├── services/       # API 服务
│   │   ├── types/          # TypeScript 类型
│   │   └── utils/          # 工具函数
│   └── package.json
├── docs/                    # 文档
└── README.md
```

## 🚀 快速开始

### 前置要求

- JDK 17+
- Node.js 18+
- MySQL 8.0+
- Gradle 7.5+（或使用 Gradle Wrapper）

### 开发环境

1. **克隆仓库**

```bash
git clone https://github.com/WrBug/PolyHermes.git
cd PolyHermes
```

2. **配置数据库**

创建 MySQL 数据库：

```sql
CREATE DATABASE polymarket_bot CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

3. **启动后端**

```bash
cd backend
./gradlew bootRun
```

后端服务将在 `http://localhost:8000` 启动（开发环境）。

4. **启动前端**

```bash
cd frontend
npm install
npm run dev
```

前端应用将在 `http://localhost:3000` 启动。

### 生产部署

详细的部署文档请参考：[部署文档](docs/DEPLOYMENT.md)

#### 快速部署

**一体化部署（推荐）** - 前后端一起部署到一个容器：
```bash
# 在项目根目录
./deploy.sh
```

**分别部署**:

后端（Java 方式）:
```bash
cd backend
./deploy.sh java
```

后端（Docker 方式）:
```bash
cd backend
./deploy.sh docker
```

前端:
```bash
cd frontend
# 使用默认后端地址（相对路径）
./build.sh

# 或指定自定义后端地址（跨域场景）
./build.sh --api-url http://your-backend-server.com:8000
```

## 📖 使用指南

### 账户管理

1. 进入"账户管理"页面
2. 点击"导入账户"
3. 输入私钥（支持私钥字符串、助记词或 Keystore 文件）
4. 系统自动推导钱包地址并验证
5. 配置账户名称和是否默认账户

### 跟单配置

1. 进入"Leader 管理"，添加被跟单者（Leader）地址
2. 进入"跟单模板"，创建跟单模板（配置跟单比例、风险控制等）
3. 进入"跟单配置"，将账户、模板和 Leader 关联
4. 启用跟单关系，系统将自动开始跟单

### 查看统计

- **全局统计**：查看所有跟单关系的汇总统计
- **Leader 统计**：查看特定 Leader 的统计信息
- **分类统计**：按分类（sports/crypto）查看统计
- **跟单关系统计**：查看单个跟单关系的详细统计

## 🔧 配置说明

### 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `DB_USERNAME` | 数据库用户名 | `root` |
| `DB_PASSWORD` | 数据库密码 | - |
| `SERVER_PORT` | 后端服务端口 | `8000` |
| `POLYGON_RPC_URL` | Polygon RPC 地址 | `https://polygon-rpc.com` |
| `JWT_SECRET` | JWT 密钥 | - |

### 代理配置

系统支持通过 Web UI 配置 HTTP 代理，无需修改环境变量：

1. 进入"系统管理"页面
2. 配置代理主机、端口、用户名和密码
3. 启用代理并测试连接
4. 配置实时生效，无需重启服务

## 📚 文档

- [部署文档](docs/DEPLOYMENT.md) - 详细的部署指南（Java/Docker）
- [跟单系统需求文档](docs/copy-trading-requirements.md) - 后端 API 接口文档
- [前端需求文档](docs/copy-trading-frontend-requirements.md) - 前端功能文档

## 🤝 贡献

欢迎贡献代码！请遵循以下步骤：

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 📝 开发规范

- **后端**: 遵循 Kotlin 编码规范，使用 Spring Boot 最佳实践
- **前端**: 遵循 TypeScript 和 React 最佳实践
- **提交信息**: 使用清晰的提交信息，遵循 [Conventional Commits](https://www.conventionalcommits.org/)

详细开发规范请参考：
- [后端开发规范](.cursor/rules/backend.mdc)
- [前端开发规范](.cursor/rules/frontend.mdc)

## ⚠️ 免责声明

本软件仅供学习和研究使用。使用本软件进行交易的风险由用户自行承担。作者不对任何交易损失负责。

## 📄 许可证

本项目采用 MIT 许可证。详情请参阅 [LICENSE](LICENSE) 文件。

## 🔗 相关链接

- [GitHub 仓库](https://github.com/WrBug/PolyHermes)
- [Twitter](https://x.com/quant_tr)
- [Polymarket 官网](https://polymarket.com)
- [Polymarket API 文档](https://docs.polymarket.com)

## 🙏 致谢

感谢所有为本项目做出贡献的开发者和用户！

---

**⭐ 如果这个项目对你有帮助，请给个 Star！**

