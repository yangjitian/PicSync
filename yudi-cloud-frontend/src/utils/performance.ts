/**
 * 性能监控工具
 */

export interface PerformanceMetrics {
  pageLoadTime: number
  firstContentfulPaint: number
  dataLoadTime: number
  cacheHitRate: number
}

class PerformanceMonitor {
  private metrics: Map<string, number> = new Map()
  private startTimes: Map<string, number> = new Map()
  private cacheStats = { hits: 0, misses: 0 }

  /**
   * 开始性能计时
   */
  startTiming(label: string): void {
    this.startTimes.set(label, performance.now())
  }

  /**
   * 结束性能计时
   */
  endTiming(label: string): number {
    const startTime = this.startTimes.get(label)
    if (!startTime) {
      console.warn(`Performance timing not started for: ${label}`)
      return 0
    }

    const duration = performance.now() - startTime
    this.metrics.set(label, duration)
    this.startTimes.delete(label)

    return duration
  }

  /**
   * 记录缓存命中
   */
  recordCacheHit(): void {
    this.cacheStats.hits++
  }

  /**
   * 记录缓存未命中
   */
  recordCacheMiss(): void {
    this.cacheStats.misses++
  }

  /**
   * 获取缓存命中率
   */
  getCacheHitRate(): number {
    const total = this.cacheStats.hits + this.cacheStats.misses
    return total > 0 ? (this.cacheStats.hits / total) * 100 : 0
  }

  /**
   * 获取所有性能指标
   */
  getMetrics(): PerformanceMetrics {
    return {
      pageLoadTime: this.metrics.get('pageLoad') || 0,
      firstContentfulPaint: this.metrics.get('firstContentfulPaint') || 0,
      dataLoadTime: this.metrics.get('dataLoad') || 0,
      cacheHitRate: this.getCacheHitRate()
    }
  }

  /**
   * 输出性能报告
   */
  logReport(): void {
    const metrics = this.getMetrics()
    // 性能报告仅在开发模式下输出
    if (import.meta.env.DEV) {
      console.group('🚀 性能监控报告')
      console.log(`页面加载时间: ${metrics.pageLoadTime.toFixed(2)}ms`)
      console.log(`首屏渲染时间: ${metrics.firstContentfulPaint.toFixed(2)}ms`)
      console.log(`数据加载时间: ${metrics.dataLoadTime.toFixed(2)}ms`)
      console.log(`缓存命中率: ${metrics.cacheHitRate.toFixed(2)}%`)
      console.log(`缓存统计: ${this.cacheStats.hits} 命中 / ${this.cacheStats.misses} 未命中`)
      console.groupEnd()
    }
  }

  /**
   * 清除所有统计数据
   */
  clear(): void {
    this.metrics.clear()
    this.startTimes.clear()
    this.cacheStats = { hits: 0, misses: 0 }
  }
}

// 创建全局性能监控实例
export const performanceMonitor = new PerformanceMonitor()

/**
 * 预加载关键资源
 */
export const preloadCriticalResources = () => {
  // 预加载字体
  const fontLinks = [
    'https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap'
  ]

  fontLinks.forEach(href => {
    const link = document.createElement('link')
    link.rel = 'preload'
    link.as = 'font'
    link.href = href
    link.crossOrigin = 'anonymous'
    document.head.appendChild(link)
  })
}

/**
 * 图片懒加载优化
 */
export const optimizeImageLoading = () => {
  // 创建图片预加载器
  const imagePreloader = new Map<string, HTMLImageElement>()

  return {
    /**
     * 预加载图片
     */
    preloadImage: (url: string): Promise<void> => {
      return new Promise((resolve, reject) => {
        if (imagePreloader.has(url)) {
          resolve()
          return
        }

        const img = new Image()
        img.onload = () => {
          imagePreloader.set(url, img)
          resolve()
        }
        img.onerror = reject
        img.src = url
      })
    },

    /**
     * 批量预加载图片
     */
    preloadImages: async (urls: string[]): Promise<void> => {
      const promises = urls.slice(0, 5).map(url => // 限制同时预加载5张
        this.preloadImage(url).catch(() => {}) // 忽略失败的图片
      )
      await Promise.all(promises)
    }
  }
}

/**
 * 检测网络质量
 */
export const detectNetworkQuality = (): string => {
  // @ts-ignore
  const connection = navigator.connection || navigator.mozConnection || navigator.webkitConnection
  
  if (!connection) {
    return 'unknown'
  }

  const effectiveType = connection.effectiveType
  const downlink = connection.downlink

  if (effectiveType === '4g' && downlink > 10) {
    return 'excellent'
  } else if (effectiveType === '4g' || downlink > 1.5) {
    return 'good'
  } else if (effectiveType === '3g' || downlink > 0.4) {
    return 'fair'
  } else {
    return 'poor'
  }
}

/**
 * 根据网络质量调整加载策略
 */
export const getLoadingStrategy = () => {
  const networkQuality = detectNetworkQuality()
  
  switch (networkQuality) {
    case 'excellent':
      return {
        pageSize: 16,
        cacheTime: 10 * 60 * 1000, // 10分钟
        enablePreload: true,
        imageQuality: 'high'
      }
    case 'good':
      return {
        pageSize: 12,
        cacheTime: 15 * 60 * 1000, // 15分钟
        enablePreload: true,
        imageQuality: 'medium'
      }
    case 'fair':
      return {
        pageSize: 8,
        cacheTime: 20 * 60 * 1000, // 20分钟
        enablePreload: false,
        imageQuality: 'low'
      }
    case 'poor':
      return {
        pageSize: 6,
        cacheTime: 30 * 60 * 1000, // 30分钟
        enablePreload: false,
        imageQuality: 'low'
      }
    default:
      return {
        pageSize: 12,
        cacheTime: 15 * 60 * 1000,
        enablePreload: false,
        imageQuality: 'medium'
      }
  }
}

/**
 * 防抖函数 - 优化搜索和滚动性能
 */
export const debounce = <T extends (...args: any[]) => any>(
  func: T,
  wait: number
): ((...args: Parameters<T>) => void) => {
  let timeout: NodeJS.Timeout | null = null

  return (...args: Parameters<T>) => {
    if (timeout) {
      clearTimeout(timeout)
    }
    timeout = setTimeout(() => func(...args), wait)
  }
}

/**
 * 节流函数 - 优化滚动和窗口调整性能
 */
export const throttle = <T extends (...args: any[]) => any>(
  func: T,
  limit: number
): ((...args: Parameters<T>) => void) => {
  let inThrottle = false

  return (...args: Parameters<T>) => {
    if (!inThrottle) {
      func(...args)
      inThrottle = true
      setTimeout(() => inThrottle = false, limit)
    }
  }
}