<template>
  <div id="homePage">
      <!-- 筛选控制区域 - 悬浮固定 -->
      <div class="filter-controls-sticky" :class="{ 'scrolled': isScrolled }" ref="stickyControls">
      <div class="filter-controls-content">
        <!-- 左侧：分类选择 -->
        <div class="category-section">
          <div class="category-scroll-wrapper">
            <a-tabs v-model:active-key="selectedCategory" @change="doSearch" class="category-tabs">
              <a-tab-pane key="all" tab="全部" />
              <a-tab-pane v-for="category in categoryList" :tab="category" :key="category" />
            </a-tabs>
          </div>
        </div>
        
        <!-- 右侧：排序控制 -->
        <div class="sort-section">
          <span class="filter-label">排序方式：</span>
          <a-select
            v-model:value="sortBy"
            placeholder="选择排序方式"
            size="middle"
            @change="doSearch"
            class="sort-select"
            :get-popup-container="getPopupContainer"
          >
            <a-select-option value="recommend">默认</a-select-option>
            <a-select-option value="viewCount">浏览量</a-select-option>
            <a-select-option value="likeCount">点赞数</a-select-option>
            <a-select-option value="collectCount">收藏数</a-select-option>
          </a-select>
        </div>
      </div>
    </div>
    
    <!-- 图片列表 -->
    <div class="picture-list-container">
      <!-- 首次加载时显示骨架屏 -->
      <PictureListSkeleton 
        v-if="initialLoading && dataList.length === 0" 
        :count="12"
      />
      <!-- 正常图片列表 -->
      <PictureList 
        v-else
        :dataList="dataList" 
        :loading="loading && (searchParams.current || 1) === 1" 
        layoutMode="detailed"
        @picture-click="handlePictureClick"
      />
    </div>
    <!-- 加载更多提示 -->
    <div v-if="loading && (searchParams.current || 1) > 1" style="text-align: center; padding: 20px;">
      <a-spin tip="加载中..." />
    </div>
     <!-- 无限滚动哨兵 - 只在有数据且非加载状态时显示 -->
     <div 
       v-if="!noMoreData && dataList.length > 0" 
       ref="sentinel" 
       style="height: 20px; background: transparent;"
     ></div>
     <!-- 到底提示 -->
     <a-empty v-if="!loading && noMoreData && dataList.length > 0" description="已经到底啦" />
     <!-- 无数据提示 -->
     <a-empty v-if="!loading && !initialLoading && dataList.length === 0" description="暂无数据" />
     
   </div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount, onActivated, reactive, ref, watch, nextTick, computed } from 'vue'
import { useRoute } from 'vue-router'
import {
  listPictureTagCategoryUsingGet,
  listPictureVoByPageUsingPost,
  listPictureVoByPagePublicUsingPost,
  getHomepageRecommendPicturesUsingPost,
  addPictureViewUsingPost,
} from '@/api/pictureController.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { message } from 'ant-design-vue'
import PictureList from '@/components/PictureList.vue'
import PictureListSkeleton from '@/components/PictureListSkeleton.vue'
import { cacheStrategies, initCacheCleanup, sessionCache, localCache } from '@/utils/cache'
import { performanceMonitor, getLoadingStrategy, debounce, throttle } from '@/utils/performance'
import { onPictureDeleted, onPictureUploaded, onPictureUpdated, crossPageComm } from '@/utils/crossPageCommunication'

const route = useRoute()
const loginUserStore = useLoginUserStore()

// --- 核心数据和状态 ---
const dataList = ref<API.PictureVO[]>([])
const total = ref(0)
const loading = ref(false) // 表示是否正在加载数据
const noMoreData = ref(false) // 表示是否已加载所有数据
const initialLoading = ref(true) // 表示是否是首次加载（用于显示骨架屏）
const categoryLoading = ref(false) // 表示分类数据是否正在加载

// --- 去重工具函数 ---
const removeDuplicates = (pictures: API.PictureVO[]): API.PictureVO[] => {
  const seen = new Set<number>()
  return pictures.filter(picture => {
    if (picture.id && seen.has(picture.id)) {
      console.warn(`发现重复图片ID: ${picture.id}，已过滤`)
      return false
    }
    if (picture.id) {
      seen.add(picture.id)
    }
    return true
  })
}

