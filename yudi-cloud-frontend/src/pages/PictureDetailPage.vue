<template>
  <div id="pictureDetailPage">
    <!-- 主要布局容器 -->
    <div class="photo-detail-layout">
      <!-- 左侧图片预览区 -->
      <div class="photo-preview">
          <a-image 
            :src="getDetailImageUrl(picture)" 
          class="main-image"
            :preview="{
              mask: '点击预览',
              maskClassName: 'custom-preview-mask'
            }"
            @contextmenu.prevent
            @dragstart.prevent
            @selectstart.prevent
          />
      </div>

      <!-- 右侧信息栏 -->
      <aside class="photo-info-sidebar">
        <!-- 作者信息 -->
        <div class="author-info">
          <a-avatar 
            :size="50" 
            :src="picture.userVO?.userAvatar" 
            class="avatar"
          />
          <div class="details">
            <p class="name">{{ picture.userVO?.userName || '匿名用户' }}</p>
            <p class="location">{{ picture.userVO?.userProfile || '暂无个人简介' }}</p>
          </div>
          <button class="follow-btn" @click="handleFollow">
            + 关注
          </button>
        </div>

        <hr class="divider">

        <!-- 核心操作模块 -->
        <div class="action-bar">
          <!-- 下载按钮 - 仅登录用户可见 -->
          <template v-if="loginUserStore.loginUser && loginUserStore.loginUser.id">
            <button 
              class="btn btn-primary" 
              @click="doDownload" 
              :disabled="downloadLimitInfo.isLimited"
            >
              ⬇️ {{ downloadLimitInfo.isLimited ? '已达上限' : '下载' }}
            </button>
          </template>
          
          <!-- 点赞按钮 - 所有用户可见 -->
          <button 
            class="btn btn-secondary" 
            @click="handleLike"
            :disabled="likeLoading"
            :class="{ 'liked': picture.liked }"
          >
            <span class="btn-icon">{{ likeLoading ? '⏳' : (picture.liked ? '❤️' : '🤍') }}</span>
            <span>{{ picture.liked ? '已点赞' : '点赞' }}</span>
          </button>
          
          <!-- 收藏按钮 - 所有用户可见 -->
          <button 
            class="btn btn-secondary" 
            @click="handleCollect"
            :disabled="collectLoading"
            :class="{ 'collected': picture.collected }"
          >
            <span class="btn-icon">{{ collectLoading ? '⏳' : (picture.collected ? '⭐' : '☆') }}</span>
            <span>{{ picture.collected ? '已收藏' : '收藏' }}</span>
          </button>
          
          <!-- 分享按钮 - 所有用户可见 -->
          <button class="btn btn-secondary" @click="doShare">
            🔗 分享
          </button>
        </div>

        <hr class="divider">

        <!-- 图片信息 -->
        <div class="metadata">
          <h1 class="title">{{ picture.name || '未命名图片' }}</h1>
          <p class="description">
            {{ picture.introduction || '作者暂未对图片做任何描述' }}
          </p>

          <!-- 标签 -->
          <div class="metadata-section tags">
            <h3>标签</h3>
            <div v-if="picture.tags && picture.tags.length > 0" class="tags-container">
              <span 
                v-for="tag in picture.tags" 
                :key="tag" 
                class="tag"
              >
                #{{ tag }}
              </span>
            </div>
            <div v-else class="no-tags-message">
              暂无任何标签
            </div>
          </div>


          <!-- 详细信息 -->
          <div class="metadata-section">
            <h3>详细信息</h3>
            <div class="details-list">
              <div class="detail-item">
                <span class="label">分类:</span>
                <span class="value value-badge value-badge--category">{{ picture.category || '默认' }}</span>
              </div>
              <div class="detail-item">
                <span class="label">格式:</span>
                <span class="value value-badge value-badge--format">{{ picture.picFormat || '-' }}</span>
              </div>
              <div class="detail-item">
                <span class="label">尺寸:</span>
                <span class="value">{{ picture.picWidth || '-' }} x {{ picture.picHeight || '-' }} px</span>
              </div>
              <div class="detail-item">
                <span class="label">大小:</span>
                <span class="value">{{ formatSize(picture.picSize) }}</span>
              </div>
              <div class="detail-item">
                <span class="label">下载量:</span>
                <span class="value">{{ (picture as any).downloadCount || 0 }}</span>
              </div>
            </div>
          </div>

          <!-- 管理操作 -->
          <div class="metadata-section" v-if="canEdit || canDelete">
            <h3>管理操作</h3>
            <div class="action-buttons">
              <button v-if="canEdit" class="btn btn-outline" @click="doEdit">
                <EditOutlined /> 编辑
              </button>
              <button v-if="canDelete" class="btn btn-danger" @click="doDelete">
                <DeleteOutlined /> 删除
              </button>
            </div>
          </div>
        </div>
      </aside>
    </div>


    <ShareModal ref="shareModalRef" title="分享图片" :link="shareLink || ''" />
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, onBeforeUnmount, ref } from 'vue'
import { deletePictureUsingPost, getPictureVoByIdUsingGet, getPictureVOByIdPublicUsingGet, generatePictureShareLinkUsingGet, addPictureShareUsingPost, addPictureDownloadUsingPost, checkPictureDownloadLimitUsingGet, togglePictureLikeUsingPost, togglePictureCollectUsingPost } from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  ShareAltOutlined,
} from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import { downloadImage, formatSize } from '@/utils'
import ShareModal from '@/components/ShareModal.vue'
import { SPACE_PERMISSION_ENUM } from '@/constants/space.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { notifyPictureDeleted, notifyPictureLiked, notifyPictureCollected, notifyPictureUpdated, onPictureLiked, onPictureCollected, onPictureUpdated } from '@/utils/crossPageCommunication'

