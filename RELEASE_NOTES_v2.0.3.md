# PolyHermes v2.0.3 Release Notes

## 📋 版本信息
- **版本号**: v2.0.3
- **发布日期**: 2026-01-31
- **基础版本**: v2.0.2

## 🎯 改动摘要

本次版本主要优化了用户界面显示，包括数值格式化、Leader列表优化、移除不必要的配置项，提升了用户体验。

---

## ✨ 新功能

### 1. 为所有数值显示添加千分位分隔符

**功能描述**：
- ✅ 重构 `formatNumber` 和 `formatUSDC` 函数，默认添加千分位分隔符
- ✅ 所有数值（金额、数量、价格等）现在默认显示千分位
- ✅ 自动去除尾随零，提升可读性
- ✅ 示例：`1234567.89` 显示为 `1,234,567.89`

**影响范围**：
- `frontend/src/utils/index.ts` - 工具函数
- `frontend/src/pages/Statistics.tsx` - 统计页面
- `frontend/src/pages/CopyTradingStatistics.tsx` - 跟单统计页面
- `frontend/src/pages/PositionList.tsx` - 持仓列表
- `frontend/src/pages/AccountList.tsx` - 账户列表

**提交**: 40081c2

---

### 2. 在创建跟单配置时显示Leader资产信息

**功能描述**：
- ✅ 选择Leader后自动获取并显示资产信息
- ✅ 显示总资产、可用余额、仓位资产
- ✅ 使用Card和Statistic组件美观展示
- ✅ 支持中英文多语言
- ✅ 使用formatUSDC格式化显示金额

**影响范围**：
- `frontend/src/pages/CopyTradingOrders/AddModal.tsx` - 添加跟单配置弹窗
- `frontend/src/pages/CopyTradingOrders/EditModal.tsx` - 编辑跟单配置弹窗
- `frontend/src/locales/**/common.json` - 多语言文件

**提交**: 390b3ee

---

### 3. Leader列表显示仓位资产

**功能描述**：
- ✅ Leader列表新增仓位资产显示
- ✅ 显示Leader的总资产、可用余额、仓位资产
- ✅ 优化资产信息展示方式

**影响范围**：
- `frontend/src/pages/LeaderList.tsx` - Leader列表页面
- `backend/src/main/kotlin/com/wrbug/polymarketbot/dto/LeaderDto.kt` - Leader DTO
- `backend/src/main/kotlin/com/wrbug/polymarketbot/service/copytrading/leaders/LeaderService.kt` - Leader服务

**提交**: 3350039

---

## 🔧 优化改进

### 1. Leader列表优化

**改进内容**：
- ✅ 后端过滤价值为0的仓位
- ✅ 持仓列表显示市场名称而非ID
- ✅ 列表移除分类和创建时间列
- ✅ 文案'跟单关系数'改为'跟单数'
- ✅ 持仓DTO添加title字段

**影响范围**：
- `frontend/src/pages/LeaderList.tsx` - Leader列表页面
- `backend/src/main/kotlin/com/wrbug/polymarketbot/service/copytrading/leaders/LeaderService.kt` - Leader服务
- `backend/src/main/kotlin/com/wrbug/polymarketbot/service/common/BlockchainService.kt` - 区块链服务

**提交**: 0bdc0c7

---

### 2. 列表只显示可用余额

**改进内容**：
- ✅ 账户列表和Leader列表只显示可用余额
- ✅ 简化界面，减少信息冗余

**影响范围**：
- `frontend/src/pages/AccountList.tsx` - 账户列表
- `backend/src/main/kotlin/com/wrbug/polymarketbot/service/accounts/AccountService.kt` - 账户服务

**提交**: 17eea01

---

### 3. 移除仓位资产列

**改进内容**：
- ✅ 移除不必要的仓位资产列显示
- ✅ 简化界面布局

**影响范围**：
- `frontend/src/pages/PositionList.tsx` - 持仓列表

**提交**: 6980781

---

## 🗑️ 移除功能

### 移除跟单最大仓位数量(maxPositionCount)配置

