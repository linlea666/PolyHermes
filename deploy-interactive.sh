#!/bin/bash

# ========================================
# PolyHermes 交互式一键部署脚本
# ========================================
# 功能：
# - 交互式配置环境变量
# - 自动生成安全密钥
# - 使用 Docker Hub 线上镜像部署
# - 支持配置预检和回滚
# ========================================

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 打印函数
info() {
    echo -e "${GREEN}[✓]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[⚠]${NC} $1"
}

error() {
    echo -e "${RED}[✗]${NC} $1"
}

title() {
    echo -e "${CYAN}${1}${NC}"
}

# 生成随机密钥
generate_secret() {
    local length=${1:-32}
    if command -v openssl &> /dev/null; then
        openssl rand -hex $length
    else
        cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w $((length * 2)) | head -n 1
    fi
}

# 生成随机端口号（10000-60000之间）
generate_random_port() {
    echo $((10000 + RANDOM % 50001))
}

# 读取用户输入（支持默认值）
read_input() {
    local prompt="$1"
    local default="$2"
    local is_secret="$3"
    local value=""
    
    # 构建提示信息（不使用颜色，因为 read -p 可能不支持）
    local prompt_text=""
    if [ -n "$default" ]; then
        if [ "$is_secret" = "secret" ]; then
            prompt_text="${prompt} [回车自动生成]: "
        else
            prompt_text="${prompt} [默认: ${default}]: "
        fi
    else
        prompt_text="${prompt}: "
    fi
    
    # 使用 read -p 确保提示正确显示
    read -r -p "$prompt_text" value
    
    # 如果用户没有输入，使用默认值
    if [ -z "$value" ]; then
        if [ "$is_secret" = "secret" ] && [ -z "$default" ]; then
            # 自动生成密钥
            case "$prompt" in
                *JWT*)
                    value=$(generate_secret 64)
                    # 输出到 stderr，避免被捕获到返回值中
                    info "已自动生成 JWT 密钥（128字符）" >&2
                    ;;
                *管理员*|*ADMIN*)
                    value=$(generate_secret 32)
                    info "已自动生成管理员重置密钥（64字符）" >&2
                    ;;
                *加密*|*CRYPTO*)
                    value=$(generate_secret 32)
                    info "已自动生成加密密钥（64字符）" >&2
                    ;;
                *数据库密码*|*DB_PASSWORD*)
                    value=$(generate_secret 16)
                    info "已自动生成数据库密码（32字符）" >&2
                    ;;
                *)
                    value="$default"
                    ;;
            esac
        else
            value="$default"
        fi
    fi
    
    echo "$value"
}

# 检查 Docker 环境
check_docker() {
    title "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    title "  步骤 1: 环境检查"
    title "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    # 检查 Docker
    if ! command -v docker &> /dev/null; then
        error "Docker 未安装"
        echo ""
        info "请先安装 Docker："
        info "  macOS: brew install docker"
        info "  Ubuntu/Debian: apt-get install docker.io"
        info "  CentOS/RHEL: yum install docker"
        exit 1
    fi
    info "Docker 已安装: $(docker --version | head -1)"
    
    # 检查 Docker Compose
    if docker compose version &> /dev/null 2>&1; then
        info "Docker Compose 已安装: $(docker compose version)"
    elif command -v docker-compose &> /dev/null; then
        info "Docker Compose 已安装: $(docker-compose --version)"
    else
        error "Docker Compose 未安装"
        echo ""
        info "请先安装 Docker Compose："
        info "  https://docs.docker.com/compose/install/"
        exit 1
    fi
    
    # 检查 Docker 守护进程
    if ! docker info &> /dev/null; then
        error "Docker 守护进程未运行"
        info "请启动 Docker 服务："
        info "  macOS: 打开 Docker Desktop"
        info "  Linux: systemctl start docker"
        exit 1
    fi
    info "Docker 守护进程运行正常"
    
    echo ""
}

