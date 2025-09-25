/**
 * 跨页面通信工具类
 * 用于处理多窗口/多标签页之间的数据同步
 */

export interface PictureUpdateEvent {
  type: 'pictureDeleted' | 'pictureUpdated' | 'pictureLiked' | 'pictureCollected' | 'pictureUploaded'
  pictureId: number
  timestamp: number
  data?: any
}

export interface CrossPageCommunicationOptions {
  channelName?: string
  enableBroadcastChannel?: boolean
  enableLocalStorage?: boolean
  enableWindowOpener?: boolean
}

/**
 * 跨页面通信管理器
 */
export class CrossPageCommunication {
  private channelName: string
  private enableBroadcastChannel: boolean
  private enableLocalStorage: boolean
  private enableWindowOpener: boolean
  private broadcastChannel: BroadcastChannel | null = null
  private listeners: Map<string, Function[]> = new Map()

  constructor(options: CrossPageCommunicationOptions = {}) {
    this.channelName = options.channelName || 'picture-updates'
    this.enableBroadcastChannel = options.enableBroadcastChannel !== false
    this.enableLocalStorage = options.enableLocalStorage !== false
    this.enableWindowOpener = options.enableWindowOpener !== false

    this.init()
  }

  /**
   * 初始化通信机制
   */
  private init() {
    // 初始化 BroadcastChannel
    if (this.enableBroadcastChannel && typeof BroadcastChannel !== 'undefined') {
      this.broadcastChannel = new BroadcastChannel(this.channelName)
      this.broadcastChannel.addEventListener('message', this.handleBroadcastMessage.bind(this))
    }

    // 初始化 localStorage 事件监听
    if (this.enableLocalStorage) {
      window.addEventListener('storage', this.handleStorageChange.bind(this))
    }

    // 初始化 window.opener 事件监听
    if (this.enableWindowOpener) {
      window.addEventListener('message', this.handleWindowMessage.bind(this))
    }
  }

  /**
   * 发送消息到所有相关页面
   */
  public broadcast(event: PictureUpdateEvent): void {
    try {
      // 1. 通过 BroadcastChannel 发送
      if (this.broadcastChannel) {
        this.broadcastChannel.postMessage(event)
        console.log('已通过 BroadcastChannel 发送消息:', event.type)
      }

      // 2. 通过 localStorage 事件发送
      if (this.enableLocalStorage) {
        localStorage.setItem('pictureUpdateEvent', JSON.stringify(event))
        localStorage.removeItem('pictureUpdateEvent') // 立即删除，触发 storage 事件
        console.log('已通过 localStorage 事件发送消息:', event.type)
      }

      // 3. 通过 window.opener 发送（如果存在父窗口）
      if (this.enableWindowOpener && window.opener && !window.opener.closed) {
        window.opener.postMessage(event, window.location.origin)
        console.log('已通过 window.opener 发送消息:', event.type)
      }

    } catch (error) {
      console.error('发送跨页面消息失败:', error)
    }
  }

  /**
   * 监听特定类型的事件
   */
  public on(eventType: string, callback: Function): void {
    if (!this.listeners.has(eventType)) {
      this.listeners.set(eventType, [])
    }
    this.listeners.get(eventType)!.push(callback)
  }

  /**
   * 移除事件监听器
   */
  public off(eventType: string, callback?: Function): void {
    if (!this.listeners.has(eventType)) return

    if (callback) {
      const callbacks = this.listeners.get(eventType)!
      const index = callbacks.indexOf(callback)
      if (index > -1) {
        callbacks.splice(index, 1)
      }
    } else {
      this.listeners.delete(eventType)
    }
  }

  /**
   * 处理 BroadcastChannel 消息
   */
  private handleBroadcastMessage(event: MessageEvent): void {
    this.handleMessage(event.data)
  }

  /**
   * 处理 localStorage 变化事件
   */
  private handleStorageChange(event: StorageEvent): void {
    if (event.key === 'pictureUpdateEvent' && event.newValue) {
      try {
        const data = JSON.parse(event.newValue)
        this.handleMessage(data)
      } catch (error) {
        console.error('解析 localStorage 事件数据失败:', error)
      }
    }
  }

  /**
   * 处理 window.opener 消息
   */
  private handleWindowMessage(event: MessageEvent): void {
    // 验证消息来源
    if (event.origin !== window.location.origin) return
    
    this.handleMessage(event.data)
  }

