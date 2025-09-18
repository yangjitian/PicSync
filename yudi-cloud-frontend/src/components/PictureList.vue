<template>
  <div v-show="dataList.length > 0" ref="grid" class="picture-list-masonry" data-picture-list>
    <!-- Sizer element for column width -->
    <div class="grid-sizer"></div>
    <div v-for="picture in dataList" :key="picture.id" class="grid-item">
      <a-card hoverable class="picture-card" :data-picture-id="picture.id" @click="doClickPicture(picture)">
        <template #cover>
          <img
            :alt="picture.name"
            :src="picture.thumbnailUrl ?? picture.url"
            class="picture-image"
          />
        </template>
        
        <!-- 详细布局模式 - 仅用于公共图库 -->
        <template v-if="layoutMode === 'detailed'">
          <!-- 图片名称 - 顶部居中 -->
          <div class="picture-title">
            <h4 class="title-text">{{ picture.name || '未命名' }}</h4>
          </div>
          
          <!-- 作者信息 -->
          <div class="author-info" @click="handleAuthorClick(picture.userVO, $event)">
            <a-space>
              <span class="author-label">作者：</span>
              <a-avatar :size="24" :src="picture.userVO?.userAvatar || getDefaultAvatar(picture.userVO?.userName || '')">
                {{ picture.userVO?.userName?.charAt(0) || 'U' }}
              </a-avatar>
              <span class="author-name">{{ picture.userVO?.userName || '未知用户' }}</span>
            </a-space>
          </div>
          
          <!-- 统计信息 -->
          <div class="stats-info">
            <div class="stats-container">
              <!-- 浏览量 -->
              <div class="stat-item">
                <a-space size="small">
                  <EyeOutlined class="stat-icon" />
                  <span class="stat-text">{{ picture.viewCount || 0 }}</span>
                </a-space>
              </div>
              <!-- 点赞数 - 优先使用服务器数据，确保页面刷新后状态正确 -->
              <div class="stat-item clickable" @click="handleLike(picture, $event)" :key="`like-${picture.id}-${forceUpdateTrigger}`">
                <a-space size="small">
                  <HeartFilled 
                    v-if="picture.id && ((picture as any).liked || userActionStatus[picture.id]?.liked)"
                    class="stat-icon liked-icon"
                    :class="{ 
                      'processing': picture.id && isOperationInProgress(picture.id, 'like'),
                      'animate-like': picture.id && userActionStatus[picture.id]?.animateLike,
                      'loading': picture.id && userActionStatus[picture.id]?.isLoading
                    }"
                  />
                  <HeartOutlined 
                    v-else
                    class="stat-icon" 
                    :class="{ 
                      'processing': picture.id && isOperationInProgress(picture.id, 'like'),
                      'animate-like': picture.id && userActionStatus[picture.id]?.animateLike,
                      'loading': picture.id && userActionStatus[picture.id]?.isLoading
                    }"
                  />
                  <span class="stat-text">{{ picture.likeCount || 0 }}</span>
                </a-space>
              </div>
              <!-- 收藏数 - 优先使用服务器数据，确保页面刷新后状态正确 -->
              <div class="stat-item clickable" @click="handleCollect(picture, $event)" :key="`collect-${picture.id}-${forceUpdateTrigger}`">
                <a-space size="small">
                  <StarFilled 
                    v-if="picture.id && ((picture as any).collected || userActionStatus[picture.id]?.collected)"
                    class="stat-icon collected-icon" 
                    :class="{ 
                      'processing': picture.id && isOperationInProgress(picture.id, 'collect'),
                      'animate-collect': picture.id && userActionStatus[picture.id]?.animateCollect,
                      'loading': picture.id && userActionStatus[picture.id]?.isLoading
                    }"
                  />
                  <StarOutlined 
                    v-else
                    class="stat-icon" 
                    :class="{ 
                      'processing': picture.id && isOperationInProgress(picture.id, 'collect'),
                      'animate-collect': picture.id && userActionStatus[picture.id]?.animateCollect,
                      'loading': picture.id && userActionStatus[picture.id]?.isLoading
                    }"
                  />
                  <span class="stat-text">{{ picture.collectCount || 0 }}</span>
                </a-space>
              </div>
              <!-- 分享数 -->
              <div class="stat-item clickable" @click="handleShare(picture, $event)">
                <a-space size="small">
                  <ShareIcon 
                    class="stat-icon"
                  />
                  <span class="stat-text">
                    {{ getShareCountDisplay(picture) }}
                  </span>
                </a-space>
              </div>
              <!-- 以图搜图 -->
              <div class="stat-item search-icon-container clickable" @click="doSearch(picture, $event)">
                <a-space size="small">
                  <SearchOutlined class="stat-icon" />
                </a-space>
              </div>
            </div>
          </div>
        </template>

        <!-- 简单布局模式 - 用于私人空间、发布列表、团队空间等 -->
        <template v-else>
          <a-card-meta :title="picture.name || '未命名'" />
        </template>


        <!-- 操作按钮（仅在showOp为true时显示） -->
        <template v-if="showOp" #actions>
          <ShareAltOutlined @click="(e) => doShare(picture, e)" />
          <SearchOutlined @click="(e) => doSearch(picture, e)" />
          <EditOutlined v-if="canEdit" @click="(e) => doEdit(picture, e)" />
          <DeleteOutlined v-if="canDelete" @click="(e) => doDelete(picture, e)" />
        </template>
      </a-card>
    </div>
  </div>
  <a-empty v-if="dataList.length === 0 && !loading" description="暂无图片，快去上传吧" />
  <ShareModal ref="shareModalRef" title="分享图片" :link="shareLink || ''" />
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, onActivated, watch, nextTick, triggerRef, computed, reactive } from 'vue'
import { useRouter } from 'vue-router'
import Masonry from 'masonry-layout'
import imagesLoaded from 'imagesloaded'
import {
  DeleteOutlined,
  EditOutlined,
  SearchOutlined,
  ShareAltOutlined,
  EyeOutlined,
  HeartOutlined,
  HeartFilled,
  StarOutlined,
  StarFilled,
  ShareAltOutlined as ShareIcon,
} from '@ant-design/icons-vue'
import { 
  deletePictureUsingPost,
  togglePictureLikeUsingPost,
  togglePictureCollectUsingPost,
  addPictureShareUsingPost,
  getPictureUserActionUsingGet,
  batchGetPictureUserActionsUsingGet
} from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import ShareModal from '@/components/ShareModal.vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
// 简化的状态管理函数
const isOperationInProgress = (pictureId: number | string, operation: 'like' | 'collect'): boolean => {
  const state = userActionStatus.value[pictureId]
  return state?.isLoading || false
}

