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
            <a-select-option value="createTime">时间</a-select-option>
            <a-select-option value="likeCount">点赞数</a-select-option>
            <a-select-option value="viewCount">浏览量</a-select-option>
            <a-select-option value="collectCount">收藏数</a-select-option>
          </a-select>
        </div>
      </div>
    </div>
    
    <!-- 图片列表 -->
    <div class="picture-list-container">
      <PictureList 
        :dataList="dataList" 
        :loading="loading && searchParams.current === 1" 
        layoutMode="detailed"
        @picture-click="handlePictureClick"
      />
    </div>
    <!-- 加载更多提示 -->
    <div v-if="loading && searchParams.current > 1" style="text-align: center; padding: 20px;">
      <a-spin tip="加载中..." />
    </div>
    <!-- 无限滚动哨兵 -->
    <div ref="sentinel" style="height: 50px;"></div>
    <a-empty v-if="!loading && noMoreData && dataList.length > 0" description="已经到底啦" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount, onActivated, reactive, ref, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import {
  listPictureTagCategoryUsingGet,
  listPictureVoByPageUsingPost,
  addPictureViewUsingPost,
} from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import PictureList from '@/components/PictureList.vue'

const route = useRoute()

// --- 核心数据和状态 ---
const dataList = ref<API.PictureVO[]>([])
const total = ref(0)
const loading = ref(false) // 表示是否正在加载数据
const noMoreData = ref(false) // 表示是否已加载所有数据

// --- 搜索条件 ---
const searchParams = reactive<API.PictureQueryRequest>({
  current: 1,
  pageSize: 12,
  sortField: 'createTime',
  sortOrder: 'descend',
})
const categoryList = ref<string[]>([])
const selectedCategory = ref<string>('all')
const sortBy = ref<string>('createTime')

// --- 下拉框容器配置 ---
const getPopupContainer = () => {
  // 直接返回body，让下拉框跟随页面滚动
  return document.body
}

// --- 数据获取 ---
const fetchData = async () => {
  if (loading.value || noMoreData.value) {
    return
  }
  loading.value = true

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
    const res = await listPictureVoByPageUsingPost(params)
    if (res.data.code === 0 && res.data.data) {
      const newRecords = res.data.data.records ?? []
      // 如果是第一页，则直接替换；否则，追加数据
      if (searchParams.current === 1) {
        dataList.value = newRecords
      } else {
        dataList.value.push(...newRecords)
      }
      total.value = res.data.data.total ?? 0

      // 判断是否还有更多数据
      if (newRecords.length < searchParams.pageSize || dataList.value.length >= total.value) {
        noMoreData.value = true
      } else {
        // 准备加载下一页
        searchParams.current++
      }
    } else {
      message.error('获取数据失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('获取数据失败，' + e.message)
  } finally {
    loading.value = false
  }
}

// --- 搜索与筛选 ---
const doSearch = () => {
  searchParams.current = 1
  noMoreData.value = false
  dataList.value = [] // 立即清空旧数据
  fetchData()
}

const getCategoryOptions = async () => {
  const res = await listPictureTagCategoryUsingGet()
  if (res.data.code === 0 && res.data.data) {
    categoryList.value = res.data.data.categoryList ?? []
  } else {
    message.error('获取分类列表失败，' + res.data.message)
  }
}

// --- 无限滚动逻辑 ---
const sentinel = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null

const setupObserver = () => {
  if (observer) observer.disconnect()

  observer = new IntersectionObserver(
    ([entry]) => {
      // 当哨兵元素进入视口、且没有在加载、且还有更多数据时，加载下一页
      if (entry && entry.isIntersecting && !loading.value && !noMoreData.value) {
        fetchData()
      }
    },
    {
      rootMargin: '0px 0px 400px 0px', // 提前400px开始加载
    }
  )

  if (sentinel.value) {
    observer.observe(sentinel.value)
  }
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

// 滚动监听器，检测滚动状态
const handleScroll = () => {
  const scrollTop = window.pageYOffset || document.documentElement.scrollTop
  isScrolled.value = scrollTop > 50 // 滚动超过50px时认为是滚动状态
  
  // 调整下拉框位置
  adjustDropdownPosition()
}

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
onMounted(() => {
  getCategoryOptions()
  fetchData() // 初始加载
  setupObserver()
  
  // 调整固定位置
  nextTick(() => {
    adjustStickyPosition()
  })
  
  // 监听窗口大小变化和滚动
  window.addEventListener('resize', handleResize)
  window.addEventListener('scroll', handleScroll, { passive: true })
  
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
    observer.disconnect()
  })
}

// 页面激活时刷新数据（仅在必要时）
onActivated(() => {
  // 只在数据可能过期时才刷新
  const now = Date.now()
  const lastRefreshTime = localStorage.getItem('lastDataRefresh') || '0'
  const timeSinceLastRefresh = now - parseInt(lastRefreshTime)
  
  // 如果超过5分钟没有刷新，或者没有刷新记录，则刷新数据
  if (timeSinceLastRefresh > 5 * 60 * 1000 || lastRefreshTime === '0') {
    nextTick(() => {
      doSearch() // 重新搜索会清空数据并重新加载
      localStorage.setItem('lastDataRefresh', now.toString())
    })
  }
  
  // 总是刷新用户行为状态（这个比较轻量）
  // 注意：这里需要根据实际的PictureList组件API来调整
})

// 添加强制刷新函数，供外部调用
const forceRefresh = () => {
  doSearch()
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
})


// --- 图片点击处理 ---
const handlePictureClick = async (picture: API.PictureVO) => {
  // 增加浏览量（今天内防重复，从凌晨0点到23:59:59）
  if (picture.id) {
    try {
      const res = await addPictureViewUsingPost(picture.id)
      if (res.data.code === 0) {
        const responseData = res.data.data
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
    } catch (error) {
      // 网络错误时保持当前状态，不进行UI更新
    }
  }
  
  // 跳转到详情页
  window.open(`/picture/${picture.id}`, '_blank')
}

onBeforeUnmount(() => {
  if (observer) {
    observer.disconnect()
  }
  // 清理事件监听器
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('refreshHomePage', handleGlobalRefresh)
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
