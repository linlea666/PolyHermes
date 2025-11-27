-- 添加是否启用字段到账户表
ALTER TABLE copy_trading_accounts 
ADD COLUMN is_enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用（用于订单推送等功能的开关）' AFTER is_default;