const setOperationState = (pictureId: number | string, operation: 'like' | 'collect', isLoading: boolean) => {
  if (!userActionStatus.value[pictureId]) {
    userActionStatus.value[pictureId] = createPictureState(pictureId)
  }
  userActionStatus.value[pictureId].isLoading = isLoading
}

const setSyncState = (syncing: boolean) => {
  // 简化的同步状态管理
}

const markDataSynced = () => {
  // 简化的数据同步标记
}

const forceDataSync = () => {
  // 简化的强制数据同步
}

const markUserStateSynced = () => {
  // 简化的用户状态同步标记
}

// --- Props ---
interface Props {
  dataList?: API.PictureVO[]
  loading?: boolean
  showOp?: boolean
  canEdit?: boolean
  canDelete?: boolean
  onReload?: () => void
  layoutMode?: 'simple' | 'detailed'
}

const props = withDefaults(defineProps<Props>(), {
  dataList: () => [],
  loading: false,
  showOp: false,
  canEdit: false,
  canDelete: false,
  layoutMode: 'simple',
})

// --- Emits ---
const emit = defineEmits<{
  'picture-click': [picture: API.PictureVO]
}>()

// --- Masonry Layout ---
const grid = ref<HTMLElement | null>(null)
let msnry: Masonry | null = null

const initMasonry = () => {
  if (!grid.value) return
  msnry = new Masonry(grid.value, {
    itemSelector: '.grid-item',
    columnWidth: '.grid-sizer',
    percentPosition: true,
    gutter: 20,
    transitionDuration: '0.4s',
    horizontalOrder: true, // 确保水平排序
    hiddenStyle: {
      transform: 'translateY(50px)',
      opacity: 0
    },
    visibleStyle: {
      transform: 'translateY(0)',
      opacity: 1
    }
  })
}

const layout = () => {
  if (!msnry || !grid.value) return
  imagesLoaded(grid.value, () => {
    msnry?.layout?.()
  })
}

onMounted(() => {
  nextTick(() => {
    initMasonry()
    // 确保在组件挂载后重新布局
    setTimeout(() => {
      if (msnry) {
        msnry.layout?.()
      }
    }, 100)
    
    // 组件挂载时加载用户行为状态
    if (props.layoutMode === 'detailed' && props.dataList.length > 0) {
      setTimeout(async () => {
        await loadAllUserActionStatus()
        await nextTick()
        triggerRef(userActionStatus)
      }, 200)
    }
  })
})

onBeforeUnmount(() => {
  msnry?.destroy?.()
})

// 组件激活时刷新用户行为状态
onActivated(() => {
  nextTick(() => {
    if (props.layoutMode === 'detailed' && props.dataList.length > 0) {
      setTimeout(async () => {
        await loadAllUserActionStatus()
        await nextTick()
        triggerRef(userActionStatus)
      }, 100)
    }
  })
})

watch(
  () => props.dataList,
  () => {
    nextTick(() => {
      if (!msnry) {
        initMasonry()
      } else {
        // 重新加载项目并重新布局
        msnry.reloadItems?.()
        layout()
      }
    })
  },
  { deep: true }
)

// --- Router and Actions ---
const router = useRouter()
const loginUserStore = useLoginUserStore()

const doClickPicture = (picture: API.PictureVO) => {
  // 触发emit事件，让父组件处理点击逻辑
  emit('picture-click', picture)
}

const doSearch = (picture: API.PictureVO, e: Event) => {
  e.stopPropagation()
  router.push(`/search_picture?pictureId=${picture.id}`)
}

const doEdit = (picture: API.PictureVO, e: Event) => {
  e.stopPropagation()
  router.push({
    path: '/add_picture',
    query: { id: picture.id, spaceId: picture.spaceId },
  })
}

const doDelete = async (picture: API.PictureVO, e: Event) => {
  e.stopPropagation()
  if (!picture.id) return
  const res = await deletePictureUsingPost({ id: picture.id })
  if (res.data.code === 0) {
    message.success('删除成功')
    props.onReload?.()
  } else {
    message.error('删除失败')
  }
}

// --- Share Modal ---
const shareModalRef = ref()
const shareLink = ref<string>()

const doShare = (picture: API.PictureVO, e: Event) => {
  e.stopPropagation()
  shareLink.value = `${window.location.protocol}//${window.location.host}/picture/${picture.id}`
  if (shareModalRef.value) {
    shareModalRef.value.openModal()
  }
}

