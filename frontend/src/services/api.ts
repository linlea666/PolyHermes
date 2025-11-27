import axios, { AxiosInstance } from 'axios'
import type { ApiResponse } from '../types'

/**
 * API 基础配置
 */
const apiClient: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

/**
 * 请求拦截器
 */
apiClient.interceptors.request.use(
  (config) => {
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

/**
 * 响应拦截器
 */
apiClient.interceptors.response.use(
  (response) => {
    return response
  },
  (error) => {
    if (error.response) {
      console.error('API 错误:', error.response.data)
    } else if (error.request) {
      console.error('网络错误:', error.request)
    } else {
      console.error('请求错误:', error.message)
    }
    return Promise.reject(error)
  }
)

/**
 * API 服务
 */
export const apiService = {
  /**
   * 账户管理 API
   */
  accounts: {
    /**
     * 导入账户
     */
    import: (data: any) => 
      apiClient.post<ApiResponse<any>>('/copy-trading/accounts/import', data),
    
    /**
     * 更新账户
     */
    update: (data: any) => 
      apiClient.post<ApiResponse<any>>('/copy-trading/accounts/update', data),
    
    /**
     * 删除账户
     */
    delete: (data: { accountId: number }) => 
      apiClient.post<ApiResponse<void>>('/copy-trading/accounts/delete', data),
    
    /**
     * 查询账户列表
     */
    list: () => 
      apiClient.post<ApiResponse<any>>('/copy-trading/accounts/list', {}),
    
    /**
     * 查询账户详情
     */
    detail: (data: { accountId?: number }) => 
      apiClient.post<ApiResponse<any>>('/copy-trading/accounts/detail', data),
    
    /**
     * 查询账户余额
     */
    balance: (data: { accountId?: number }) => 
      apiClient.post<ApiResponse<any>>('/copy-trading/accounts/balance', data),
    
    /**
     * 设置默认账户
     */
    setDefault: (data: { accountId: number }) => 
      apiClient.post<ApiResponse<void>>('/copy-trading/accounts/set-default', data),
    
    /**
     * 查询所有账户的仓位列表
     */
    positionsList: () => 
      apiClient.post<ApiResponse<any>>('/copy-trading/accounts/positions/list', {}),
    
    /**
     * 卖出仓位
     */
    sellPosition: (data: any) => 
      apiClient.post<ApiResponse<any>>('/copy-trading/accounts/positions/sell', data),
    
    /**
     * 获取市场价格
     */
    getMarketPrice: (data: any) => 
      apiClient.post<ApiResponse<any>>('/copy-trading/accounts/markets/price', data)
  },
  
  /**
   * Leader 管理 API
   */
  leaders: {
    /**
     * 添加 Leader
     */
    add: (data: any) => 
      apiClient.post<ApiResponse<any>>('/copy-trading/leaders/add', data),
    
    /**
     * 更新 Leader
     */
    update: (data: any) => 
      apiClient.post<ApiResponse<any>>('/copy-trading/leaders/update', data),
    
    /**
     * 删除 Leader
     */
    delete: (data: { leaderId: number }) => 
      apiClient.post<ApiResponse<void>>('/copy-trading/leaders/delete', data),
    
    /**
     * 查询 Leader 列表
     */
    list: (data: { enabled?: boolean; category?: string } = {}) => 
      apiClient.post<ApiResponse<any>>('/copy-trading/leaders/list', data)
  },
  
  /**
   * 配置管理 API
   */
  config: {
    /**
     * 获取全局配置
     */
    get: () => 
      apiClient.post<ApiResponse<any>>('/copy-trading/config/get', {}),
    
    /**
     * 更新全局配置
     */
    update: (data: any) => 
      apiClient.post<ApiResponse<void>>('/copy-trading/config/update', data)
  },
  
  /**
   * 订单管理 API
   */
  orders: {
    /**
     * 查询跟单订单列表
     */
    list: (data: any) => 
      apiClient.post<ApiResponse<any>>('/copy-trading/orders/list', data),
    
    /**
     * 取消跟单订单
     */
    cancel: (data: { copyOrderId: number }) => 
      apiClient.post<ApiResponse<void>>('/copy-trading/orders/cancel', data)
  },
  
  /**
   * 统计 API
   */
  statistics: {
    /**
     * 获取全局统计
     */
    global: (data: { startTime?: number; endTime?: number } = {}) => 
      apiClient.post<ApiResponse<any>>('/copy-trading/statistics/global', data),
    
    /**
     * 获取 Leader 统计
     */
    leader: (data: { leaderId: number; startTime?: number; endTime?: number }) => 
      apiClient.post<ApiResponse<any>>('/copy-trading/statistics/leader', data),
    
    /**
     * 获取分类统计
     */
    category: (data: { category: string; startTime?: number; endTime?: number }) => 
      apiClient.post<ApiResponse<any>>('/copy-trading/statistics/category', data)
  }
}

export default apiClient

