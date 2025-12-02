import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  // 加载环境变量
  const env = loadEnv(mode, process.cwd(), '')
  
  // 从环境变量获取后端地址，用于开发环境代理
  // 如果未设置，使用默认值 localhost:8000
  const API_URL = env.VITE_API_URL || 'http://localhost:8000'
  const WS_URL = env.VITE_WS_URL || 'ws://localhost:8000'
  
  return {
    plugins: [react()],
    server: {
      port: 3000,
      proxy: {
        '/api': {
          target: API_URL,
          changeOrigin: true
        },
        '/ws': {
          target: WS_URL,
          ws: true,
          changeOrigin: true
        }
      }
    }
  }
})