// --- Author Functions ---
const getDefaultAvatar = (userName?: string) => {
  if (!userName) return ''
  // 根据用户名生成默认头像颜色
  const colors = [
    '#ff6b6b', '#4ecdc4', '#45b7d1', '#96ceb4', '#feca57',
    '#ff9ff3', '#54a0ff', '#5f27cd', '#00d2d3', '#ff9f43'
  ]
  const colorIndex = userName.charCodeAt(0) % colors.length
  return `data:image/svg+xml;base64,${btoa(`
    <svg width="40" height="40" xmlns="http://www.w3.org/2000/svg">
      <rect width="40" height="40" fill="${colors[colorIndex]}"/>
      <text x="20" y="26" text-anchor="middle" fill="white" font-family="Arial" font-size="16" font-weight="bold">
        ${userName.charAt(0).toUpperCase()}
      </text>
    </svg>
  `)}`
}

const handleAuthorClick = (user: API.UserVO | undefined, e: Event) => {
  e.stopPropagation()
  if (user?.id) {
    router.push(`/user/${user.id}`)
  }
}

// --- 用户行为状态管理 - 企业级隔离方案 ---
// 使用 ref 对象确保 Vue 能检测到深层变化
const userActionStatus = ref<Record<number | string, API.UserPictureActionStatus & { animateLike?: boolean; animateCollect?: boolean; isLoading?: boolean }>>({})

// 强制更新触发器 - 用于强制重新渲染图标状态
const forceUpdateTrigger = ref(0)

// 强制重新渲染所有图标状态
const forceRerender = () => {
  forceUpdateTrigger.value++
}

// 清理动画效果 - 确保动画结束后清除所有效果
const cleanupAnimationEffects = (pictureId: number | string) => {
  const pictureCard = document.querySelector(`[data-picture-id="${pictureId}"]`)
  
  if (pictureCard) {
    const allIcons = pictureCard.querySelectorAll('.stat-icon')
    
    allIcons.forEach((icon) => {
      const element = icon as HTMLElement
      
      // 移除所有动画类
      element.classList.remove('animate-like', 'animate-collect', 'click-feedback', 'processing')
      
      // 强制清除所有视觉效果，包括边框残留
      element.style.filter = 'none'
      element.style.transform = 'none'
      element.style.boxShadow = 'none'
      element.style.textShadow = 'none'
      element.style.animation = 'none'
      element.style.border = 'none'
      element.style.outline = 'none'
      element.style.borderRadius = 'none'
      
      // 确保颜色状态正确
      if (element.classList.contains('liked-icon')) {
        element.style.color = '#ff6b6b'
      } else if (element.classList.contains('collected-icon')) {
        element.style.color = '#ffd700'
      } else {
        element.style.color = '#8c8c8c'
      }
    })
  }
}

// 为每个图片创建独立的状态管理 - 优先使用服务器数据
const createPictureState = (pictureId: number | string): API.UserPictureActionStatus & { animateLike?: boolean; animateCollect?: boolean; isLoading?: boolean } => {
  // 优先从服务器数据中获取状态，确保页面刷新后状态正确
  const picture = props.dataList.find(p => p.id === pictureId)
  if (picture) {
    return {
      liked: Boolean(picture.liked),
      collected: Boolean(picture.collected),
      animateLike: false,
      animateCollect: false,
      isLoading: false
    }
  }
  
  // 如果服务器数据不存在，返回默认状态
  return {
    liked: false,
    collected: false,
    animateLike: false,
    animateCollect: false,
    isLoading: false
  }
}

// 默认状态对象，避免重复创建
const DEFAULT_STATE: API.UserPictureActionStatus & { animateLike?: boolean; animateCollect?: boolean; isLoading?: boolean } = {
  liked: false,
  collected: false,
  animateLike: false,
  animateCollect: false,
  isLoading: false
}

// 获取图片状态的辅助函数 - 优先使用服务器数据，确保状态隔离和响应式更新
const getPictureState = (pictureId: number | string | undefined): API.UserPictureActionStatus & { animateLike?: boolean; animateCollect?: boolean; isLoading?: boolean } => {
  // 如果 pictureId 无效，返回默认状态
  if (!pictureId || (typeof pictureId !== 'number' && typeof pictureId !== 'string')) {
    return DEFAULT_STATE
  }
  
  // 优先从服务器数据中获取状态，确保页面刷新后状态正确
  const picture = props.dataList.find(p => p.id === pictureId)
  if (picture) {
    // 如果服务器数据存在，使用服务器数据作为权威状态
    const serverState: API.UserPictureActionStatus & { animateLike?: boolean; animateCollect?: boolean; isLoading?: boolean } = {
      liked: Boolean(picture.liked),
      collected: Boolean(picture.collected),
      animateLike: false,
      animateCollect: false,
      isLoading: false
    }
    
    // 如果本地状态不存在或与服务器数据不一致，使用服务器数据
    if (!userActionStatus.value[pictureId] || 
        userActionStatus.value[pictureId].liked !== serverState.liked ||
        userActionStatus.value[pictureId].collected !== serverState.collected) {
      
      userActionStatus.value[pictureId] = {
        ...serverState,
        animateLike: userActionStatus.value[pictureId]?.animateLike || false,
        animateCollect: userActionStatus.value[pictureId]?.animateCollect || false,
        isLoading: userActionStatus.value[pictureId]?.isLoading || false
      }
    }
  } else {
    // 如果服务器数据不存在，创建默认状态
    if (!userActionStatus.value[pictureId]) {
      userActionStatus.value[pictureId] = createPictureState(pictureId)
    }
  }
  
  let state = userActionStatus.value[pictureId]
  
  // 验证状态对象的有效性，确保所有必要字段都存在
  if (!state || typeof state.liked !== 'boolean' || typeof state.collected !== 'boolean') {
    userActionStatus.value[pictureId] = createPictureState(pictureId)
    state = userActionStatus.value[pictureId]
  }
  
  return state
}