// --- 动态加载策略 ---
const loadingStrategy = getLoadingStrategy()

// --- 搜索条件 ---
const searchParams = reactive<API.PictureQueryRequest>({
  current: 1,
  pageSize: loadingStrategy.pageSize, // 根据网络质量动态调整
  sortField: 'createTime',
  sortOrder: 'descend',
})
const categoryList = ref<string[]>([])
const selectedCategory = ref<string>('all')
const sortBy = ref<string>('recommend')

// --- 下拉框容器配置 ---
const getPopupContainer = () => {
  // 直接返回body，让下拉框跟随页面滚动
  return document.body
}

// --- 数据获取 ---
const fetchData = async (useCache = true) => {
  if (loading.value || noMoreData.value) {
    return
  }
  
  // 防重复请求：检查是否在短时间内重复请求相同数据
  const requestKey = `${sortBy.value}_${selectedCategory.value}_${searchParams.current}`
  const now = Date.now()
  const lastRequestTime = sessionStorage.getItem(`lastRequest_${requestKey}`)
  
  if (lastRequestTime && now - parseInt(lastRequestTime) < 1000) {
    console.log('防重复请求：跳过重复的请求', requestKey)
    return
  }
  
  sessionStorage.setItem(`lastRequest_${requestKey}`, now.toString())
  
  loading.value = true
  
  // 开始性能计时
  performanceMonitor.startTiming('dataLoad')

  // 构造请求参数
  const params = {
    ...searchParams,
    sortField: sortBy.value,
    sortOrder: 'descend', // 固定为降序
  }
  if (selectedCategory.value !== 'all') {
    params.category = selectedCategory.value
  }

  try {
    // 优先尝试从缓存获取数据（仅首页且推荐排序）
    if (useCache && searchParams.current === 1 && sortBy.value === 'recommend') {
      const cachedData = cacheStrategies.recommendPictures.get(selectedCategory.value)
      if (cachedData && cachedData.length > 0) {
        dataList.value = cachedData
        // 注意：不要将total设置为缓存数据长度，保持原有的total值
        // 这样可以确保后续分页加载正常工作
        initialLoading.value = false
        loading.value = false
        
        // 记录缓存命中
        performanceMonitor.recordCacheHit()
        performanceMonitor.endTiming('dataLoad')
        
        // 从缓存加载后，准备加载下一页
        searchParams.current = 2
        noMoreData.value = false
        
        // 确保观察器正常工作
        nextTick(() => {
          setupObserver()
        })
        
        return
      } else {
        // 记录缓存未命中
        performanceMonitor.recordCacheMiss()
      }
    }

    let res: any
    
    // 检查用户登录状态
    const currentUser = loginUserStore.loginUser
    
    // 如果是默认排序（recommend），使用推荐算法
    if (sortBy.value === 'recommend') {
      res = await getHomepageRecommendPicturesUsingPost(params)
    } else {
      // 其他排序方式：根据登录状态选择接口
      if (currentUser?.id) {
        // 已登录用户使用普通查询接口
        res = await listPictureVoByPageUsingPost(params)
      } else {
        // 未登录用户使用公开查询接口
        res = await listPictureVoByPagePublicUsingPost(params)
      }
    }
    
    if (res.data.code === 0 && res.data.data) {
      const newRecords = res.data.data.records ?? []
      
      // 如果是第一页，则直接替换；否则，追加数据
      if (searchParams.current === 1) {
        // 对第一页数据进行去重
        dataList.value = removeDuplicates(newRecords)
        
        // 缓存首页推荐数据
        if (sortBy.value === 'recommend' && dataList.value.length > 0) {
          cacheStrategies.recommendPictures.set(dataList.value, selectedCategory.value)
        }
      } else {
        // 分页加载时，检查重复并去重
        const existingIds = new Set(dataList.value.map((p: API.PictureVO) => p.id))
        const uniqueNewRecords = newRecords.filter((p: API.PictureVO) => !existingIds.has(p.id))
        
        if (uniqueNewRecords.length > 0) {
          // 对新数据进行去重后再添加
          const deduplicatedNewRecords = removeDuplicates(uniqueNewRecords)
          dataList.value.push(...deduplicatedNewRecords)
          console.log(`分页加载: 新增${deduplicatedNewRecords.length}张图片，过滤掉${newRecords.length - deduplicatedNewRecords.length}张重复图片`)
        } else {
          console.log('分页加载: 所有图片都已存在，跳过添加')
        }
      }
      total.value = res.data.data.total ?? 0

      // 判断是否还有更多数据
      if (newRecords.length < (searchParams.pageSize || 10) || dataList.value.length >= total.value) {
        noMoreData.value = true
      } else {
        // 准备加载下一页
        searchParams.current = (searchParams.current || 1) + 1
      }
    } else {
      message.error('获取数据失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('获取数据失败，' + e.message)
  } finally {
    loading.value = false
    initialLoading.value = false
    
    // 结束性能计时
    performanceMonitor.endTiming('dataLoad')
    
    // 确保观察器在数据加载完成后正确工作
    nextTick(() => {
      setupObserver()
    })
  }
}

// --- 搜索与筛选 ---
const doSearch = () => {
  searchParams.current = 1
  noMoreData.value = false
  dataList.value = []
  total.value = 0
  initialLoading.value = true // 重新搜索时重置为初始加载状态
  
  // 先断开现有观察器
  if (observer) {
    observer.disconnect()
    observer = null
  }
  
  // 重新设置观察器
  nextTick(() => {
    setupObserver()
  })
  
  fetchData(false) // 搜索时不使用缓存，确保数据最新
}

const getCategoryOptions = async (useCache = true) => {
  categoryLoading.value = true
  
  try {
    // 优先从缓存获取分类数据
    if (useCache) {
      const cachedCategories = cacheStrategies.categoryList.get()
      if (cachedCategories && cachedCategories.length > 0) {
        categoryList.value = cachedCategories
        categoryLoading.value = false
        return
      }
    }

    const res = await listPictureTagCategoryUsingGet()
    if (res.data.code === 0 && res.data.data) {
      const categories = res.data.data.categoryList ?? []
      categoryList.value = categories
      
      // 缓存分类数据
      if (categories.length > 0) {
        cacheStrategies.categoryList.set(categories)
      }
    } else {
      message.error('获取分类列表失败，' + res.data.message)
    }
  } catch (error: any) {
    message.error('获取分类列表失败，' + error.message)
  } finally {
    categoryLoading.value = false
  }
}

// --- 无限滚动逻辑 ---
const sentinel = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null


const setupObserver = () => {
  // 先断开现有观察器
  if (observer) {
    observer.disconnect()
    observer = null
  }

  // 确保哨兵元素存在
  if (!sentinel.value) {
    console.warn('哨兵元素不存在，无法设置观察器')
    return
  }

  observer = new IntersectionObserver(
    ([entry]) => {
      // 当哨兵元素进入视口、且没有在加载、且还有更多数据时，加载下一页
      if (entry && entry.isIntersecting && !loading.value && !noMoreData.value) {
        console.log('触发无限滚动加载，当前页码:', searchParams.current)
        fetchData(false) // 分页加载不使用缓存，页码会在 fetchData 内部自动递增
      }
    },
    {
      rootMargin: '0px 0px 400px 0px', // 提前400px开始加载
      threshold: 0.1, // 当10%的哨兵元素可见时触发
    }
  )

  observer.observe(sentinel.value)
  console.log('观察器已设置，哨兵元素:', sentinel.value)
}

// --- 监听查询参数变化 ---
watch(
  () => route.query.searchText,
  (newSearchText) => {
    if (newSearchText && typeof newSearchText === 'string') {
      searchParams.searchText = newSearchText
      doSearch()
    }
  },
  { immediate: true }
)

// --- 悬浮固定控制 ---
const stickyControls = ref<HTMLElement | null>(null)
const isScrolled = ref(false) // 滚动状态

// 动态调整固定位置，避免覆盖顶部导航栏
const adjustStickyPosition = () => {
  if (!stickyControls.value) return
  
  // 查找顶部导航栏
  const header = document.querySelector('header') || 
                 document.querySelector('.ant-layout-header') || 
                 document.querySelector('[class*="header"]') ||
                 document.querySelector('.navbar') ||
                 document.querySelector('.nav-header')
  
  let headerHeight = 64 // 默认高度
  
  if (header) {
    const rect = header.getBoundingClientRect()
    headerHeight = rect.height
  }
  
  // 设置固定位置
  stickyControls.value.style.top = `${headerHeight}px`
}

// 滚动监听器，检测滚动状态（使用节流优化性能）
const handleScroll = throttle(() => {
  const scrollTop = window.pageYOffset || document.documentElement.scrollTop
  isScrolled.value = scrollTop > 50 // 滚动超过50px时认为是滚动状态
  
  // 调整下拉框位置
  adjustDropdownPosition()
}, 16) // 约60fps的刷新率

// 调整下拉框位置
const adjustDropdownPosition = () => {
  const dropdowns = document.querySelectorAll('.ant-select-dropdown')
  dropdowns.forEach(dropdown => {
    if (dropdown instanceof HTMLElement) {
      // 强制设置为fixed定位
      dropdown.style.position = 'fixed'
      dropdown.style.zIndex = '10000'
    }
  })
}

// 监听窗口大小变化
const handleResize = () => {
  adjustStickyPosition()
}

// --- 生命周期钩子 ---
onMounted(async () => {
  // 开始页面加载计时
  performanceMonitor.startTiming('pageLoad')
  
  // 初始化缓存清理
  initCacheCleanup()
  
  // 检查是否有数据变化标记，如果有则清理缓存
  const hasDataChanges = localStorage.getItem('hasDataChanges')
  if (hasDataChanges === 'true') {
    // 清理所有相关缓存
    sessionCache.clear() // 清理推荐图片缓存
    localCache.clear()   // 清理分类列表缓存
    localStorage.removeItem('hasDataChanges')
    console.log('检测到数据变化，已清理缓存')
  }
  
  // 优化：先加载分类数据，再加载图片数据，避免并行请求导致的重复查询
  try {
    // 1. 先加载分类数据
    await getCategoryOptions()
    
    // 2. 再加载图片数据
    await fetchData()
  } catch (error) {
    message.error('初始数据加载失败')
  }
  
  setupObserver()
  
  // 调整固定位置
  nextTick(() => {
    adjustStickyPosition()
    
    // 结束页面加载计时
    performanceMonitor.endTiming('pageLoad')
    
  })
  
  // 监听窗口大小变化和滚动
  window.addEventListener('resize', handleResize)
  window.addEventListener('scroll', handleScroll, { passive: true })
  
  // 监听来自子窗口的消息
  window.addEventListener('message', handleWindowMessage)
  
  // 监听下拉框的创建和更新
  setupDropdownObserver()
})

// 设置下拉框观察器
const setupDropdownObserver = () => {
  const observer = new MutationObserver((mutations) => {
    mutations.forEach((mutation) => {
      if (mutation.type === 'childList') {
        mutation.addedNodes.forEach((node) => {
          if (node instanceof HTMLElement) {
            // 检查是否是下拉框
            if (node.classList.contains('ant-select-dropdown')) {
              adjustDropdownPosition()
            }
            // 检查子元素中是否有下拉框
            const dropdowns = node.querySelectorAll('.ant-select-dropdown')
            if (dropdowns.length > 0) {
              adjustDropdownPosition()
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
  
  // 在组件卸载时清理观察器
  onBeforeUnmount(() => {
    if (observer) observer.disconnect()
  })
}

// 页面激活时刷新数据（仅在必要时）
onActivated(() => {
  console.log('主页页面激活，当前数据长度:', dataList.value.length)
  
  // 检查是否有数据变化标记
  const hasDataChanges = localStorage.getItem('hasDataChanges')
  if (hasDataChanges === 'true') {
    // 清理缓存并强制刷新
    sessionCache.clear() // 清理推荐图片缓存
    localCache.clear()   // 清理分类列表缓存
    localStorage.removeItem('hasDataChanges')
    console.log('页面激活时检测到数据变化，已清理缓存')
    
    // 强制刷新数据
    nextTick(() => {
      doSearch()
    })
    return
  }
  
  // 如果数据为空，立即加载数据
  if (dataList.value.length === 0) {
    console.log('页面激活时数据为空，立即加载数据')
    nextTick(() => {
      fetchData()
    })
    return
  }
  
  // 只在数据可能过期时才刷新
  const now = Date.now()
  const lastRefreshTime = localStorage.getItem('lastDataRefresh') || '0'
  const timeSinceLastRefresh = now - parseInt(lastRefreshTime)
  
  // 如果超过5分钟没有刷新，或者没有刷新记录，则刷新数据
  if (timeSinceLastRefresh > 5 * 60 * 1000 || lastRefreshTime === '0') {
    console.log('页面激活时数据过期，刷新数据')
    nextTick(() => {
      doSearch() // 重新搜索会清空数据并重新加载
      localStorage.setItem('lastDataRefresh', now.toString())
    })
  } else {
    console.log('页面激活时数据未过期，跳过刷新')
  }
  
  // 总是刷新用户行为状态（这个比较轻量）
  // 注意：这里需要根据实际的PictureList组件API来调整
})

// 添加强制刷新函数，供外部调用
const forceRefresh = () => {
  // 检查是否有数据变化标记
  const hasDataChanges = localStorage.getItem('hasDataChanges')
  if (hasDataChanges === 'true') {
    // 清理缓存并强制刷新
    sessionCache.clear()
    localCache.clear()
    localStorage.removeItem('hasDataChanges')
    console.log('检测到数据变化，已清理缓存')
    doSearch()
    return
  }
  
  // 检查数据是否为空，如果为空则重新加载
  if (dataList.value.length === 0) {
    console.log('数据为空，重新加载数据')
    doSearch()
    return
  }
  
  // 如果数据不为空，只刷新用户行为状态，不重新加载数据
  console.log('数据已存在，跳过重新加载')
}

// 将刷新函数暴露到全局，供其他组件调用
;(window as any).refreshHomePage = forceRefresh

// 监听全局刷新事件（仅在必要时）
const handleGlobalRefresh = () => {
  const now = Date.now()
  const lastRefreshTime = localStorage.getItem('lastDataRefresh') || '0'
  const timeSinceLastRefresh = now - parseInt(lastRefreshTime)
  
  // 如果超过2分钟没有刷新，才执行刷新
  if (timeSinceLastRefresh > 2 * 60 * 1000) {
    nextTick(() => {
      doSearch()
      localStorage.setItem('lastDataRefresh', now.toString())
    })
  }
}

// 添加全局事件监听
onMounted(() => {
  window.addEventListener('refreshHomePage', handleGlobalRefresh)
  
  // 使用新的跨页面通信工具监听图片删除事件
  onPictureDeleted(handlePictureDeleted)
  
  // 监听图片上传事件
  onPictureUploaded(handlePictureUploaded)
  
  // 监听图片状态变化事件
  onPictureUpdated(handlePictureUpdated)
})

// 处理图片上传事件
const handlePictureUploaded = (event: any) => {
  const { pictureId, data: pictureData } = event
  console.log('收到图片上传事件:', pictureId, pictureData)
  
  // 清理缓存，确保下次加载时获取最新数据
  sessionCache.clear()
  localCache.clear()
  
  // 检查是否已存在相同ID的图片，避免重复添加
  const existingIndex = dataList.value.findIndex(p => p.id === pictureId)
  
  // 如果当前是推荐排序且是第一页，将新图片添加到列表顶部
  if (sortBy.value === 'recommend' && searchParams.current === 1 && pictureData && existingIndex === -1) {
    // 将新图片添加到列表顶部，然后进行去重
    dataList.value.unshift(pictureData)
    dataList.value = removeDuplicates(dataList.value)
    console.log('已将新图片添加到列表顶部:', pictureId)
    
    // 确保观察器正常工作
    nextTick(() => {
      setupObserver()
    })
  } else if (existingIndex !== -1) {
    // 如果图片已存在，更新数据而不是添加
    dataList.value[existingIndex] = pictureData
    console.log('已更新现有图片数据:', pictureId)
  } else {
    // 其他情况，强制刷新数据
    console.log('强制刷新数据以获取最新图片')
    nextTick(() => {
      doSearch()
      // 确保在数据加载完成后重新设置观察器
      setTimeout(() => {
        setupObserver()
        console.log('图片上传后重新初始化观察器')
      }, 100)
    })
  }
}

// 处理图片删除事件
const handlePictureDeleted = (event: any) => {
  const { pictureId } = event
  console.log('收到图片删除事件:', pictureId)
  
  // 立即清理缓存并刷新
  sessionCache.clear()
  localCache.clear()
  
  // 从当前数据列表中移除已删除的图片
  if (pictureId) {
    const index = dataList.value.findIndex(p => p.id === pictureId)
    if (index !== -1) {
      dataList.value.splice(index, 1)
      console.log('已从列表中移除图片:', pictureId)
    }
  }
  
  // 强制刷新数据并确保观察器正确重新初始化
  nextTick(() => {
    doSearch()
    // 确保在数据加载完成后重新设置观察器
    setTimeout(() => {
      setupObserver()
      console.log('删除图片后重新初始化观察器')
    }, 100)
  })
}

// 处理图片更新事件
const handlePictureUpdated = (event: any) => {
  const { pictureId, data } = event;
  console.log('收到图片更新事件:', pictureId, data);

  // 1. 更新当前视图中的数据 (确保响应式)
  const pictureIndex = dataList.value.findIndex(p => p.id === pictureId);
  if (pictureIndex > -1) {
    const originalPicture = dataList.value[pictureIndex];
    dataList.value[pictureIndex] = { ...originalPicture, ...data };
    console.log('已更新内存中的图片数据:', pictureId, data);
  }

  // 2. 更新会话缓存中的数据，防止页面刷新后状态丢失
  try {
    const cachedData = cacheStrategies.recommendPictures.get(selectedCategory.value);
    if (cachedData) {
      const cachedIndex = cachedData.findIndex(p => p.id === pictureId);
      if (cachedIndex > -1) {
        // 更新缓存中的对象
        const originalCachedPicture = cachedData[cachedIndex];
        cachedData[cachedIndex] = { ...originalCachedPicture, ...data };
        // 将更新后的数组存回缓存
        cacheStrategies.recommendPictures.set(cachedData, selectedCategory.value);
        console.log('已同步更新SessionStorage中的缓存数据');
      }
    }
  } catch (e) {
    console.error('更新缓存失败:', e);
  }
};


// --- 处理来自子窗口的消息 ---
const handleWindowMessage = (event: MessageEvent) => {
  // 验证消息来源
  if (event.origin !== window.location.origin) {
    return
  }
  
  const { type, pictureId, timestamp } = event.data
  
  if (type === 'pictureDeleted') {
    console.log('收到子窗口删除消息:', pictureId)
    
    // 检查消息时间戳，避免处理过期消息
    const now = Date.now()
    if (now - timestamp < 30000) { // 30秒内的消息才处理
      handlePictureDeleted({ detail: { pictureId } })
    }
  }
}

// --- 图片点击处理 ---
const handlePictureClick = async (picture: API.PictureVO) => {
  // 增加浏览量（今天内防重复，从凌晨0点到23:59:59）
  if (picture.id) {
    try {
      const res = await addPictureViewUsingPost(picture.id)
      if (res.data.code === 0) {
        const responseData = res.data.data
        if (responseData) {
          const success = responseData.success
          const serverViewCount = responseData.viewCount
          
          // 智能UI更新：基于服务器响应
          const pictureIndex = dataList.value.findIndex(p => p.id === picture.id)
          if (pictureIndex !== -1) {
            if (success) {
              // 成功增加浏览量，使用服务器计数
              dataList.value[pictureIndex].viewCount = serverViewCount
            } else {
              // 今天已浏览过，使用服务器计数
              dataList.value[pictureIndex].viewCount = serverViewCount
            }
          }
        }
      }
    } catch (error) {
      // 网络错误时保持当前状态，不进行UI更新
    }
  }
  
  // 跳转到详情页
  window.open(`/picture/${picture.id}`, '_blank')
}

onBeforeUnmount(() => {
  // 清理无限滚动观察器
  if (observer) {
    observer.disconnect()
    observer = null
  }
  // 清理事件监听器
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('refreshHomePage', handleGlobalRefresh)
  window.removeEventListener('message', handleWindowMessage)
  
  // 清理跨页面通信
  crossPageComm.destroy()
})

// 重新激活时重新设置观察器（用于keep-alive组件）
onActivated(() => {
  nextTick(() => {
    setupObserver()
  })
})
</script>

<style scoped>
#homePage {
  margin-bottom: 16px;
  padding-top: 120px; /* 为固定的筛选控制区域留出空间 */
}

/* 筛选控制区域 - 悬浮固定样式 */
.filter-controls-sticky {
  position: fixed;
  top: 64px; /* 假设顶部导航栏高度为64px，避免覆盖 */
  left: 0;
  right: 0;
  width: 100%;
  z-index: 998; /* 降低z-index，让侧边菜单栏(z-index: 999)能够覆盖 */
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid #f0f0f0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transition: all 0.3s ease;
}

/* 滚动时的透明背景效果 */
.filter-controls-sticky.scrolled {
  background: transparent;
  backdrop-filter: blur(15px);
  border-bottom: 1px solid rgba(240, 240, 240, 0.8);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

.filter-controls-sticky:hover {
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

/* 滚动时悬停效果 */
.filter-controls-sticky.scrolled:hover {
  background: transparent;
  backdrop-filter: blur(20px);
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.12);
}

.filter-controls-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  max-width: 1200px;
  margin: 0 auto;
  gap: 20px;
}

  .category-section {
    display: flex;
    align-items: center;
    gap: 12px;
    flex: 1;
    /* 为左侧菜单栏留出空间，避免文字被覆盖 */
    margin-left: 80px;
    transition: margin-left 0.3s ease;
  }

.sort-section {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 220px;
  margin-left: 30px;
}

.filter-label {
  font-size: 14px;
  font-weight: 500;
  color: #262626;
  white-space: nowrap;
}

.category-tabs {
  margin: 0;
  flex: 1;
  min-width: 300px;
}

.category-tabs :deep(.ant-tabs-nav) {
  margin: 0;
}

.category-tabs :deep(.ant-tabs-tab) {
  padding: 6px 12px;
  font-size: 14px;
  font-weight: 400;
  font-family: 'PingFang SC', 'Microsoft YaHei', 'Helvetica Neue', Arial, sans-serif;
  font-style: italic;
  border-radius: 6px;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
  border: 1px solid transparent;
  background-color: #f9f9f9;
  margin-right: 4px;
  transform: skew(-8deg);
}

/* 滚动时增强分类标签的视觉效果 */
.filter-controls-sticky.scrolled .category-tabs :deep(.ant-tabs-tab) {
  background-color: rgba(249, 249, 249, 0.9);
  border: 1px solid rgba(0, 0, 0, 0.08);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.category-tabs :deep(.ant-tabs-tab):hover {
  color: #9c27b0;
  background-color: #fce4ec;
  border-color: #f8bbd0;
  transform: skew(-8deg) translateY(-1px);
}

.category-tabs :deep(.ant-tabs-tab-active) {
  color: #9c27b0 !important;
  background-color: #fce4ec !important;
  border-color: #f8bbd0 !important;
  font-weight: 500;
  box-shadow: 0 1px 4px rgba(156, 39, 176, 0.2);
  transform: skew(-8deg);
}

/* 滚动时增强激活标签的视觉效果 */
.filter-controls-sticky.scrolled .category-tabs :deep(.ant-tabs-tab-active) {
  background-color: rgba(252, 228, 236, 0.95) !important;
  border-color: rgba(248, 187, 208, 0.9) !important;
  box-shadow: 0 2px 8px rgba(156, 39, 176, 0.3);
}

.category-tabs :deep(.ant-tabs-tab-active .ant-tabs-tab-btn) {
  color: #9c27b0 !important;
}

.category-tabs :deep(.ant-tabs-tab-btn) {
  transform: skew(8deg);
  display: inline-block;
}

.category-tabs :deep(.ant-tabs-ink-bar) {
  display: none;
}

.sort-select {
  min-width: 120px;
  font-size: 14px;
  transition: all 0.3s ease;
}

.sort-select:hover {
  border-color: #1890ff;
}

.sort-select :deep(.ant-select-selector) {
  border-radius: 6px;
  padding: 6px 12px;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
}

.sort-select :deep(.ant-select-focused .ant-select-selector) {
  border-color: #1890ff;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.2);
}

/* 滚动时增强排序选择器的视觉效果 */
.filter-controls-sticky.scrolled .sort-select :deep(.ant-select-selector) {
  background-color: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(0, 0, 0, 0.1);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.filter-controls-sticky.scrolled .sort-select:hover :deep(.ant-select-selector) {
  border-color: #1890ff;
  background-color: rgba(255, 255, 255, 0.95);
  box-shadow: 0 2px 6px rgba(24, 144, 255, 0.15);
}

.picture-list-container {
  width: 100%;
  min-height: 400px;
  margin-bottom: 20px;
}

/* 分类标签过多时的滚动处理 */
.category-scroll-wrapper {
  flex: 1;
  overflow-x: auto;
  padding-bottom: 4px;
}

.category-scroll-wrapper::-webkit-scrollbar {
  height: 4px;
}

.category-scroll-wrapper::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 2px;
}

.category-scroll-wrapper::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 2px;
}

.category-scroll-wrapper::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

/* 响应式设计优化 */
@media (max-width: 1024px) {
  .filter-controls-content {
    padding: 14px 18px;
  }
  
  .category-tabs {
    min-width: 250px;
  }
}

@media (max-width: 768px) {
  .filter-controls-content {
    flex-direction: column;
    gap: 16px;
    padding: 12px 16px;
  }
  
  .category-section,
  .sort-section {
    width: 100%;
    justify-content: center;
  }
  
  .category-section {
    flex-direction: column;
    gap: 8px;
    /* 移动端取消左侧偏移 */
    margin-left: 0;
  }
  
  .sort-section {
    flex-wrap: wrap;
    gap: 8px;
  }
  
  .category-tabs {
    min-width: auto;
    width: 100%;
  }
  
  .sort-select {
    min-width: 100px;
    flex: 1;
  }
  
  /* 移动端调整固定位置 */
  .filter-controls-sticky {
    top: 56px; /* 移动端导航栏通常更矮 */
  }
  
  #homePage {
    padding-top: 100px; /* 移动端减少顶部间距 */
  }
}

@media (max-width: 480px) {
  .filter-controls-content {
    padding: 8px 12px;
  }
  
  .category-tabs :deep(.ant-tabs-tab) {
    padding: 6px 12px;
    font-size: 13px;
  }
  
  .sort-select {
    min-width: 80px;
  }
  
  /* 小屏幕设备调整 */
  .filter-controls-sticky {
    top: 48px; /* 小屏幕导航栏更矮 */
  }
  
  #homePage {
    padding-top: 90px; /* 小屏幕进一步减少顶部间距 */
  }
  
  /* 小屏幕设备取消分类文字左侧偏移 */
  .category-section {
    margin-left: 0;
  }
}

/* 下拉框滚动修复 */
.sort-select :deep(.ant-select-dropdown) {
  position: fixed !important;
  z-index: 10000 !important;
}

/* 确保下拉框在滚动时正确定位 */
.sort-select :deep(.ant-select-dropdown-placement-bottomLeft) {
  position: fixed !important;
}

.sort-select :deep(.ant-select-dropdown-placement-bottomRight) {
  position: fixed !important;
}
</style>