/**
 * 缓存工具类 - 用于主页性能优化
 */

export interface CacheItem<T> {
  data: T
  timestamp: number
  expiry: number
}

export interface CacheConfig {
  defaultTTL?: number // 默认缓存时间（毫秒）
  maxSize?: number    // 最大缓存条目数
}

/**
 * 内存缓存管理器
 */
class MemoryCache {
  private cache = new Map<string, CacheItem<any>>()
  private config: Required<CacheConfig>

  constructor(config: CacheConfig = {}) {
    this.config = {
      defaultTTL: config.defaultTTL || 5 * 60 * 1000, // 默认5分钟
      maxSize: config.maxSize || 100 // 默认最多100个条目
    }
  }

  /**
   * 设置缓存
   */
  set<T>(key: string, data: T, ttl?: number): void {
    const expiry = ttl || this.config.defaultTTL
    const item: CacheItem<T> = {
      data,
      timestamp: Date.now(),
      expiry
    }

    // 检查缓存大小限制
    if (this.cache.size >= this.config.maxSize) {
      // 删除最老的缓存条目
      const oldestKey = this.cache.keys().next().value
      this.cache.delete(oldestKey)
    }

    this.cache.set(key, item)
  }

  /**
   * 获取缓存
   */
  get<T>(key: string): T | null {
    const item = this.cache.get(key)
    
    if (!item) {
      return null
    }

    // 检查是否过期
    if (Date.now() - item.timestamp > item.expiry) {
      this.cache.delete(key)
      return null
    }

    return item.data
  }

  /**
   * 删除缓存
   */
  delete(key: string): boolean {
    return this.cache.delete(key)
  }

  /**
   * 清空所有缓存
   */
  clear(): void {
    this.cache.clear()
  }

  /**
   * 获取缓存大小
   */
  size(): number {
    return this.cache.size
  }

  /**
   * 清理过期缓存
   */
  cleanup(): void {
    const now = Date.now()
    for (const [key, item] of this.cache.entries()) {
      if (now - item.timestamp > item.expiry) {
        this.cache.delete(key)
      }
    }
  }
}

/**
 * LocalStorage 缓存管理器
 */
class LocalStorageCache {
  private prefix: string

  constructor(prefix = 'yudi_cache_') {
    this.prefix = prefix
  }

  /**
   * 设置本地存储缓存
   */
  set<T>(key: string, data: T, ttl = 24 * 60 * 60 * 1000): void {
    try {
      const item: CacheItem<T> = {
        data,
        timestamp: Date.now(),
        expiry: ttl
      }
      localStorage.setItem(this.prefix + key, JSON.stringify(item))
    } catch (error) {
      console.warn('LocalStorage cache set failed:', error)
    }
  }

  /**
   * 获取本地存储缓存
   */
  get<T>(key: string): T | null {
    try {
      const itemStr = localStorage.getItem(this.prefix + key)
      if (!itemStr) {
        return null
      }

      const item: CacheItem<T> = JSON.parse(itemStr)
      
      // 检查是否过期
      if (Date.now() - item.timestamp > item.expiry) {
        this.delete(key)
        return null
      }

      return item.data
    } catch (error) {
      console.warn('LocalStorage cache get failed:', error)
      return null
    }
  }

  /**
   * 删除本地存储缓存
   */
  delete(key: string): void {
    try {
      localStorage.removeItem(this.prefix + key)
    } catch (error) {
      console.warn('LocalStorage cache delete failed:', error)
    }
  }

  /**
   * 清空所有本地存储缓存
   */
  clear(): void {
    try {
      const keys = Object.keys(localStorage).filter(key => key.startsWith(this.prefix))
      keys.forEach(key => localStorage.removeItem(key))
    } catch (error) {
      console.warn('LocalStorage cache clear failed:', error)
    }
  }

  /**
   * 清理过期的本地存储缓存
   */
  cleanup(): void {
    try {
      const keys = Object.keys(localStorage).filter(key => key.startsWith(this.prefix))
      const now = Date.now()
      
      keys.forEach(key => {
        try {
          const itemStr = localStorage.getItem(key)
          if (itemStr) {
            const item: CacheItem<any> = JSON.parse(itemStr)
            if (now - item.timestamp > item.expiry) {
              localStorage.removeItem(key)
            }
          }
        } catch {
          // 清理损坏的缓存项
          localStorage.removeItem(key)
        }
      })
    } catch (error) {
      console.warn('LocalStorage cache cleanup failed:', error)
    }
  }
}

/**
 * SessionStorage 缓存管理器
 */
class SessionStorageCache {
  private prefix: string