// 更新图片状态的辅助函数 - 确保Vue响应式更新正常工作
const updatePictureState = async (pictureId: number | string | undefined, updates: Partial<API.UserPictureActionStatus & { animateLike?: boolean; animateCollect?: boolean; isLoading?: boolean }>) => {
  // 参数校验
  if (!pictureId || (typeof pictureId !== 'number' && typeof pictureId !== 'string')) {
    return
  }
  
  // 规范化 pictureId 为 number 类型
  const id = typeof pictureId === 'string' ? parseInt(pictureId) : pictureId
  if (isNaN(id) || id <= 0) {
    return
  }
  
  // 确保状态对象存在
  if (!userActionStatus.value[id]) {
    userActionStatus.value[id] = createPictureState(id)
  }
  
  // 获取当前状态
  const currentState = userActionStatus.value[id]
  
  // 创建新的状态对象，确保所有字段都有默认值
  const newState: API.UserPictureActionStatus & { animateLike?: boolean; animateCollect?: boolean; isLoading?: boolean } = {
    liked: updates.liked !== undefined ? updates.liked : (currentState?.liked ?? false),
    collected: updates.collected !== undefined ? updates.collected : (currentState?.collected ?? false),
    animateLike: updates.animateLike !== undefined ? updates.animateLike : (currentState?.animateLike ?? false),
    animateCollect: updates.animateCollect !== undefined ? updates.animateCollect : (currentState?.animateCollect ?? false),
    isLoading: updates.isLoading !== undefined ? updates.isLoading : ((currentState as any)?.isLoading ?? false)
  }
  
  // 记录状态变化
  const hasChanges = Object.entries(updates).some(([key, value]) => {
    return (currentState as any)?.[key] !== value
  })
  
  if (hasChanges) {
    // 直接更新状态对象，确保响应式更新
    Object.assign(userActionStatus.value[id], newState)
    
    // 强制触发响应式更新
    triggerRef(userActionStatus)
    
    // 强制重新渲染组件
    forceRerender()
    
    // 确保DOM更新完成
    await nextTick()
  }
}


// 批量加载用户行为状态，集成数据同步
const loadAllUserActionStatus = async () => {
  if (props.layoutMode === 'detailed' && props.dataList.length > 0) {
    try {
      setSyncState(true)
      
      // 提取所有有效的图片ID
      const pictureIds = props.dataList
        .map(picture => picture.id)
        .filter(id => id != null && typeof id === 'number')
        .join(',')
      
      if (pictureIds) {
        const res = await batchGetPictureUserActionsUsingGet(pictureIds)
        
        if (res.data.code === 0 && res.data.data) {
          const serverData = res.data.data
          
          // 直接使用服务器数据更新本地状态，确保响应式更新
          for (const [pictureIdStr, status] of Object.entries(serverData)) {
            const pictureId = parseInt(pictureIdStr)
            if (!isNaN(pictureId) && pictureId > 0) {
              // 确保状态对象结构正确
              const validStatus: API.UserPictureActionStatus & { animateLike?: boolean; animateCollect?: boolean; isLoading?: boolean } = {
                liked: status?.liked ?? false,
                collected: status?.collected ?? false,
                animateLike: false,
                animateCollect: false,
                isLoading: false
              }
              
              // 确保状态对象存在，然后更新
              if (!userActionStatus.value[pictureId]) {
                userActionStatus.value[pictureId] = createPictureState(pictureId)
              }
              
              // 使用 Object.assign 确保响应式更新
              Object.assign(userActionStatus.value[pictureId], validStatus)
              
              // 同时更新服务器数据到picture对象，确保页面刷新后状态正确
              const picture = props.dataList.find(p => p.id === pictureId)
              if (picture) {
                (picture as any).liked = validStatus.liked as boolean
                (picture as any).collected = validStatus.collected as boolean
              }
            }
          }
          
          // 批量更新完成后，强制重新渲染图标
          await nextTick()
          
          // 强制触发Vue响应式更新
          triggerRef(userActionStatus)
          
          // 再等待一个事件循环，确保状态完全更新到DOM
          await new Promise(resolve => setTimeout(resolve, 100))
          triggerRef(userActionStatus)
          
          // 标记数据同步完成
          markDataSynced()
          setSyncState(false)
          
          // 标记用户状态同步完成
          markUserStateSynced()
        }
      }
    } catch (error) {
      setSyncState(false)
    }
  }
}

// 强制刷新用户行为状态，集成数据同步
const forceRefreshUserActionStatus = async () => {
  if (props.layoutMode === 'detailed') {
    // 强制数据同步
    forceDataSync()
    
    // 清空缓存 - 使用 reactive 对象清空
    Object.keys(userActionStatus.value).forEach(key => {
      delete userActionStatus.value[parseInt(key)]
    })
    
    // 重新加载
    await loadAllUserActionStatus()
  }
}

// 监听数据变化，重新加载用户行为状态
watch(
  () => props.dataList,
  (newDataList) => {
    if (props.layoutMode === 'detailed') {
      // 清空之前的用户行为状态缓存 - 使用 reactive 对象清空
      Object.keys(userActionStatus.value).forEach(key => {
        delete userActionStatus.value[parseInt(key)]
      })
      // 重新加载所有用户行为状态
      loadAllUserActionStatus()
    }
  },
  { deep: true }
)