# 交互式配置收集
collect_configuration() {
    title "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    title "  步骤 2: 配置收集"
    title "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    info "💡 所有配置项均为可选，直接按回车即可使用默认值或自动生成"
    echo ""
    warn "密钥配置：回车将自动生成安全的随机密钥"
    warn "其他配置：回车将使用括号中的默认值"
    echo ""
    
    # 基础配置
    title "【基础配置】"
    echo -e "${CYAN}将配置：服务器端口、MySQL端口、时区${NC}"
    # 生成随机端口作为默认值
    DEFAULT_PORT=$(generate_random_port)
    SERVER_PORT=$(read_input "➤ 服务器端口" "$DEFAULT_PORT")
    MYSQL_PORT=$(read_input "➤ MySQL 端口（外部访问）" "3307")
    TZ=$(read_input "➤ 时区" "Asia/Shanghai")
    echo ""
    
    # 数据库配置
    title "【数据库配置】"
    echo -e "${CYAN}将配置：数据库用户名、数据库密码${NC}"
    echo -e "${YELLOW}💡 提示：密码留空将自动生成 32 字符的安全随机密码${NC}"
    DB_USERNAME=$(read_input "➤ 数据库用户名" "root")
    DB_PASSWORD=$(read_input "➤ 数据库密码" "" "secret")
    echo ""
    
    # 安全配置
    title "【安全配置】"
    echo -e "${CYAN}将配置：JWT密钥、管理员密码重置密钥、数据加密密钥${NC}"
    echo -e "${YELLOW}💡 提示：留空将自动生成高强度随机密钥（推荐）${NC}"
    JWT_SECRET=$(read_input "➤ JWT 密钥" "" "secret")
    ADMIN_RESET_PASSWORD_KEY=$(read_input "➤ 管理员密码重置密钥" "" "secret")
    CRYPTO_SECRET_KEY=$(read_input "➤ 加密密钥（用于加密 API Key）" "" "secret")
    echo ""
    
    # 日志配置
    title "【日志配置】"
    echo -e "${CYAN}将配置：Root日志级别、应用日志级别${NC}"
    echo -e "${YELLOW}可选级别: TRACE, DEBUG, INFO, WARN, ERROR, OFF${NC}"
    LOG_LEVEL_ROOT=$(read_input "➤ Root 日志级别（第三方库）" "WARN")
    LOG_LEVEL_APP=$(read_input "➤ 应用日志级别" "INFO")
    echo ""
    
    # 自动设置不需要用户输入的配置
    SPRING_PROFILES_ACTIVE="prod"
    ALLOW_PRERELEASE="false"
    GITHUB_REPO="WrBug/PolyHermes"
}

# 下载 docker-compose.prod.yml（如果不存在）
download_docker_compose_file() {
    title "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    title "  步骤 3: 获取部署配置"
    title "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    if [ -f "docker-compose.prod.yml" ]; then
        info "检测到现有 docker-compose.prod.yml，跳过下载"
        echo ""
        return 0
    fi
    
    info "正在从 GitHub 下载 docker-compose.prod.yml..."
    
    # GitHub raw 文件链接
    local compose_url="https://raw.githubusercontent.com/WrBug/PolyHermes/main/docker-compose.prod.yml"
    
    # 尝试下载
    if curl -fsSL "$compose_url" -o docker-compose.prod.yml; then
        info "docker-compose.prod.yml 下载成功"
    else
        error "docker-compose.prod.yml 下载失败"
        warn "请检查网络连接或手动下载："
        warn "  $compose_url"
        exit 1
    fi
    
    echo ""
}

# 生成 .env 文件
generate_env_file() {
    title "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    title "  步骤 4: 生成环境变量文件"
    title "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    # 备份现有 .env 文件
    if [ -f ".env" ]; then
        BACKUP_FILE=".env.backup.$(date +%Y%m%d_%H%M%S)"
        cp .env "$BACKUP_FILE"
        warn "已备份现有配置文件到: $BACKUP_FILE"
    fi
    
    # 生成新的 .env 文件
    cat > .env <<EOF
# ========================================
# PolyHermes 生产环境配置
# 生成时间: $(date '+%Y-%m-%d %H:%M:%S')
# ========================================

# ============================================
# 基础配置
# ============================================
TZ=${TZ}
SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}
SERVER_PORT=${SERVER_PORT}
MYSQL_PORT=${MYSQL_PORT}

