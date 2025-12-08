#!/bin/bash

# 启动脚本：同时启动 Nginx 和后端服务

set -e

# 默认值常量
DEFAULT_JWT_SECRET="change-me-in-production"
DEFAULT_ADMIN_RESET_KEY="change-me-in-production"

# 检查安全配置
check_security_config() {
    local errors=0
    
    # 检查 JWT_SECRET
    if [ -z "$JWT_SECRET" ] || [ "$JWT_SECRET" = "$DEFAULT_JWT_SECRET" ]; then
        echo "❌ 错误: JWT_SECRET 不能使用默认值 '${DEFAULT_JWT_SECRET}'"
        echo "   请设置环境变量 JWT_SECRET 为安全的随机字符串"
        errors=$((errors + 1))
    fi
    
    # 检查 ADMIN_RESET_PASSWORD_KEY
    if [ -z "$ADMIN_RESET_PASSWORD_KEY" ] || [ "$ADMIN_RESET_PASSWORD_KEY" = "$DEFAULT_ADMIN_RESET_KEY" ]; then
        echo "❌ 错误: ADMIN_RESET_PASSWORD_KEY 不能使用默认值 '${DEFAULT_ADMIN_RESET_KEY}'"
        echo "   请设置环境变量 ADMIN_RESET_PASSWORD_KEY 为安全的随机字符串"
        errors=$((errors + 1))
    fi
    
    if [ $errors -gt 0 ]; then
        echo ""
        echo "⚠️  安全配置检查失败，容器将不会启动"
        echo "   请在 docker-compose.yml 或 .env 文件中设置正确的值"
        exit 1
    fi
    
    echo "✅ 安全配置检查通过"
}

# 执行安全配置检查
check_security_config

# 函数：清理进程
cleanup() {
    echo "收到退出信号，清理进程..."
    if [ -n "$BACKEND_PID" ]; then
        kill $BACKEND_PID 2>/dev/null || true
    fi
    nginx -s quit 2>/dev/null || true
    exit 0
}

# 注册信号处理
trap cleanup SIGTERM SIGINT

# 启动后端服务（以 appuser 用户运行，后台运行）
echo "启动后端服务..."
# 自动使用系统时区
java -jar /app/app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod} &
BACKEND_PID=$!

# 等待后端服务启动
echo "等待后端服务启动..."
for i in {1..60}; do
    if curl -f http://localhost:8000/api/system/health > /dev/null 2>&1; then
        echo "后端服务已启动"
        break
    fi
    if [ $i -eq 60 ]; then
        echo "后端服务启动超时"
        exit 1
    fi
    sleep 1
done

# 启动 Nginx（前台运行，作为主进程）
echo "启动 Nginx..."
exec nginx -g "daemon off;"