// --- 用户行为处理方法 ---
const handleLike = async (picture: API.PictureVO, e: Event) => {
  e.stopPropagation()
  if (!picture.id) return
  
  // 检查操作是否正在进行
  if (isOperationInProgress(picture.id, 'like')) {
    return
  }
  
  // 设置操作状态
  setOperationState(picture.id, 'like', true)
  
  // 更新状态为加载中
  await updatePictureState(picture.id, { 
    isLoading: true 
  })
  
  // 添加点击反馈动画
  const iconElement = e.target as HTMLElement
  let statIcon = null
  
  // 查找图标元素
  if (iconElement.classList.contains('stat-icon')) {
    statIcon = iconElement
  } else {
    const statItem = iconElement.closest('.stat-item')
    if (statItem) {
      statIcon = statItem.querySelector('.stat-icon')
    }
  }
  
  if (statIcon) {
    // 添加点击反馈动画
    statIcon.classList.add('click-feedback')
    setTimeout(() => {
      statIcon.classList.remove('click-feedback')
    }, 200)
  }
  
  try {
    const res = await togglePictureLikeUsingPost(picture.id)
    
    if (res.data.code === 0 && res.data.data) {
      const responseData = res.data.data
      
      // 使用服务器返回的权威数据更新前端
      if (responseData?.liked !== undefined) {
        // 1. 直接更新状态对象，确保响应式更新
        if (!userActionStatus.value[picture.id]) {
          userActionStatus.value[picture.id] = createPictureState(picture.id)
        }
        
        userActionStatus.value[picture.id] = {
          ...userActionStatus.value[picture.id],
          liked: !!responseData.liked,
          isLoading: false
        } as API.UserPictureActionStatus & { animateLike?: boolean; animateCollect?: boolean; isLoading?: boolean }
        
        // 同时更新服务器数据到picture对象，确保页面刷新后状态正确
        (picture as any).liked = responseData.liked
        
        // 2. 强制触发响应式更新
        triggerRef(userActionStatus)
        
        // 3. 强制重新渲染组件
        forceRerender()
        await nextTick()
      }
      
      if (responseData?.likeCount !== undefined) {
        picture.likeCount = responseData.likeCount
      }
      
      // 添加点赞动画和粒子效果
      if (statIcon && responseData?.liked) {
        statIcon.classList.add('animate-like')
        statIcon.classList.add('heart-particles')
        
        setTimeout(() => {
          statIcon.classList.remove('animate-like')
          statIcon.classList.remove('heart-particles')
        }, 800)
      }
      
      // 显示成功消息
      const successMessage = responseData?.liked ? '点赞成功' : '取消点赞'
      message.success(successMessage)
      
    } else {
      
      // 显示错误消息
      message.error(res.data.message || '操作失败，请重试')
      
      // 更新状态为非加载中
      await updatePictureState(picture.id, { isLoading: false })
    }
  } catch (error: any) {
    
    // 显示错误消息
    message.error('网络错误，请重试')
    
    // 更新状态为非加载中
    await updatePictureState(picture.id, { isLoading: false })
  } finally {
    // 确保清理操作状态
    setOperationState(picture.id, 'like', false)
  }
}

const handleCollect = async (picture: API.PictureVO, e: Event) => {
  e.stopPropagation()
  if (!picture.id) return
  
  // 检查操作是否正在进行
  if (isOperationInProgress(picture.id, 'collect')) {
    return
  }
  
  // 设置操作状态
  setOperationState(picture.id, 'collect', true)
  
  // 更新状态为加载中
  await updatePictureState(picture.id, { 
    isLoading: true 
  })
  
  // 添加点击反馈动画
  const iconElement = e.target as HTMLElement
  let statIcon = null
  
  // 查找图标元素
  if (iconElement.classList.contains('stat-icon')) {
    statIcon = iconElement
  } else {
    const statItem = iconElement.closest('.stat-item')
    if (statItem) {
      statIcon = statItem.querySelector('.stat-icon')
    }
  }
  
  if (statIcon) {
    // 添加点击反馈动画
    statIcon.classList.add('click-feedback')
    setTimeout(() => {
      statIcon.classList.remove('click-feedback')
    }, 200)
  }
  
  try {
    // 发送请求到服务器
    
    const res = await togglePictureCollectUsingPost(picture.id)
    
    if (res.data.code === 0 && res.data.data) {
      const responseData = res.data.data
      
      // 使用服务器返回的权威数据更新前端
      if (responseData?.collected !== undefined) {
        // 1. 直接更新状态对象，确保响应式更新
        if (!userActionStatus.value[picture.id]) {
          userActionStatus.value[picture.id] = createPictureState(picture.id)
        }
        
        userActionStatus.value[picture.id] = {
          ...userActionStatus.value[picture.id],
          collected: !!responseData.collected,
          isLoading: false
        } as API.UserPictureActionStatus & { animateLike?: boolean; animateCollect?: boolean; isLoading?: boolean }
        
        // 同时更新服务器数据到picture对象，确保页面刷新后状态正确
        (picture as any).collected = responseData.collected
        
        // 2. 强制触发响应式更新
        triggerRef(userActionStatus)
        
        // 3. 强制重新渲染组件
        forceRerender()
        await nextTick()
      }
      
      if (responseData?.collectCount !== undefined) {
        picture.collectCount = responseData.collectCount
      }
      
      // 添加收藏动画和粒子效果
      if (statIcon && responseData?.collected) {
        statIcon.classList.add('animate-collect')
        statIcon.classList.add('star-particles')
        
        setTimeout(() => {
          statIcon.classList.remove('animate-collect')
          statIcon.classList.remove('star-particles')
        }, 900)
      }
      
      // 显示成功消息
      const successMessage = responseData?.collected ? '收藏成功' : '取消收藏'
      message.success(successMessage)
      
    } else {
      
      // 显示错误消息
      message.error(res.data.message || '操作失败，请重试')
      
      // 更新状态为非加载中
      await updatePictureState(picture.id, { isLoading: false })
    }
  } catch (error: any) {
    
    // 显示错误消息
    message.error('网络错误，请重试')
    
    // 更新状态为非加载中
    await updatePictureState(picture.id, { isLoading: false })
  } finally {
    // 确保清理操作状态
    setOperationState(picture.id, 'collect', false)
  }
}

