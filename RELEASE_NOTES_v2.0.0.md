# PolyHermes v2.0.0 Release Notes

## 🎉 重大更新

PolyHermes v2.0.0 是一个重要版本更新，带来了系统动态更新功能、优化的用户体验和多项技术改进。

---

## ✨ 新功能

### 🔄 系统动态更新（核心功能）

**无需重启 Docker 容器即可更新系统**，大幅提升部署和维护效率。

- ✅ **在线更新**：在 Web UI 中一键检查并应用更新，无需手动操作
- ✅ **零停机更新**：更新过程约 30-60 秒，系统自动处理，无需重启容器
- ✅ **自动回滚**：更新失败时自动恢复到旧版本，确保系统稳定性
- ✅ **版本管理**：清晰显示当前版本和可用更新，支持 Pre-release 版本检测
- ✅ **更新内容展示**：支持 Markdown 格式的更新说明，美观易读

**技术特性**：
- 独立的 Python Flask 更新服务（端口 9090），与主应用隔离
- 单一更新包（tar.gz），包含前后端完整更新
- 自动备份和版本管理
- 管理员权限验证，确保安全性

### 📦 Release 管理工具

- **自动化发布脚本** (`create-release.sh`)：
  - 自动创建 Git 标签
  - 发布 GitHub Release
  - 支持 Pre-release 标记
  - 自动拼接版本号后缀（Pre-release 自动添加 `-beta`）
  - 支持非交互模式，便于 CI/CD 集成

### 🎨 版本号显示优化

- **Tag 格式显示**：版本号使用 Git Tag 格式（如 `v2.0.0-beta`）
- **智能颜色提示**：
  - 🟡 **黄色 Tag**：有新版本可用（点击可跳转到系统更新页面）
  - 🟢 **绿色 Tag**：当前已是最新版本
- **镂空样式**：更小巧美观的版本号标签
- **自动检查**：系统自动检查更新，有新版本时在导航栏显示提示

---

## 🎨 UI/UX 优化

### 系统更新页面

- **美化界面**：全新的渐变背景、卡片样式和图标设计
- **Markdown 支持**：更新内容支持完整的 Markdown 渲染（标题、列表、代码块、表格等）
- **进度显示**：美观的进度条和状态提示
- **优化布局**：系统更新模块移至系统设置页面最上方，更易访问

### 版本号显示

- 使用镂空 Tag 样式，字号 8px
- 与标题垂直居中对齐
- 响应式设计，完美支持移动端和桌面端

---

## 🔧 技术改进

### 构建系统

- **修复 Docker 构建问题**：
  - 修复 `BUILD_IN_DOCKER=false` 时找不到前端产物的问题
  - 优化 `.dockerignore` 配置，确保外部构建产物可被使用
  - 修复 GitHub Actions 构建流程

- **版本号注入**：
  - 修复前端构建时版本号未正确传递的问题
  - 支持在构建时注入 Git Tag 和版本信息

- **Gradle Wrapper**：
  - 修复 GitHub Actions 构建错误
  - 正确配置 Gradle Wrapper JAR

### 代码质量

- 修复 TypeScript 编译错误
- 清理未使用的导入和组件
- 优化代码结构

---

## 📝 新增文档

- `docs/zh/DYNAMIC_UPDATE.md` - 动态更新技术方案文档
- `docs/zh/DOCKER_VERSION.md` - Docker 版本管理说明
- `docs/zh/DYNAMIC_UPDATE_CHECK.md` - 动态更新检查机制文档
- `scripts/README_RELEASE.md` - Release 脚本使用说明
- `scripts/CHANGELOG_TEMPLATE.md` - 更新日志模板

---

## 🔄 升级指南

### 从 v1.1.16 升级到 v2.0.0

#### 方式一：使用动态更新功能（推荐）

1. 登录系统，进入 **系统设置** → **系统更新**
2. 点击 **检查更新**
3. 如果有新版本，点击 **立即升级**
4. 等待更新完成（约 30-60 秒）
5. 页面会自动刷新，更新完成

#### 方式二：重新部署 Docker 容器

```bash
# 1. 停止当前容器
docker-compose -f docker-compose.prod.yml down

# 2. 拉取新版本镜像
docker pull wrbug/polyhermes:v2.0.0

# 3. 更新 docker-compose.prod.yml 中的镜像标签
# image: wrbug/polyhermes:v2.0.0

# 4. 重新启动
docker-compose -f docker-compose.prod.yml up -d
```

### 注意事项

- ⚠️ **数据备份**：虽然更新不会删除数据，但建议在更新前备份数据库
- ⚠️ **权限要求**：执行动态更新需要管理员权限
- ✅ **向后兼容**：v2.0.0 完全兼容 v1.1.16 的数据结构和配置

---

## 📊 变更统计

- **新增文件**：17 个
- **修改文件**：13 个
- **代码变更**：+5251 行，-163 行

### 主要新增文件

- `docker/update-service.py` - 更新服务（Python Flask）
- `frontend/src/pages/SystemUpdate.tsx` - 系统更新页面
- `create-release.sh` - Release 创建脚本
- `docs/zh/DYNAMIC_UPDATE.md` - 动态更新技术文档

---

## 🐛 修复的问题

- 修复 Docker 构建时找不到前端产物的问题
- 修复前端构建时版本号未正确传递的问题
- 修复 GitHub Actions 构建错误（Gradle Wrapper）
- 修复 TypeScript 编译错误

---

## 📚 相关文档

- [动态更新技术方案](docs/zh/DYNAMIC_UPDATE.md)
- [Docker 版本管理](docs/zh/DOCKER_VERSION.md)
- [部署指南](docs/zh/DEPLOYMENT.md)
- [Release 脚本使用说明](scripts/README_RELEASE.md)

---

## 🙏 致谢

感谢所有使用 PolyHermes 的用户和贡献者！

---

**下载地址**：
- Docker Hub: `wrbug/polyhermes:v2.0.0`
- GitHub Releases: [v2.0.0](https://github.com/WrBug/PolyHermes/releases/tag/v2.0.0)