# ============================================
# 数据库配置
# ============================================
DB_URL=jdbc:mysql://mysql:3306/polyhermes?useSSL=false&serverTimezone=UTC&characterEncoding=utf8&allowPublicKeyRetrieval=true
DB_USERNAME=${DB_USERNAME}
DB_PASSWORD=${DB_PASSWORD}

# ============================================
# 安全配置（请妥善保管）
# ============================================
JWT_SECRET=${JWT_SECRET}
ADMIN_RESET_PASSWORD_KEY=${ADMIN_RESET_PASSWORD_KEY}
CRYPTO_SECRET_KEY=${CRYPTO_SECRET_KEY}

# ============================================
# 日志配置
# ============================================
LOG_LEVEL_ROOT=${LOG_LEVEL_ROOT}
LOG_LEVEL_APP=${LOG_LEVEL_APP}

# ============================================
# 其他配置
# ============================================
ALLOW_PRERELEASE=${ALLOW_PRERELEASE}
GITHUB_REPO=${GITHUB_REPO}
EOF
    
    info "配置文件已生成: .env"
    echo ""
    
    # 显示配置摘要
    title "【配置摘要】"
    echo "  服务器端口: ${SERVER_PORT}"
    echo "  MySQL 端口: ${MYSQL_PORT}"
    echo "  时区: ${TZ}"
    echo "  数据库用户: ${DB_USERNAME}"
    echo "  数据库密码: ${DB_PASSWORD:0:8}... (已隐藏)"
    echo "  JWT 密钥: ${JWT_SECRET:0:16}... (已隐藏)"
    echo "  管理员重置密钥: ${ADMIN_RESET_PASSWORD_KEY:0:16}... (已隐藏)"
    echo "  加密密钥: ${CRYPTO_SECRET_KEY:0:16}... (已隐藏)"
    echo "  日志级别: Root=${LOG_LEVEL_ROOT}, App=${LOG_LEVEL_APP}"
    echo ""
}

# 拉取镜像
pull_images() {
    title "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    title "  步骤 5: 拉取 Docker 镜像"
    title "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    info "正在从 Docker Hub 拉取最新镜像..."
    
    # 拉取应用镜像
    if docker pull wrbug/polyhermes:latest; then
        info "应用镜像拉取成功: wrbug/polyhermes:latest"
    else
        error "应用镜像拉取失败"
        warn "可能的原因："
        warn "  1. 网络连接问题"
        warn "  2. Docker Hub 服务异常"
        warn "  3. 镜像不存在"
        exit 1
    fi
    
    # 拉取 MySQL 镜像
    if docker pull mysql:8.2; then
        info "MySQL 镜像拉取成功: mysql:8.2"
    else
        warn "MySQL 镜像拉取失败，将在启动时自动下载"
    fi
    
    echo ""
}

# 部署服务
deploy_services() {
    title "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    title "  步骤 6: 部署服务"
    title "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    # 停止现有服务
    if docker compose -f docker-compose.prod.yml ps -q 2>/dev/null | grep -q .; then
        warn "检测到正在运行的服务，正在停止..."
        docker compose -f docker-compose.prod.yml down
        info "已停止现有服务"
    fi
    
    # 启动服务
    info "正在启动服务..."
    if docker compose -f docker-compose.prod.yml up -d; then
        info "服务启动成功"
    else
        error "服务启动失败"
        error "请检查日志: docker compose -f docker-compose.prod.yml logs"
        exit 1
    fi
    
    echo ""
}