const handleShare = async (picture: API.PictureVO, e: Event) => {
  e.stopPropagation()
  if (!picture.id) return
  
  try {
    
    // 记录操作前的计数
    const originalCount = picture.shareCount || 0
    
    // 发送请求到服务器
    const res = await addPictureShareUsingPost(picture.id)
    
    if (res.data.code === 0) {
      const responseData = res.data.data
      const success = responseData?.success
      const serverShareCount = responseData?.shareCount
      
      // 更新计数
      picture.shareCount = serverShareCount
      
      if (success) {
        message.success('分享成功')
      } else {
        // 24小时内已分享过，不增加计数但提示分享成功
        message.success('分享成功')
      }
    } else {
      // 请求失败，保持原始状态
      message.error('分享失败')
    }
  } catch (error: any) {
    
    // 根据错误类型显示不同的提示
    if (error.message?.includes('timeout')) {
      message.error('网络超时，请检查网络连接')
    } else if (error.message?.includes('Network Error')) {
      message.error('网络错误，请稍后重试')
    } else {
      message.error('分享失败，请稍后重试')
    }
  }
  
  // 无论成功失败都打开分享模态框
  shareLink.value = `${window.location.protocol}//${window.location.host}/picture/${picture.id}`
  if (shareModalRef.value) {
    shareModalRef.value.openModal()
  }
}

// 格式化分享量显示（用于非作者用户10以上的数据）
const formatShareCount = (count: number): string => {
  if (count === 0) return '0'
  if (count < 10) return `${count}` // 低于10显示原始数据
  if (count < 100) {
    // 四舍五入到十位：14 -> 10+, 15 -> 20+, 24 -> 20+
    const tens = Math.round(count / 10) * 10
    return `${tens}+`
  }
  if (count < 1000) {
    // 四舍五入到百位：140 -> 100+, 150 -> 200+, 240 -> 200+
    const hundreds = Math.round(count / 100) * 100
    return `${hundreds}+`
  }
  if (count < 10000) {
    // 四舍五入到千位：1400 -> 1k+, 1500 -> 2k+, 2400 -> 2k+
    const thousands = Math.round(count / 1000) * 1000
    if (thousands >= 1000) {
      return `${thousands / 1000}k+`
    }
    return `${thousands}+`
  }
  if (count < 100000) {
    // 四舍五入到万位：14000 -> 1w+, 15000 -> 2w+, 24000 -> 2w+
    const tenThousands = Math.round(count / 10000) * 10000
    return `${tenThousands / 10000}w+`
  }
  // 大于10万的情况
  const tenThousands = Math.round(count / 10000) * 10000
  return `${tenThousands / 10000}w+`
}

// 获取分享量显示文本
const getShareCountDisplay = (picture: API.PictureVO) => {
  const count = picture.shareCount || 0
  const currentUser = loginUserStore.loginUser
  
  // 1. 管理员：显示所有图片的原始数据
  if (currentUser?.userRole === 'admin') {
    return count.toString()
  }
  
  // 2. 图片作者：显示原始数据
  if (currentUser?.id && picture.userVO?.id && currentUser.id === picture.userVO.id) {
    return count.toString()
  }
  
  // 3. 非作者用户：10以下显示原始数据，10以上显示格式化数据
  if (count < 10) {
    return count.toString() // 10以下显示原始数据
  } else {
    return formatShareCount(count) // 10以上显示格式化数据
  }
}
</script>

<style scoped>
.picture-list-masonry {
  width: 100%;
  min-height: 200px; /* 确保容器有最小高度 */
  position: relative; /* 确保定位正确 */
}

.grid-sizer,
.grid-item {
  width: calc(100% / 6 - 20px); /* Default for 6 columns */
}

.grid-item {
  margin-bottom: 20px;
}

.picture-card {
  overflow: hidden;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  transition: all 0.3s ease;
  padding: 0;
}

.picture-card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  transform: translateY(-4px);
}

.picture-image {
  width: 100%;
  height: auto;
  display: block;
}

/* 图片标题样式 */
.picture-title {
  padding: 12px 16px 8px;
  text-align: center;
  border-bottom: 1px solid #f0f0f0;
}