  constructor(prefix = 'yudi_session_') {
    this.prefix = prefix
  }

  /**
   * 设置会话存储缓存
   */
  set<T>(key: string, data: T, ttl = 30 * 60 * 1000): void {
    try {
      const item: CacheItem<T> = {
        data,
        timestamp: Date.now(),
        expiry: ttl
      }
      sessionStorage.setItem(this.prefix + key, JSON.stringify(item))
    } catch (error) {
      console.warn('SessionStorage cache set failed:', error)
    }
  }

  /**
   * 获取会话存储缓存
   */
  get<T>(key: string): T | null {
    try {
      const itemStr = sessionStorage.getItem(this.prefix + key)
      if (!itemStr) {
        return null
      }

      const item: CacheItem<T> = JSON.parse(itemStr)
      
      // 检查是否过期
      if (Date.now() - item.timestamp > item.expiry) {
        this.delete(key)
        return null
      }

      return item.data
    } catch (error) {
      console.warn('SessionStorage cache get failed:', error)
      return null
    }
  }

  /**
   * 删除会话存储缓存
   */
  delete(key: string): void {
    try {
      sessionStorage.removeItem(this.prefix + key)
    } catch (error) {
      console.warn('SessionStorage cache delete failed:', error)
    }
  }

  /**
   * 清空所有会话存储缓存
   */
  clear(): void {
    try {
      const keys = Object.keys(sessionStorage).filter(key => key.startsWith(this.prefix))
      keys.forEach(key => sessionStorage.removeItem(key))
    } catch (error) {
      console.warn('SessionStorage cache clear failed:', error)
    }
  }
}

// 创建缓存实例
export const memoryCache = new MemoryCache({
  defaultTTL: 5 * 60 * 1000, // 5分钟
  maxSize: 50
})

export const localCache = new LocalStorageCache()
export const sessionCache = new SessionStorageCache()

/**
 * 缓存键值常量
 */
export const CACHE_KEYS = {
  // 分类相关
  CATEGORY_LIST: 'category_list',
  
  // 图片相关
  PICTURE_LIST: 'picture_list',
  HOT_PICTURES: 'hot_pictures',
  RECOMMEND_PICTURES: 'recommend_pictures',
  
  // 用户相关
  USER_PREFERENCES: 'user_preferences',
  VIEW_HISTORY: 'view_history',
  
  // 系统相关
  LAST_REFRESH_TIME: 'last_refresh_time',
  APP_CONFIG: 'app_config'
} as const

/**
 * 智能缓存策略函数
 */
export const cacheStrategies = {
  /**
   * 分类列表缓存策略 - 使用LocalStorage，1天过期
   */
  categoryList: {
    set: (data: string[]) => localCache.set(CACHE_KEYS.CATEGORY_LIST, data, 24 * 60 * 60 * 1000),
    get: () => localCache.get<string[]>(CACHE_KEYS.CATEGORY_LIST)
  },

  /**
   * 热门图片缓存策略 - 使用内存缓存，5分钟过期
   */
  hotPictures: {
    set: (data: API.PictureVO[]) => memoryCache.set(CACHE_KEYS.HOT_PICTURES, data, 5 * 60 * 1000),
    get: () => memoryCache.get<API.PictureVO[]>(CACHE_KEYS.HOT_PICTURES)
  },

  /**
   * 推荐图片缓存策略 - 使用SessionStorage，15分钟过期
   */
  recommendPictures: {
    set: (data: API.PictureVO[], category = 'all') => {
      const key = `${CACHE_KEYS.RECOMMEND_PICTURES}_${category}`
      sessionCache.set(key, data, 15 * 60 * 1000)
    },
    get: (category = 'all') => {
      const key = `${CACHE_KEYS.RECOMMEND_PICTURES}_${category}`
      return sessionCache.get<API.PictureVO[]>(key)
    }
  },

  /**
   * 用户偏好缓存策略 - 使用LocalStorage，7天过期
   */
  userPreferences: {
    set: (data: any) => localCache.set(CACHE_KEYS.USER_PREFERENCES, data, 7 * 24 * 60 * 60 * 1000),
    get: () => localCache.get<any>(CACHE_KEYS.USER_PREFERENCES)
  }
}

/**
 * 初始化缓存清理
 */
export const initCacheCleanup = () => {
  // 立即清理一次过期缓存
  memoryCache.cleanup()
  localCache.cleanup()
  
  // 设置定期清理（每10分钟）
  setInterval(() => {
    memoryCache.cleanup()
    localCache.cleanup()
  }, 10 * 60 * 1000)
}