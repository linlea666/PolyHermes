#!/bin/bash

# 启动脚本：同时启动 Nginx 和后端服务

set -e

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

# 启动后端服务（后台运行）
echo "启动后端服务..."
java -jar /app/app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod} &
BACKEND_PID=$!

# 等待后端服务启动
echo "等待后端服务启动..."
for i in {1..60}; do
    if curl -f http://localhost:8000/api/health > /dev/null 2>&1; then
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