.title-text {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #262626;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 作者信息样式 */
.author-info {
  padding: 8px 16px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: all 0.2s ease;
  border-radius: 4px;
  margin: 0 8px;
}

.author-info:hover {
  background-color: #f5f5f5;
}

.author-label {
  font-size: 12px;
  color: #8c8c8c;
  font-weight: 500;
}

.author-name {
  font-size: 12px;
  color: #262626;
  font-weight: 500;
  transition: color 0.2s ease;
}

.author-info:hover .author-name {
  color: #1890ff;
}

/* 统计信息样式 */
.stats-info {
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.stats-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stat-item {
  display: flex;
  align-items: center;
}

.stat-icon {
  color: #8c8c8c;
  font-size: 12px;
  transition: color 0.3s cubic-bezier(0.4, 0, 0.2, 1), transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.stat-text {
  font-size: 12px;
  color: #8c8c8c;
  transition: all 0.3s ease;
}

/* 可点击的统计项样式 */
.stat-item.clickable {
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}

.stat-item.clickable:hover {
  background-color: #f5f5f5;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.stat-item.clickable:not(:has(.liked-icon)):not(:has(.collected-icon)):hover .stat-icon {
  color: #8a2be2;
  transform: scale(1.05);
}

.stat-item.clickable:hover .stat-text {
  color: #8a2be2;
}

/* 点击时的波纹效果 */
.stat-item.clickable:active {
  transform: translateY(0px) scale(0.98);
  transition: all 0.1s ease;
}

/* 为点赞和收藏按钮添加特殊的悬停效果 */
.stat-item.clickable:hover .stat-icon.liked-icon {
  color: #ff6b6b !important;
  transform: scale(1.1);
  filter: drop-shadow(0 0 6px rgba(255, 107, 107, 0.6));
}

.stat-item.clickable:hover .stat-icon.collected-icon {
  color: #ffd700 !important;
  transform: scale(1.1);
  filter: drop-shadow(0 0 6px rgba(255, 215, 0, 0.6));
}

/* 高优先级点赞和收藏状态样式 - 优化视觉效果 */
.picture-card .stat-item.clickable .stat-icon.liked-icon,
.picture-list-masonry .stat-item.clickable .stat-icon.liked-icon,
.stat-icon.liked-icon {
  color: #ff6b6b !important;
  filter: drop-shadow(0 2px 4px rgba(255, 107, 107, 0.3)) !important;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1) !important;
}

.picture-card .stat-item.clickable .stat-icon.collected-icon,
.picture-list-masonry .stat-item.clickable .stat-icon.collected-icon,
.stat-icon.collected-icon {
  color: #ffd700 !important;
  filter: drop-shadow(0 2px 4px rgba(255, 215, 0, 0.4)) !important;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1) !important;
}

/* 单独的动画触发规则 - 使用优化后的动画 */
.picture-card .stat-item.clickable .stat-icon.liked-icon.animate-like,
.picture-list-masonry .stat-item.clickable .stat-icon.liked-icon.animate-like {
  animation: heartBeat 0.8s cubic-bezier(0.68, -0.55, 0.265, 1.55) !important;
}

.picture-card .stat-item.clickable .stat-icon.collected-icon.animate-collect,
.picture-list-masonry .stat-item.clickable .stat-icon.collected-icon.animate-collect {
  animation: starTwinkle 0.9s cubic-bezier(0.68, -0.55, 0.265, 1.55) !important;
}

/* 确保动画结束后清除所有效果 */
.stat-icon.liked-icon:not(.animate-like):not(.processing):not(.loading) {
  filter: none !important;
  transform: none !important;
  box-shadow: none !important;
  text-shadow: none !important;
  animation: none !important;
}

.stat-icon.collected-icon:not(.animate-collect):not(.processing):not(.loading) {
  filter: none !important;
  transform: none !important;
  box-shadow: none !important;
  text-shadow: none !important;
  animation: none !important;
}

/* 强制清除所有可能的视觉效果，包括边框残留 */
.stat-icon:not(.animate-like):not(.animate-collect):not(.click-feedback):not(.processing):not(.loading) {
  filter: none !important;
  transform: none !important;
  box-shadow: none !important;
  text-shadow: none !important;
  animation: none !important;
  border: none !important;
  outline: none !important;
}

/* 确保未激活状态的图标样式 - 不影响激活状态 */
.stat-icon:not(.liked-icon):not(.collected-icon) {
  color: #8c8c8c !important;
  filter: none !important;
  transform: none !important;
  box-shadow: none !important;
  text-shadow: none !important;
  animation: none !important;
  border: none !important;
  outline: none !important;
}

/* 点赞动画触发类 - 使用更高优先级 */
/* 点赞动画效果 - 抖音风格心跳 */
.picture-card .stat-item .stat-icon.animate-like {
  animation: heartBeat 0.8s cubic-bezier(0.68, -0.55, 0.265, 1.55) !important;
  transition: none !important;
  color: #ff6b6b !important;
}

/* 收藏动画效果 - 星星闪烁 */
.picture-card .stat-item .stat-icon.animate-collect {
  animation: starTwinkle 0.9s cubic-bezier(0.68, -0.55, 0.265, 1.55) !important;
  transition: none !important;
  color: #ffd700 !important;
}

/* 点击反馈动画 - 即时响应 */
.picture-card .stat-item .stat-icon.click-feedback {
  animation: clickBounce 0.3s cubic-bezier(0.68, -0.55, 0.265, 1.55) !important;
  transition: none !important;
  color: #8a2be2 !important;
}

/* 处理中状态样式 - 优化动画 */
.picture-card .stat-item .stat-icon.processing {
  opacity: 0.6 !important;
  animation: pulse 1.2s ease-in-out infinite !important;
  transition: none !important;
}

/* 心跳粒子效果 */
.picture-card .stat-item .stat-icon.heart-particles::before {
  content: '❤';
  position: absolute;
  top: -10px;
  left: -10px;
  font-size: 12px;
  color: #ff6b6b;
  animation: heartParticles 0.6s ease-out forwards;
  pointer-events: none;
  z-index: 10;
}

.picture-card .stat-item .stat-icon.heart-particles::after {
  content: '❤';
  position: absolute;
  top: -5px;
  right: -15px;
  font-size: 10px;
  color: #ff8e8e;
  animation: heartParticles 0.6s ease-out 0.1s forwards;
  pointer-events: none;
  z-index: 10;
}

/* 星星粒子效果 */
.picture-card .stat-item .stat-icon.star-particles::before {
  content: '✨';
  position: absolute;
  top: -8px;
  left: -8px;
  font-size: 10px;
  color: #ffd700;
  animation: starSparkle 0.7s ease-out forwards;
  pointer-events: none;
  z-index: 10;
}

.picture-card .stat-item .stat-icon.star-particles::after {
    content: '★';
  position: absolute;
  top: -5px;
  right: -12px;
  font-size: 8px;
  color: #ffed4e;
  animation: starSparkle 0.7s ease-out 0.15s forwards;
  pointer-events: none;
  z-index: 10;
}

/* 加载状态样式 */
.picture-card .stat-item .stat-icon.loading {
  opacity: 0.7 !important;
  animation: loadingPulse 1s ease-in-out infinite !important;
  transition: none !important;
}

/* 加载指示器样式 */
.loading-spinner {
  margin-left: 4px;
  opacity: 0.8;
}

.loading-spinner .ant-spin-dot {
  font-size: 10px;
}

.loading-spinner .ant-spin-dot-item {
  background-color: #1890ff;
  width: 6px;
  height: 6px;
}

/* 抖音风格心跳动画 - 多层次效果 */
@keyframes heartBeat {
  0% {
    transform: scale(1);
    color: #ff6b6b;
    filter: none;
  }
  15% {
    transform: scale(1.4);
    color: #ff8e8e;
    filter: drop-shadow(0 0 8px rgba(255, 107, 107, 0.6));
  }
  30% {
    transform: scale(1.1);
    color: #ff4757;
    filter: drop-shadow(0 0 12px rgba(255, 71, 87, 0.8));
  }
  45% {
    transform: scale(1.3);
    color: #ff7675;
    filter: drop-shadow(0 0 16px rgba(255, 118, 117, 1));
  }
  60% {
    transform: scale(1.05);
    color: #ff6b6b;
    filter: drop-shadow(0 0 8px rgba(255, 107, 107, 0.6));
  }
  75% {
    transform: scale(1.2);
    color: #ff8e8e;
    filter: drop-shadow(0 0 10px rgba(255, 142, 142, 0.7));
  }
  100% {
    transform: scale(1);
    color: #ff6b6b !important;
    filter: none;
  }
}

/* 心跳粒子爆炸效果 */
@keyframes heartParticles {
  0% {
    opacity: 0;
    transform: scale(0) rotate(0deg);
  }
  20% {
    opacity: 1;
    transform: scale(1.2) rotate(45deg);
  }
  40% {
    opacity: 0.8;
    transform: scale(1.5) rotate(90deg);
  }
  60% {
    opacity: 0.6;
    transform: scale(1.8) rotate(135deg);
  }
  80% {
    opacity: 0.3;
    transform: scale(2) rotate(180deg);
  }
  100% {
    opacity: 0;
    transform: scale(2.5) rotate(225deg);
  }
}

/* 收藏星星闪烁动画 - 金色渐变效果 */
@keyframes starTwinkle {
  0% {
    transform: scale(1) rotate(0deg);
    color: #ffd700;
    filter: none;
  }
  20% {
    transform: scale(1.3) rotate(20deg);
    color: #ffed4e;
    filter: drop-shadow(0 0 10px rgba(255, 215, 0, 0.8));
  }
  40% {
    transform: scale(1.1) rotate(-15deg);
    color: #ff6b35;
    filter: drop-shadow(0 0 15px rgba(255, 107, 53, 1));
  }
  60% {
    transform: scale(1.4) rotate(25deg);
    color: #ffa726;
    filter: drop-shadow(0 0 20px rgba(255, 167, 38, 1));
  }
  80% {
    transform: scale(1.05) rotate(-5deg);
    color: #ffd700;
    filter: drop-shadow(0 0 8px rgba(255, 215, 0, 0.6));
  }
  100% {
    transform: scale(1) rotate(0deg);
    color: #ffd700;
    filter: none !important;
  }
}

/* 星星粒子星光效果 */
@keyframes starSparkle {
  0% {
    opacity: 0;
    transform: scale(0) rotate(0deg);
  }
  25% {
    opacity: 1;
    transform: scale(1.5) rotate(90deg);
  }
  50% {
    opacity: 0.8;
    transform: scale(2) rotate(180deg);
  }
  75% {
    opacity: 0.4;
    transform: scale(2.5) rotate(270deg);
  }
  100% {
    opacity: 0;
    transform: scale(3) rotate(360deg);
  }
}

@keyframes pulse {
  0% {
    opacity: 0.6;
  }
  50% {
    opacity: 1;
  }
  100% {
    opacity: 0.6;
  }
}

/* 加载脉冲动画 */
@keyframes loadingPulse {
  0% {
    opacity: 0.7;
    transform: scale(1);
  }
  50% {
    opacity: 1;
    transform: scale(1.05);
  }
  100% {
    opacity: 0.7;
    transform: scale(1);
  }
}

/* 点击反馈动画 - 抖音风格即时反馈 */
@keyframes clickBounce {
  0% {
    transform: scale(1);
    filter: none;
  }
  30% {
    transform: scale(1.5);
    filter: drop-shadow(0 0 15px rgba(138, 43, 226, 1));
  }
  60% {
    transform: scale(1.1);
    filter: drop-shadow(0 0 8px rgba(138, 43, 226, 0.6));
  }
  100% {
    transform: scale(1);
    filter: none;
  }
}

/* 弹性缓动动画 - 更自然的动画效果 */
@keyframes elasticBounce {
  0% {
    transform: scale(1);
  }
  20% {
    transform: scale(1.3);
  }
  40% {
    transform: scale(0.9);
  }
  60% {
    transform: scale(1.1);
  }
  80% {
    transform: scale(0.95);
  }
  100% {
    transform: scale(1);
  }
}


/* 以图搜图按钮样式 */
.search-btn {
  color: #1890ff;
  font-size: 14px;
  height: 24px;
  width: 24px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.search-btn:hover {
  color: #40a9ff;
  background-color: #f0f8ff;
}

.search-icon {
  font-size: 14px;
}


.search-icon-container {
  margin-right: -4px; /* 微调，将图标向右移动 */
}


/* Responsive column widths */
@media (max-width: 1600px) {
  .grid-sizer,
  .grid-item {
    width: calc(100% / 5 - 20px);
  }
}
@media (max-width: 1200px) {
  .grid-sizer,
  .grid-item {
    width: calc(100% / 4 - 20px);
  }
}
@media (max-width: 992px) {
  .grid-sizer,
  .grid-item {
    width: calc(100% / 3 - 20px);
  }
}
@media (max-width: 768px) {
  .grid-sizer,
  .grid-item {
    width: calc(100% / 2 - 20px);
  }
}
@media (max-width: 576px) {
  .grid-sizer,
  .grid-item {
    width: 100%;
  }
}
</style>