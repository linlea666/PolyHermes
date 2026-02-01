-- 智能止盈止损系统相关表
-- V27: 创建智能止盈止损配置表、执行日志表、待处理限价单表

-- 1. 智能止盈止损配置表（账户级别配置）
CREATE TABLE smart_take_profit_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    
    -- 基础配置
    account_id BIGINT NOT NULL UNIQUE COMMENT '关联账户ID',
    enabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '总开关',
    
    -- 止盈配置
    take_profit_enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '止盈开关',
    take_profit_base_threshold DECIMAL(10, 4) NOT NULL DEFAULT 10 COMMENT '止盈基础阈值（百分比）',
    take_profit_ratio DECIMAL(10, 4) NOT NULL DEFAULT 30 COMMENT '每次止盈卖出比例（百分比）',
    take_profit_keep_ratio DECIMAL(10, 4) NOT NULL DEFAULT 20 COMMENT '保留底仓比例（百分比）',
    
    -- 止损配置
    stop_loss_enabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '止损开关',
    stop_loss_threshold DECIMAL(10, 4) NOT NULL DEFAULT -20 COMMENT '止损阈值（百分比）',
    stop_loss_ratio DECIMAL(10, 4) NOT NULL DEFAULT 100 COMMENT '止损卖出比例（百分比）',
    
    -- 流动性动态调整
    liquidity_adjust_enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '流动性调整开关',
    liquidity_danger_ratio DECIMAL(10, 4) NOT NULL DEFAULT 0.3 COMMENT '流动性危险阈值',
    liquidity_warning_ratio DECIMAL(10, 4) NOT NULL DEFAULT 1.0 COMMENT '流动性警告阈值',
    liquidity_safe_ratio DECIMAL(10, 4) NOT NULL DEFAULT 3.0 COMMENT '流动性安全阈值',
    
    -- 时间衰减配置
    time_decay_enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '时间衰减开关',
    time_decay_start_minutes INT NOT NULL DEFAULT 30 COMMENT '开始衰减的剩余时间（分钟）',
    time_decay_urgent_minutes INT NOT NULL DEFAULT 5 COMMENT '紧急阈值（分钟）',
    time_decay_critical_minutes INT NOT NULL DEFAULT 2 COMMENT '危险阈值（分钟）',
    
    -- 卖出执行策略
    use_limit_order BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否使用限价单',
    limit_order_premium DECIMAL(10, 4) NOT NULL DEFAULT 1 COMMENT '限价单溢价（百分比）',
    limit_order_wait_seconds INT NOT NULL DEFAULT 60 COMMENT '限价单等待时间（秒）',
    price_retry_enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用价格阶梯重试',
    price_retry_step DECIMAL(10, 4) NOT NULL DEFAULT 1 COMMENT '每次重试降价幅度（百分比）',
    max_price_slippage DECIMAL(10, 4) NOT NULL DEFAULT 5 COMMENT '最大价格滑点（百分比）',
    max_retry_count INT NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    
    -- 时间戳
    created_at BIGINT NOT NULL COMMENT '创建时间',
    updated_at BIGINT NOT NULL COMMENT '更新时间',
    
    INDEX idx_stp_account_id (account_id),
    INDEX idx_stp_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='智能止盈止损配置';

-- 2. 智能止盈止损执行日志表
CREATE TABLE smart_take_profit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    
    -- 关联信息
    config_id BIGINT NOT NULL COMMENT '关联配置ID',
    account_id BIGINT NOT NULL COMMENT '账户ID',
    market_id VARCHAR(100) NOT NULL COMMENT '市场ID',
    market_title VARCHAR(500) NULL COMMENT '市场名称',
    outcome VARCHAR(100) NOT NULL COMMENT '结果名称',
    outcome_index INT NULL COMMENT '结果索引',
    
    -- 触发信息
    trigger_type VARCHAR(30) NOT NULL COMMENT '触发类型：TAKE_PROFIT/STOP_LOSS/FORCED_LIQUIDITY/FORCED_TIME',
    trigger_pnl_percent DECIMAL(10, 4) NOT NULL COMMENT '触发时的盈亏比例',
    dynamic_threshold DECIMAL(10, 4) NULL COMMENT '动态阈值',
    liquidity_coef DECIMAL(10, 4) NULL COMMENT '流动性系数',
    time_coef DECIMAL(10, 4) NULL COMMENT '时间系数',
    urgency_level DECIMAL(10, 4) NULL COMMENT '紧急程度',
    
    -- 市场状态
    orderbook_bid_depth DECIMAL(20, 8) NULL COMMENT '订单簿买盘深度',
    position_value DECIMAL(20, 8) NULL COMMENT '持仓价值',
    remaining_minutes INT NULL COMMENT '市场剩余时间（分钟）',
    
    -- 执行信息
    sold_quantity DECIMAL(20, 8) NOT NULL COMMENT '卖出数量',
    sold_price DECIMAL(10, 4) NOT NULL COMMENT '卖出价格',
    sold_amount DECIMAL(20, 8) NOT NULL COMMENT '卖出金额',
    order_type VARCHAR(20) NOT NULL COMMENT '订单类型：MARKET/LIMIT',
    order_id VARCHAR(100) NULL COMMENT '订单ID',
    status VARCHAR(20) NOT NULL COMMENT '执行状态：SUCCESS/FAILED/PENDING',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    error_message VARCHAR(1000) NULL COMMENT '错误信息',
    
    -- 时间戳
    created_at BIGINT NOT NULL COMMENT '创建时间',
    
    INDEX idx_stpl_config_id (config_id),
    INDEX idx_stpl_account_id (account_id),
    INDEX idx_stpl_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='智能止盈止损执行日志';

-- 3. 待处理限价单表
CREATE TABLE pending_limit_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    
    -- 关联信息
    config_id BIGINT NOT NULL COMMENT '关联配置ID',
    account_id BIGINT NOT NULL COMMENT '账户ID',
    market_id VARCHAR(100) NOT NULL COMMENT '市场ID',
    token_id VARCHAR(100) NOT NULL COMMENT 'Token ID',
    outcome VARCHAR(100) NOT NULL COMMENT '结果名称',
    outcome_index INT NULL COMMENT '结果索引',
    
    -- 订单信息
    order_id VARCHAR(100) NOT NULL COMMENT 'Polymarket订单ID',
    quantity DECIMAL(20, 8) NOT NULL COMMENT '订单数量',
    price DECIMAL(10, 4) NOT NULL COMMENT '订单价格',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/FILLED/PARTIALLY_FILLED/CANCELLED/FAILED',
    filled_quantity DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT '已成交数量',
    
    -- 重试配置
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    max_retry_count INT NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    initial_price DECIMAL(10, 4) NOT NULL COMMENT '初始价格',
    max_slippage DECIMAL(10, 4) NOT NULL COMMENT '最大滑点',
    trigger_type VARCHAR(30) NOT NULL COMMENT '触发类型：TAKE_PROFIT/STOP_LOSS',
    
    -- 时间戳
    created_at BIGINT NOT NULL COMMENT '创建时间',
    expire_at BIGINT NOT NULL COMMENT '过期时间',
    updated_at BIGINT NOT NULL COMMENT '更新时间',
    
    INDEX idx_plo_config_id (config_id),
    INDEX idx_plo_status (status),
    INDEX idx_plo_expire_at (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='待处理限价单';