**移除原因**：
- 该配置项使用频率低，且增加了系统复杂度
- 简化跟单配置，提升用户体验

**移除内容**：
- ✅ 数据库：创建迁移文件 V26 删除 `max_position_count` 字段
- ✅ 后端：移除实体类、DTO、服务中的 `maxPositionCount` 相关代码
- ✅ 后端：移除 `FilterResult` 中的 `FAILED_MAX_POSITION_COUNT` 状态
- ✅ 后端：移除 `CopyTradingFilterService` 中的最大仓位数量检查逻辑
- ✅ 前端：移除类型定义、表单字段和国际化翻译
- ✅ 前端：移除过滤订单列表中的 `MAX_POSITION_COUNT` 类型

**影响范围**：
- `backend/src/main/resources/db/migration/V26__remove_max_position_count.sql` - 数据库迁移
- `backend/src/main/kotlin/com/wrbug/polymarketbot/entity/CopyTrading.kt` - 实体类
- `backend/src/main/kotlin/com/wrbug/polymarketbot/dto/CopyTradingDto.kt` - DTO
- `backend/src/main/kotlin/com/wrbug/polymarketbot/service/copytrading/configs/CopyTradingFilterService.kt` - 过滤服务
- `frontend/src/pages/CopyTradingOrders/AddModal.tsx` - 添加表单
- `frontend/src/pages/CopyTradingOrders/EditModal.tsx` - 编辑表单
- `frontend/src/types/index.ts` - 类型定义

**提交**: e8fd1b5

---

## 🐛 Bug 修复

### 修复TypeScript类型错误

**修复内容**：
- ✅ 修复编译时的TypeScript类型错误
- ✅ 修复Spin导入问题
- ✅ 修复Table fixed类型问题
- ✅ 修复size类型问题

**影响范围**：
- `frontend/src/pages/LeaderList.tsx` - Leader列表页面

**提交**: 8097660

---

## ⚠️ 潜在问题和注意事项

### 1. 数值格式化变更

**影响**：
- 所有数值现在默认显示千分位分隔符
- 如果之前有代码依赖特定的数值格式，可能需要调整

**建议**：
- 检查是否有代码依赖特定的数值格式
- 确认数值显示是否符合预期

### 2. 移除maxPositionCount配置

**影响**：
- 如果之前使用了最大仓位数量限制功能，升级后将不再可用
- 需要手动调整跟单策略

**建议**：
- 升级前检查是否有跟单配置使用了最大仓位数量限制
- 如有需要，可以手动调整跟单策略

### 3. Leader列表显示变更

**影响**：
- Leader列表现在只显示价值大于0的仓位
- 列表布局和显示内容有所调整

**建议**：
- 升级后检查Leader列表显示是否符合预期
- 确认仓位信息是否正确显示

---

## 📊 文件变更统计

- **修改文件数**: 28
- **新增行数**: 1490
- **删除行数**: 897

---

## 🔄 升级建议

1. **检查数值显示**：
   - 升级后检查所有数值显示是否符合预期
   - 确认千分位分隔符显示正确

2. **检查跟单配置**：
   - 如果有使用最大仓位数量限制的配置，需要手动调整
   - 确认跟单功能正常工作

3. **检查Leader列表**：
   - 升级后检查Leader列表显示是否正确
   - 确认仓位信息是否完整

4. **数据库迁移**：
   - 升级时会自动执行数据库迁移 V26
   - 迁移会删除 `max_position_count` 字段
   - 建议在升级前备份数据库

---

## 📝 完整提交列表

- 40081c2 - feat: 为所有数值显示添加千分位分隔符
- e8fd1b5 - 移除跟单最大仓位数量(maxPositionCount)配置
- 390b3ee - feat: 在创建跟单配置时显示Leader资产信息
- 8097660 - fix: 修复TypeScript类型错误
- 17eea01 - refactor: 列表只显示可用余额
- 6980781 - refactor: 移除仓位资产列
- 3350039 - feat: Leader列表显示仓位资产
- 0bdc0c7 - feat: Leader列表优化

---

## 🙏 致谢

感谢所有贡献者和用户的支持与反馈！