# 健康检查
health_check() {
    title "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    title "  步骤 7: 健康检查"
    title "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    
    info "等待服务启动（最多等待 60 秒）..."
    
    local max_attempts=12
    local attempt=0
    
    while [ $attempt -lt $max_attempts ]; do
        attempt=$((attempt + 1))
        
        # 检查容器状态
        if docker compose -f docker-compose.prod.yml ps | grep -q "Up"; then
            info "容器运行正常"
            
            # 检查应用是否响应
            if curl -s -o /dev/null -w "%{http_code}" http://localhost:${SERVER_PORT} | grep -q "200\|302\|401"; then
                info "应用响应正常"
                echo ""
                return 0
            fi
        fi
        
        echo -n "."
        sleep 5
    done
    
    echo ""
    warn "健康检查超时，请手动检查服务状态"
    warn "查看日志: docker compose -f docker-compose.prod.yml logs -f"
    echo ""
}

# 显示部署信息
show_deployment_info() {
    title "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    title "  部署完成！"
    title "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    
    info "访问地址: ${GREEN}http://localhost:${SERVER_PORT}${NC}"
    echo ""
    
    title "【常用命令】"
    echo "  查看服务状态: ${CYAN}docker compose -f docker-compose.prod.yml ps${NC}"
    echo "  查看日志: ${CYAN}docker compose -f docker-compose.prod.yml logs -f${NC}"
    echo "  停止服务: ${CYAN}docker compose -f docker-compose.prod.yml down${NC}"
    echo "  重启服务: ${CYAN}docker compose -f docker-compose.prod.yml restart${NC}"
    echo "  更新镜像: ${CYAN}docker pull wrbug/polyhermes:latest && docker compose -f docker-compose.prod.yml up -d${NC}"
    echo ""
    
    title "【数据库连接信息】"
    echo "  主机: ${CYAN}localhost${NC}"
    echo "  端口: ${CYAN}${MYSQL_PORT}${NC}"
    echo "  数据库: ${CYAN}polyhermes${NC}"
    echo "  用户名: ${CYAN}${DB_USERNAME}${NC}"
    echo "  密码: ${CYAN}${DB_PASSWORD}${NC}"
    echo ""
    
    warn "重要提示："
    warn "  1. 请妥善保管 .env 文件，勿提交到版本控制系统"
    warn "  2. 定期备份数据库数据（位于 Docker volume: polyhermes_mysql-data）"
    warn "  3. 生产环境建议配置反向代理（如 Nginx）并启用 HTTPS"
    echo ""
}

# 主函数
main() {
    clear
    
    echo ""
    title "========================================="
    title "   PolyHermes 交互式一键部署脚本   "
    title "========================================="
    echo ""
    
    # 执行部署流程
    check_docker
    
    # 检查是否已存在 .env 文件
    if [ -f ".env" ]; then
        echo ""
        title "【检测到现有配置】"
        info "发现已存在的 .env 配置文件"
        echo ""
        echo -ne "${YELLOW}是否使用现有配置直接更新镜像？[Y/n]: ${NC}"
        read -r use_existing
        use_existing=${use_existing:-Y}
        
        if [[ "$use_existing" =~ ^[Yy]$ ]]; then
            info "将使用现有配置，跳过配置步骤"
            echo ""
            # 从现有 .env 文件读取必要的变量
            source .env 2>/dev/null || true
        else
            warn "将重新配置，现有配置将被备份"
            echo ""
            collect_configuration
        fi
    else
        collect_configuration
    fi
    
    download_docker_compose_file
    
    # 只有在重新配置时才生成新的 .env 文件
    if [[ ! "$use_existing" =~ ^[Yy]$ ]] || [ ! -f ".env" ]; then
        generate_env_file
    fi
    
    # 确认部署
    echo ""
    title "【确认部署】"
    echo -ne "${YELLOW}是否开始部署？[Y/n]（回车默认为是）: ${NC}"
    read -r confirm
    
    # 默认为 Y，只有明确输入 n/N 才取消
    confirm=${confirm:-Y}
    if [[ "$confirm" =~ ^[Nn]$ ]]; then
        warn "部署已取消"
        exit 0
    fi
    
    echo ""
    pull_images
    deploy_services
    health_check
    show_deployment_info
    
    info "部署流程已完成！"
}

# 捕获 Ctrl+C
trap 'echo ""; warn "部署已中断"; exit 1' INT

# 运行主函数
main "$@"