  /**
   * 统一处理消息
   */
  private handleMessage(data: PictureUpdateEvent): void {
    // 检查消息时间戳，避免处理过期消息
    const now = Date.now()
    if (now - data.timestamp > 30000) { // 30秒内的消息才处理
      console.log('忽略过期消息:', data.type, data.timestamp)
      return
    }

    // 触发对应的监听器
    const callbacks = this.listeners.get(data.type) || []
    callbacks.forEach(callback => {
      try {
        callback(data)
      } catch (error) {
        console.error('执行事件回调失败:', error)
      }
    })

    // 触发通用监听器
    const allCallbacks = this.listeners.get('*') || []
    allCallbacks.forEach(callback => {
      try {
        callback(data)
      } catch (error) {
        console.error('执行通用事件回调失败:', error)
      }
    })
  }

  /**
   * 清理资源
   */
  public destroy(): void {
    if (this.broadcastChannel) {
      this.broadcastChannel.close()
      this.broadcastChannel = null
    }

    if (this.enableLocalStorage) {
      window.removeEventListener('storage', this.handleStorageChange.bind(this))
    }

    if (this.enableWindowOpener) {
      window.removeEventListener('message', this.handleWindowMessage.bind(this))
    }

    this.listeners.clear()
  }
}

/**
 * 创建全局跨页面通信实例
 */
export const crossPageComm = new CrossPageCommunication()

/**
 * 便捷方法：通知图片删除
 */
export const notifyPictureDeleted = (pictureId: number): void => {
  crossPageComm.broadcast({
    type: 'pictureDeleted',
    pictureId,
    timestamp: Date.now()
  })
}

/**
 * 便捷方法：通知图片更新
 */
export const notifyPictureUpdated = (pictureId: number, data?: any): void => {
  crossPageComm.broadcast({
    type: 'pictureUpdated',
    pictureId,
    timestamp: Date.now(),
    data
  })
}

/**
 * 便捷方法：通知图片点赞状态变化
 */
export const notifyPictureLiked = (pictureId: number, liked: boolean): void => {
  crossPageComm.broadcast({
    type: 'pictureLiked',
    pictureId,
    timestamp: Date.now(),
    data: { liked }
  })
}

/**
 * 便捷方法：通知图片上传成功
 */
export const notifyPictureUploaded = (pictureId: number, pictureData?: any): void => {
  crossPageComm.broadcast({
    type: 'pictureUploaded',
    pictureId,
    timestamp: Date.now(),
    data: pictureData
  })
}

/**
 * 便捷方法：监听图片上传事件
 */
export const onPictureUploaded = (callback: (event: PictureUpdateEvent) => void): void => {
  crossPageComm.on('pictureUploaded', callback)
}

/**
 * 便捷方法：通知图片收藏状态变化
 */
export const notifyPictureCollected = (pictureId: number, collected: boolean): void => {
  crossPageComm.broadcast({
    type: 'pictureCollected',
    pictureId,
    timestamp: Date.now(),
    data: { collected }
  })
}

/**
 * 便捷方法：监听图片删除事件
 */
export const onPictureDeleted = (callback: (event: PictureUpdateEvent) => void): void => {
  crossPageComm.on('pictureDeleted', callback)
}

/**
 * 便捷方法：监听图片更新事件
 */
export const onPictureUpdated = (callback: (event: PictureUpdateEvent) => void): void => {
  crossPageComm.on('pictureUpdated', callback)
}

/**
 * 便捷方法：监听图片点赞事件
 */
export const onPictureLiked = (callback: (event: PictureUpdateEvent) => void): void => {
  crossPageComm.on('pictureLiked', callback)
}

/**
 * 便捷方法：监听图片收藏事件
 */
export const onPictureCollected = (callback: (event: PictureUpdateEvent) => void): void => {
  crossPageComm.on('pictureCollected', callback)
}

/**
 * 便捷方法：监听所有事件
 */
export const onAnyPictureEvent = (callback: (event: PictureUpdateEvent) => void): void => {
  crossPageComm.on('*', callback)
}

/**
 * 便捷方法：移除事件监听器
 */
export const removePictureEventListener = (eventType: string, callback?: Function): void => {
  crossPageComm.off(eventType, callback)
}