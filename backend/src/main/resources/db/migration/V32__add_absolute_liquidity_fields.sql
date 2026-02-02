-- 添加绝对流动性检查字段到智能止盈止损配置表
-- 绝对流动性按市场买盘深度(USDC)判断，不受持仓大小影响

ALTER TABLE smart_take_profit_configs
    ADD COLUMN liquidity_absolute_enabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否启用绝对流动性检查',
    ADD COLUMN liquidity_absolute_danger DECIMAL(20,2) NOT NULL DEFAULT 300 COMMENT '绝对流动性危险阈值(USDC)',
    ADD COLUMN liquidity_absolute_warning DECIMAL(20,2) NOT NULL DEFAULT 1000 COMMENT '绝对流动性警告阈值(USDC)',
    ADD COLUMN liquidity_absolute_safe DECIMAL(20,2) NOT NULL DEFAULT 3000 COMMENT '绝对流动性安全阈值(USDC)',
    ADD COLUMN liquidity_absolute_force_on_loss BOOLEAN NOT NULL DEFAULT TRUE COMMENT '亏损时是否也强制卖出';
