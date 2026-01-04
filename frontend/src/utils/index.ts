/**
 * 格式化数字，自动去除尾随零
 * @param value - 数字值（字符串或数字）
 * @param maxDecimals - 最大小数位数（默认不限制）
 * @returns 格式化后的字符串，如果值为空或无效则返回 ''
 * @example
 * formatNumber(100.00) => "100"
 * formatNumber(100.50) => "100.5"
 * formatNumber(100.55) => "100.55"
 */
export const formatNumber = (value: string | number | undefined | null, maxDecimals?: number): string => {
  if (value === undefined || value === null || value === '') {
    return ''
  }
  
  const num = typeof value === 'string' ? parseFloat(value) : value
  if (isNaN(num)) {
    return ''
  }
  
  // 如果有最大小数位数限制，先截断
  if (maxDecimals !== undefined) {
    const multiplier = Math.pow(10, maxDecimals)
    const truncated = Math.floor(num * multiplier) / multiplier
    return truncated.toFixed(maxDecimals).replace(/\.?0+$/, '')
  }
  
  // 直接转换为字符串，然后去除尾随零
  return num.toString().replace(/\.?0+$/, '')
}

/**
 * 格式化 USDC 金额
 * 最多显示 4 位小数，自动去除尾随零（截断，不四舍五入）
 * @param value - 金额值（字符串或数字）
 * @returns 格式化后的字符串，如果值为空或无效则返回 '-'
 * @example
 * formatUSDC(1.23) => "1.23"
 * formatUSDC(1.23456) => "1.2345"
 * formatUSDC(1.2) => "1.2"
 * formatUSDC(1) => "1"
 */
export const formatUSDC = (value: string | number | undefined | null): string => {
  if (value === undefined || value === null || value === '') {
    return '-'
  }
  
  const num = typeof value === 'string' ? parseFloat(value) : value
  if (isNaN(num)) {
    return '-'
  }
  
  // 使用 Math.floor 截断到4位小数（不四舍五入）
  const multiplier = Math.pow(10, 4)
  const truncated = Math.floor(num * multiplier) / multiplier
  
  // 使用 toFixed(4) 确保格式一致，然后去除尾随零和小数点
  return truncated.toFixed(4).replace(/\.?0+$/, '')
}

// 统一导出 ethers 相关工具函数
export {
  getAddressFromPrivateKey,
  getAddressFromMnemonic,
  getPrivateKeyFromMnemonic,
  isValidMnemonic,
  isValidWalletAddress,
  isValidPrivateKey
} from './ethers'

// 统一导出 auth 相关工具函数
export {
  getToken,
  setToken,
  removeToken,
  hasToken
} from './auth'

// 统一导出 version 相关工具函数
export {
  getVersionInfo,
  getVersionText,
  getGitHubTagUrl
} from './version'