interface Props {
  id: string | number
}

const props = defineProps<Props>()
const picture = ref<API.PictureVO>({})
const downloadLoading = ref(false)
const likeLoading = ref(false)
const collectLoading = ref(false)
const downloadLimitInfo = ref<{
  todayDownloadCount: number
  maxDailyDownloads: number
  isLimited: boolean
  remainingDownloads: number
}>({
  todayDownloadCount: 0,
  maxDailyDownloads: 3,
  isLimited: false,
  remainingDownloads: 3
})

// 通用权限检查函数
function createPermissionChecker(permission: string) {
  return computed(() => {
    return (picture.value.permissionList ?? []).includes(permission)
  })
}

// 定义权限检查
const canEdit = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_EDIT)
const canDelete = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_DELETE)

// 登录用户状态
const loginUserStore = useLoginUserStore()

// --- 图片显示逻辑 ---
const getDetailImageUrl = (picture: API.PictureVO) => {
  // 详情页显示策略：优先原图，确保最佳质量
  return picture.url
}

// 获取图片详情
const fetchPictureDetail = async () => {
  try {
    let res
    // 根据用户登录状态选择不同的API
    if (loginUserStore.loginUser && loginUserStore.loginUser.id) {
      // 已登录用户使用完整API
      res = await getPictureVoByIdUsingGet({
        id: props.id,
      })
    } else {
      // 未登录用户使用公共API
      res = await getPictureVOByIdPublicUsingGet({
      id: props.id,
    })
    }
    
    if (res.data.code === 0 && res.data.data) {
      picture.value = res.data.data
      // 只有登录用户才检查下载限制
      if (loginUserStore.loginUser && loginUserStore.loginUser.id) {
      await checkDownloadLimit()
      }
    } else {
      message.error('获取图片详情失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('获取图片详情失败：' + e.message)
  }
}


// 处理关注功能
const handleFollow = () => {
  message.info('关注功能正在开发中，敬请期待！')
}

// 处理收藏功能
const handleFavorite = () => {
  message.info('收藏功能正在开发中，敬请期待！')
}

// 处理点赞功能
const handleLike = async () => {
  if (!picture.value.id) return
  
  // 检查用户是否已登录
  const currentUser = loginUserStore.loginUser
  if (!currentUser?.id) {
    // 显示登录提示
    message.warning({
      content: '请先登录后再点赞',
      duration: 3,
      style: {
        fontSize: '16px',
        fontWeight: 'bold'
      }
    })
    return
  }
  
  // 检查操作是否正在进行
  if (likeLoading.value) {
    message.warning('请勿频繁操作，请稍后再试')
    return
  }
  
  try {
    likeLoading.value = true
    const res = await togglePictureLikeUsingPost(picture.value.id)
    
      if (res.data.code === 0 && res.data.data) {
        const result = res.data.data as any
        picture.value.liked = result.liked
        picture.value.likeCount = result.likeCount
        message.success(picture.value.liked ? '已点赞' : '已取消点赞')
        
        // 通知其他页面更新点赞状态
        notifyPictureUpdated(picture.value.id, {
          liked: result.liked,
          likeCount: result.likeCount
        })
      } else {
      message.error(res.data.message || '操作失败')
    }
  } catch (error) {
    message.error('点赞操作失败')
  } finally {
    likeLoading.value = false
  }
}

// 处理收藏功能
const handleCollect = async () => {
  if (!picture.value.id) return
  
  // 检查用户是否已登录
  const currentUser = loginUserStore.loginUser
  if (!currentUser?.id) {
    // 显示登录提示
    message.warning({
      content: '请先登录后再收藏',
      duration: 3,
      style: {
        fontSize: '16px',
        fontWeight: 'bold'
      }
    })
    return
  }
  
  // 检查操作是否正在进行
  if (collectLoading.value) {
    message.warning('请勿频繁操作，请稍后再试')
    return
  }
  
  try {
    collectLoading.value = true
    const res = await togglePictureCollectUsingPost(picture.value.id)
    
      if (res.data.code === 0 && res.data.data) {
        const result = res.data.data as any
        picture.value.collected = result.collected
        picture.value.collectCount = result.collectCount
        message.success(picture.value.collected ? '已收藏' : '已取消收藏')
        
        // 通知其他页面更新收藏状态
        notifyPictureUpdated(picture.value.id, {
          collected: result.collected,
          collectCount: result.collectCount
        })
      } else {
      message.error(res.data.message || '操作失败')
    }
  } catch (error) {
    message.error('收藏操作失败')
  } finally {
    collectLoading.value = false
  }
}


// 跳转到图片详情页
const goToPicture = (pictureId: string | number | undefined) => {
  if (pictureId) {
    router.push(`/picture/${pictureId}`)
  }
}

// 检查下载限制状态
const checkDownloadLimit = async () => {
  if (!picture.value.id) return
  
  try {
    const res = await checkPictureDownloadLimitUsingGet(picture.value.id)
    if (res.data.code === 0 && res.data.data) {
      const data = res.data.data as {
        todayDownloadCount: number
        maxDailyDownloads: number
        isLimited: boolean
        remainingDownloads: number
      }
      downloadLimitInfo.value = {
        todayDownloadCount: data.todayDownloadCount || 0,
        maxDailyDownloads: data.maxDailyDownloads || 3,
        isLimited: data.isLimited || false,
        remainingDownloads: data.remainingDownloads || 0
      }
    }
  } catch (e: any) {
    message.error('检查下载限制失败')
  }
}

onMounted(() => {
  fetchPictureDetail()
  
  // 设置跨页面通信监听
  setupCrossPageListeners()
  
  // 监听窗口关闭事件，通知父页面刷新（仅在必要时）
  const handleBeforeUnload = () => {
    // 检查是否有数据变化需要刷新
    const hasDataChanges = localStorage.getItem('hasDataChanges') === 'true'
    if (hasDataChanges && window.opener) {
      window.opener.dispatchEvent(new CustomEvent('refreshHomePage'))
      localStorage.removeItem('hasDataChanges') // 清除标记
    }
  }
  
  window.addEventListener('beforeunload', handleBeforeUnload)
  
  // 禁用预览模态框的右键功能
  const disablePreviewRightClick = () => {
    // 监听DOM变化，当预览模态框出现时禁用右键
    const observer = new MutationObserver((mutations) => {
      mutations.forEach((mutation) => {
        if (mutation.type === 'childList') {
          mutation.addedNodes.forEach((node) => {
            if (node.nodeType === Node.ELEMENT_NODE) {
              const element = node as Element
              // 检查是否是预览模态框
              if (element.classList?.contains('ant-image-preview-wrap') || 
                  element.querySelector?.('.ant-image-preview-wrap')) {
                const previewWrap = element.classList?.contains('ant-image-preview-wrap') 
                  ? element 
                  : element.querySelector('.ant-image-preview-wrap')
                
                if (previewWrap) {
                  // 禁用右键菜单
                  previewWrap.addEventListener('contextmenu', (e) => {
                    e.preventDefault()
                    e.stopPropagation()
                    return false
                  })
                  
                  // 禁用拖拽
                  previewWrap.addEventListener('dragstart', (e) => {
                    e.preventDefault()
                    e.stopPropagation()
                    return false
                  })
                  
                  // 禁用选择
                  previewWrap.addEventListener('selectstart', (e) => {
                    e.preventDefault()
                    e.stopPropagation()
                    return false
                  })
                }
              }
            }
          })
        }
      })
    })
    
    observer.observe(document.body, {
      childList: true,
      subtree: true
    })
    
    // 清理观察器
    onBeforeUnmount(() => {
      observer.disconnect()
    })
  }
  
  disablePreviewRightClick()
  
  // 清理事件监听器
  onBeforeUnmount(() => {
    window.removeEventListener('beforeunload', handleBeforeUnload)
  })
})

const router = useRouter()

// 编辑
const doEdit = () => {
  router.push({
    path: '/add_picture',
    query: {
      id: picture.value.id,
      spaceId: picture.value.spaceId,
    },
  })
}

// 删除数据
const doDelete = async () => {
  const id = picture.value.id
  if (!id) {
    return
  }
  const res = await deletePictureUsingPost({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    
    // 标记数据有变化，需要刷新主页
    localStorage.setItem('hasDataChanges', 'true')
    
    // 使用新的跨页面通信工具通知所有相关页面
    notifyPictureDeleted(id)
    
    // 检查是否是从其他窗口打开的详情页
    if (window.opener && !window.opener.closed) {
      // 如果有父窗口且未关闭，则关闭当前窗口并让父窗口刷新
      console.log('检测到父窗口，关闭当前窗口并刷新父窗口')
      
      // 通知父窗口刷新数据
      try {
        window.opener.postMessage({
          type: 'pictureDeleted',
          pictureId: id,
          timestamp: Date.now()
        }, window.location.origin)
      } catch (error) {
        console.warn('无法向父窗口发送消息:', error)
      }
      
      // 延迟关闭窗口，确保消息发送完成
      setTimeout(() => {
        window.close()
      }, 100)
    } else {
      // 没有父窗口，正常路由跳转
      const deleteResponse = res.data.data
      if (deleteResponse?.isPublicPicture) {
        // 公共图库图片，跳转到首页
        router.push('/')
      } else if (deleteResponse?.spaceId) {
        // 私有/团队空间图片，跳转到对应空间
        router.push(`/space/${deleteResponse.spaceId}`)
      } else {
        // 兜底：跳转到我的空间
        router.push('/my_space')
      }
    }
  } else {
    message.error('删除失败')
  }
}

// 下载图片
const doDownload = async () => {
  if (!picture.value.id) {
    message.error('图片信息不完整，无法下载')
    return
  }
  
  // 检查下载限制
  if (downloadLimitInfo.value.isLimited) {
    message.warning('今日下载次数已达上限(3次)，请明天再试')
    return
  }
  
  // 防连点检查
  if (downloadLoading.value) {
    message.warning('下载正在进行中，请稍候...')
    return
  }
  
  try {
    downloadLoading.value = true
    
    // 先调用下载量统计接口
    const res = await addPictureDownloadUsingPost(picture.value.id)
    
    if (res.data.code === 0) {
      const responseData = res.data.data
      
      // 检查是否被限制
      if (responseData?.limited) {
        message.warning('今日下载次数已达上限(3次)，但图片仍可正常下载')
      } else {
        // 显示今日下载次数提示
        const todayCount = responseData?.todayDownloadCount || 1
        if (todayCount >= 2) {
          message.warning(`今日已下载${todayCount}次，剩余${3 - todayCount}次统计机会`)
        }
      }
      
      // 执行实际下载（使用原图URL）
      downloadImage(picture.value.url, picture.value.name || '图片')
      
      // 更新图片的下载量显示
      if (responseData?.downloadCount !== undefined) {
        (picture.value as any).downloadCount = responseData.downloadCount
      }
      
      // 更新下载限制状态
      await checkDownloadLimit()
      
      message.success('下载成功')
    } else {
      message.error('下载失败: ' + res.data.message)
    }
  } catch (error: any) {
    message.error('下载失败，请稍后重试')
  } finally {
    downloadLoading.value = false
  }
}

// ----- 分享操作 ----
const shareModalRef = ref()
// 分享链接
const shareLink = ref<string>()
// 分享
const doShare = async () => {
  if (!picture.value.id) return
  
  // 检查用户是否已登录
  const currentUser = loginUserStore.loginUser
  if (!currentUser?.id) {
    message.warning('请先登录后再分享')
    return
  }
  
  try {
    // 显示加载状态
    message.loading('正在生成分享链接...', 0)
    
    // 先调用分享计数接口
    const shareRes = await addPictureShareUsingPost(picture.value.id)
    
    if (shareRes.data.code === 0) {
      // 分享计数成功，再生成分享链接
      const linkRes = await generatePictureShareLinkUsingGet(picture.value.id)
      
      if (linkRes.data.code === 0) {
        // 分享链接在message字段中
        const shareLinkFromServer = linkRes.data.message
        
        // 调试日志
        console.log('分享链接API响应:', linkRes.data)
        console.log('分享链接值:', shareLinkFromServer)
        
        // 设置分享链接并打开模态框
        shareLink.value = shareLinkFromServer
        if (shareModalRef.value) {
          shareModalRef.value.openModal()
        }
        
        message.destroy() // 清除加载消息
        message.success('分享成功，链接已生成')
        
        // 更新图片的分享计数显示
        if (shareRes.data.data && shareRes.data.data.shareCount !== undefined) {
          picture.value.shareCount = shareRes.data.data.shareCount
          
          // 通知其他页面更新分享计数
          notifyPictureUpdated(picture.value.id, {
            shareCount: shareRes.data.data.shareCount
          })
        }
      } else {
        message.destroy() // 清除加载消息
        message.error('生成分享链接失败')
      }
    } else {
      message.destroy() // 清除加载消息
      message.error('分享失败: ' + shareRes.data.message)
    }
  } catch (error: any) {
    message.destroy() // 清除加载消息
    
    // 根据错误类型显示不同的提示
    if (error.message?.includes('timeout')) {
      message.error('网络超时，请检查网络连接')
    } else if (error.message?.includes('Network Error')) {
      message.error('网络错误，请稍后重试')
    } else {
      message.error('生成分享链接失败，请稍后重试')
    }
  }
}

// --- 跨页面通信监听 ---
const setupCrossPageListeners = () => {
  // 监听点赞状态变化
  onPictureLiked((event) => {
    const { pictureId, data } = event
    // 只处理当前图片的事件
    if (pictureId === picture.value.id && data?.liked !== undefined) {
      picture.value.liked = data.liked
      if (data.likeCount !== undefined) {
        picture.value.likeCount = data.likeCount
      }
      console.log('详情页收到点赞状态更新:', pictureId, data.liked)
    }
  })
  
  // 监听收藏状态变化
  onPictureCollected((event) => {
    const { pictureId, data } = event
    // 只处理当前图片的事件
    if (pictureId === picture.value.id && data?.collected !== undefined) {
      picture.value.collected = data.collected
      if (data.collectCount !== undefined) {
        picture.value.collectCount = data.collectCount
      }
      console.log('详情页收到收藏状态更新:', pictureId, data.collected)
    }
  })
  
  // 监听图片更新事件
  onPictureUpdated((event) => {
    const { pictureId, data } = event
    // 只处理当前图片的事件
    if (pictureId === picture.value.id && data) {
      if (data.liked !== undefined) {
        picture.value.liked = data.liked
      }
      if (data.collected !== undefined) {
        picture.value.collected = data.collected
      }
      if (data.likeCount !== undefined) {
        picture.value.likeCount = data.likeCount
      }
      if (data.collectCount !== undefined) {
        picture.value.collectCount = data.collectCount
      }
      if (data.shareCount !== undefined) {
        picture.value.shareCount = data.shareCount
      }
      console.log('详情页收到图片数据更新:', pictureId, data)
    }
  })
}
</script>

<style scoped>
/* --- 1. 全局样式与变量 --- */
:root {
  --primary-color: #1890ff;
  --primary-hover-color: #40a9ff;
  --text-color: #333;
  --text-secondary-color: #666;
  --bg-color: #f8f9fa;
  --border-color: #dee2e6;
  --tag-bg-color: #9ca3af;
  --white-color: #fff;
  --body-font: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
}

#pictureDetailPage {
  font-family: var(--body-font);
  margin: 0;
  background-color: var(--bg-color);
  color: var(--text-color);
  line-height: 1.6;
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}

/* --- 2. 整体布局 (移动端优先) --- */
.photo-detail-layout {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.photo-preview {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  margin-top: 60px;
}

.main-image {
  max-width: 100%;
  max-height: 80vh;
  width: auto;
  height: auto;
  border-radius: 8px;
  display: block;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.1);
  object-fit: contain;
}

.photo-info-sidebar {
  background-color: var(--white-color);
  border-radius: 12px;
  padding: 24px;
  border: 2px solid #d1d5db;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  gap: 0;
}

.divider {
  border: none;
  border-top: 2px solid #d1d5db;
  margin: 24px 0;
}

/* --- 3. 组件样式 --- */

/* 作者信息 */
.author-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 0;
}

.author-info .avatar {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  object-fit: cover;
}

.author-info .details {
  flex-grow: 1;
}

.author-info .name {
  font-weight: 600;
  font-size: 1.1em;
  margin: 0;
}

.author-info .location {
  font-size: 0.9em;
  color: var(--text-secondary-color);
  margin: 0;
}

.follow-btn {
  background: linear-gradient(180deg, #bfe7d6 0%, #9fdcc0 100%);
  border: 1px solid #86efac;
  color: #065f46;
  padding: 8px 16px;
  border-radius: 999px;
  cursor: pointer;
  font-weight: 600;
  transition: box-shadow 0.2s ease, transform 0.05s ease, background-color 0.2s ease;
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.6) inset, 0 1px 2px rgba(0,0,0,0.04);
}

.follow-btn:hover {
  background: linear-gradient(180deg, #9fdcc0 0%, #86d3b1 100%);
  color: #064e3b;
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.08);
}

.follow-btn:active {
  transform: translateY(1px);
}

/* 操作按钮 */
.action-bar {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr 1fr;
  gap: 8px;
  padding-bottom: 0;
}

.btn {
  padding: 8px 6px;
  border-radius: 8px;
  border: none;
  font-size: 0.85em;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  text-align: center;
  white-space: nowrap;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  position: relative;
  overflow: hidden;
}

.btn-primary {
  background-color: var(--primary-color);
  color: var(--white-color);
}

.btn-primary:hover:not(:disabled) {
  background-color: var(--primary-hover-color);
}

.btn-primary:disabled {
  background-color: #ccc;
  cursor: not-allowed;
}

.btn-secondary {
  background-color: var(--tag-bg-color);
  color: var(--text-color);
}

.btn-secondary:hover:not(.disabled) {
  background-color: #d3d9df;
}

.btn-secondary.disabled {
  background-color: #f5f5f5;
  color: #ccc;
  cursor: not-allowed;
  opacity: 0.6;
}

.btn-outline {
  background-color: transparent;
  border: 1px solid #c9d2dc;
  color: #334155;
}

.btn-outline:hover {
  background-color: #eef2f7;
  color: #0f172a;
}

.btn-danger {
  background-color: #fee2e2;
  color: #991b1b;
}

.btn-danger:hover {
  background-color: #fecaca;
  color: #7f1d1d;
}

/* 点赞和收藏按钮特殊样式 */
.btn.liked {
  background: linear-gradient(135deg, #ff6b6b, #ff8e8e);
  color: white;
  box-shadow: 0 2px 8px rgba(255, 107, 107, 0.3);
  transform: scale(1.02);
}

.btn.liked:hover {
  background: linear-gradient(135deg, #ff5252, #ff7979);
  box-shadow: 0 4px 12px rgba(255, 107, 107, 0.4);
  transform: scale(1.05);
}

.btn.collected {
  background: linear-gradient(135deg, #4ecdc4, #6dd5ed);
  color: white;
  box-shadow: 0 2px 8px rgba(78, 205, 196, 0.3);
  transform: scale(1.02);
}

.btn.collected:hover {
  background: linear-gradient(135deg, #26d0ce, #54c7ec);
  box-shadow: 0 4px 12px rgba(78, 205, 196, 0.4);
  transform: scale(1.05);
}

/* 点赞按钮未激活状态 */
.btn:not(.liked):not(.collected) {
  background: linear-gradient(135deg, #f8f9fa, #e9ecef);
  color: #6c757d;
  border: 1px solid #dee2e6;
}

.btn:not(.liked):not(.collected):hover {
  background: linear-gradient(135deg, #e9ecef, #dee2e6);
  color: #495057;
  transform: translateY(-1px);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
}

/* 特殊按钮的图标动画 */
.btn-icon {
  transition: transform 0.3s ease;
  display: inline-block;
}

.btn.liked:hover .btn-icon,
.btn.collected:hover .btn-icon {
  transform: scale(1.1) rotate(5deg);
}

.btn:not(.liked):not(.collected):hover .btn-icon {
  transform: scale(1.05);
}

/* 点击波纹效果 */
.btn::before {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  width: 0;
  height: 0;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 50%;
  transform: translate(-50%, -50%);
  transition: width 0.6s, height 0.6s;
  pointer-events: none;
}

.btn:active::before {
  width: 300px;
  height: 300px;
}

/* 点赞按钮的特殊波纹效果 */
.btn.liked::before {
  background: rgba(255, 255, 255, 0.4);
}

.btn.collected::before {
  background: rgba(255, 255, 255, 0.4);
}

/* 图片元数据 */
.metadata {
  padding-top: 0;
}

.metadata .title {
  font-size: 1.8em;
  font-weight: 700;
  margin: 0 0 8px 0;
}

.metadata .description {
  color: var(--text-secondary-color);
  margin-bottom: 16px;
  font-style: italic;
}

.metadata-section {
  margin-bottom: 24px;
}

.metadata-section h3 {
  font-size: 0.9em;
  font-weight: 600;
  color: var(--text-secondary-color);
  margin: 0 0 12px 0;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

/* 标签 */
.tags-container {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tags .tag {
  display: inline-block;
  background-color: #e2e8f0; /* deeper gray-blue */
  border: 1px solid #cbd5e1;
  padding: 6px 12px;
  border-radius: 16px;
  font-size: 0.9em;
  cursor: pointer;
  transition: all 0.2s ease;
  color: #3b82f6;
}

.tags .tag:hover {
  background-color: #cbd5e1; /* deeper on hover */
  color: #2563eb;
  transform: translateY(-1px);
}

.no-tags-message {
  color: var(--text-secondary-color);
  font-style: italic;
  font-size: 0.9em;
  padding: 8px 0;
}


/* 详细信息 */
.details-list {
  list-style: none;
  padding-left: 0;
  margin-top: 12px;
  font-size: 0.9em;
  color: var(--text-secondary-color);
}

.detail-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
}

.detail-item .label {
  font-weight: 600;
  color: var(--text-color);
}

.detail-item .value {
  color: var(--text-secondary-color);
}

.value-badge {
  display: inline-block;
  padding: 2px 8px;
  border: 1px solid #c4b5fd; /* deeper purple border */
  border-radius: 8px;
  background-color: #ede9fe; /* deeper purple bg */
  color: #6d28d9; /* deeper purple text */
}

/* 提升优先级，避免被 .detail-item .value 覆盖 */
.detail-item .value.value-badge {
  background-color: #ede9fe; /* match deeper base */
  border: 1px solid #c4b5fd;
  color: #6d28d9;
}

.detail-item .value.value-badge:hover {
  background-color: #ddd6fe; /* even deeper */
  color: #5b21b6;
}

/* 分类（绿色系） */
.detail-item .value.value-badge.value-badge--category {
  background-color: #dcfce7; /* deeper */
  border: 1px solid #86efac;
  color: #15803d;
}
.detail-item .value.value-badge.value-badge--category:hover {
  background-color: #bbf7d0; /* even deeper */
  color: #166534;
}

/* 格式（橙色系） */
.detail-item .value.value-badge.value-badge--format {
  background-color: #ffedd5; /* deeper */
  border: 1px solid #fdba74;
  color: #c2410c;
}
.detail-item .value.value-badge.value-badge--format:hover {
  background-color: #fed7aa; /* even deeper */
  color: #9a3412;
}

/* 管理操作 */
.action-buttons {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}



/* --- 4. 桌面端布局 (响应式断点) --- */
@media (min-width: 992px) {
  #pictureDetailPage {
    padding: 40px;
  }
  
  .photo-detail-layout {
    flex-direction: row;
    align-items: flex-start;
    gap: 40px;
  }
  
  .photo-preview {
    flex: 3;
  }
  
  .photo-info-sidebar {
    flex: 1;
    max-width: 350px;
    position: sticky;
    top: 40px;
  }
}

/* --- 6. 移动端响应式样式 --- */
@media (max-width: 768px) {
  #pictureDetailPage {
    padding: 20px;
  }
  
  .photo-detail-layout {
    flex-direction: column;
    gap: 20px;
  }
  
  .photo-preview {
    order: 1;
    margin-top: 35px;
  }
  
  .main-image {
    max-height: 60vh;
    max-width: 100%;
  }
  
  :deep(.ant-image img) {
    max-height: 60vh !important;
  }
  
  .photo-info-sidebar {
    order: 2;
    max-width: none;
    position: static;
  }
}

@media (max-width: 480px) {
  #pictureDetailPage {
    padding: 15px;
  }
  
  .photo-preview {
    margin-top: 30px;
  }
  
  .main-image {
    max-height: 50vh;
  }
  
  :deep(.ant-image img) {
    max-height: 50vh !important;
  }
  
  .photo-info-sidebar {
    padding: 16px;
  }
}

/* --- 5. 禁用图片的右键菜单、拖拽和选择 --- */
:deep(.ant-image) {
  user-select: none;
  -webkit-user-select: none;
  -moz-user-select: none;
  -ms-user-select: none;
}

:deep(.ant-image img) {
  pointer-events: none;
  user-select: none;
  -webkit-user-select: none;
  -moz-user-select: none;
  -ms-user-select: none;
  -webkit-user-drag: none;
  -khtml-user-drag: none;
  -moz-user-drag: none;
  -o-user-drag: none;
  /* 关键：控制图片尺寸和比例 */
  max-width: 100% !important;
  max-height: 80vh !important;
  width: auto !important;
  height: auto !important;
  object-fit: contain !important;
  display: block !important;
}

/* 禁用预览模态框中的右键功能 */
:deep(.ant-image-preview) {
  user-select: none;
  -webkit-user-select: none;
  -moz-user-select: none;
  -ms-user-select: none;
}

:deep(.ant-image-preview img) {
  pointer-events: none;
  user-select: none;
  -webkit-user-select: none;
  -moz-user-select: none;
  -ms-user-select: none;
  -webkit-user-drag: none;
  -khtml-user-drag: none;
  -moz-user-drag: none;
  -o-user-drag: none;
}

/* 禁用预览模态框的右键菜单 */
:deep(.ant-image-preview-wrap) {
  user-select: none;
  -webkit-user-select: none;
  -moz-user-select: none;
  -ms-user-select: none;
}

/* 自定义预览遮罩样式 */
:deep(.custom-preview-mask) {
  user-select: none;
  -webkit-user-select: none;
  -moz-user-select: none;
  -ms-user-select: none;
}
</style>