<template>
  <div class="liked-pictures-page">
    <!-- 顶部信息区域和搜索筛选区域 -->
    <div class="top-section">
      <div class="info-content">
        <div class="info-left">
          <div class="page-title">
            <HeartOutlined class="title-icon" />
            <span>我的点赞</span>
          </div>
          <div class="page-description">
            发现您曾经心动的那一瞬间，重温美好回忆
          </div>
        </div>
        <div class="info-right">
          <div class="count-info">
            <div class="count-number">{{ totalCount }}</div>
            <div class="count-label">张图片</div>
          </div>
        </div>
      </div>
      
      <div class="search-filter-section">
      <div class="search-row">
        <div class="search-item">
          <label class="search-label">按名称搜索：</label>
          <a-input
            v-model:value="searchParams.name"
            placeholder="输入图片名称"
            size="middle"
            @pressEnter="handleSearch"
            class="search-input-small"
          >
            <template #prefix>
              <SearchOutlined />
            </template>
          </a-input>
        </div>
        
        <div class="search-item">
          <label class="search-label">按标签搜索：</label>
          <a-input
            v-model:value="searchParams.tags"
            placeholder="输入标签关键词"
            size="middle"
            @pressEnter="handleSearch"
            class="search-input-small"
          >
            <template #prefix>
              <TagOutlined />
            </template>
          </a-input>
        </div>
        
        <div class="search-item">
          <label class="search-label">按分类搜索：</label>
          <a-select
            v-model:value="searchParams.category"
            placeholder="选择分类"
            size="middle"
            allowClear
            @change="handleSearch"
            class="search-input-small"
          >
            <a-select-option value="风景">风景</a-select-option>
            <a-select-option value="人物">人物</a-select-option>
            <a-select-option value="动物">动物</a-select-option>
            <a-select-option value="建筑">建筑</a-select-option>
            <a-select-option value="美食">美食</a-select-option>
            <a-select-option value="艺术">艺术</a-select-option>
            <a-select-option value="其他">其他</a-select-option>
          </a-select>
        </div>
      </div>
      
      <div class="search-row">
        <div class="search-item">
          <label class="search-label">按颜色搜索：</label>
          <div class="color-picker-wrapper">
            <ColorPicker 
              format="hex" 
              :pureColor="searchParams.color || '#000000'"
              @pureColorChange="onColorChange" 
            />
          </div>
        </div>
        
        <div class="filter-item">
          <label class="search-label">排序方式：</label>
          <a-select
            v-model:value="sortBy"
            placeholder="选择排序方式"
            size="middle"
            @change="handleSearch"
            class="search-input-small"
          >
            <a-select-option value="createTime">时间</a-select-option>
            <a-select-option value="likeCount">点赞数</a-select-option>
            <a-select-option value="viewCount">浏览量</a-select-option>
            <a-select-option value="collectCount">收藏数</a-select-option>
          </a-select>
        </div>
        
        <div class="filter-item">
          <label class="search-label">排序顺序：</label>
          <a-select
            v-model:value="sortOrder"
            placeholder="选择排序顺序"
            size="middle"
            @change="handleSearch"
            class="search-input-small"
          >
            <a-select-option value="desc">降序</a-select-option>
            <a-select-option value="asc">升序</a-select-option>
          </a-select>
        </div>
        
        <div class="search-actions">
          <a-button type="primary" @click="handleSearch" :loading="loading">
            <SearchOutlined />
            搜索
          </a-button>
          <a-button @click="handleReset">
            <ReloadOutlined />
            重置
          </a-button>
        </div>
      </div>
    </div>
    </div>

    <!-- 图片列表区域 -->
    <div class="content-section">
      <!-- 加载状态 -->
      <div v-if="loading" class="loading-container">
        <a-spin size="large">
          <div class="loading-text">正在加载你的点赞图片...</div>
        </a-spin>
      </div>

      <!-- 空状态 -->
      <div v-else-if="!dataList || dataList.length === 0" class="empty-container">
        <div class="empty-content">
          <HeartOutlined class="empty-icon" />
          <h3 class="empty-title">还没有点赞的图片</h3>
          <p class="empty-description">
            去公共图库或团队空间发现更多精彩内容吧！
          </p>
          <a-button type="primary" @click="goToPublicGallery">
            <EyeOutlined />
            浏览图库
          </a-button>
        </div>
      </div>

      <!-- 图片网格 -->
      <div v-else class="pictures-grid">
        <div
          v-for="picture in dataList"
          :key="picture.id"
          class="picture-card"
          @click="goToPictureDetail(picture.id)"
        >
          <div class="picture-container">
            <img
              :src="picture.thumbnailUrl || picture.url"
              :alt="picture.name"
              class="picture-image"
              @error="handleImageError"
              @contextmenu.prevent
              @dragstart.prevent
              @selectstart.prevent
            />
            
            <!-- 图片信息覆盖层 -->
            <div class="picture-overlay">
              <div class="picture-info">
                <h4 class="picture-title">{{ picture.name || '未命名' }}</h4>
                <p class="picture-meta">
                  <a-tooltip 
                    :title="getUserTooltipContent(picture.userVO)"
                    placement="top"
                  >
                    <div class="user-avatar-container">
                      <img 
                        :src="picture.userVO?.userAvatar || '/default-avatar.png'" 
                        :alt="picture.userVO?.userName || '未知用户'"
                        class="user-avatar"
                        @error="handleAvatarError"
                      />
                    </div>
                  </a-tooltip>
                  <span class="user-name">{{ picture.userVO?.userName || '未知用户' }}</span>
                </p>
              </div>
              
              <!-- 操作按钮 -->
              <div class="picture-actions">
                <a-tooltip title="查看详情">
                  <a-button
                    type="text"
                    size="small"
                    @click.stop="goToPictureDetail(picture.id)"
                    class="action-btn"
                  >
                    <EyeOutlined />
                  </a-button>
                </a-tooltip>
                
                <a-tooltip title="取消点赞">
                  <a-button
                    type="text"
                    size="small"
                    @click.stop="handleUnlike(picture)"
                    :loading="isOperationInProgress(picture.id, 'like')"
                    class="action-btn unlike-btn"
                  >
                    <HeartFilled />
                  </a-button>
                </a-tooltip>
                
                <a-tooltip :title="picture.collected ? '取消收藏' : '收藏'">
                  <a-button
                    type="text"
                    size="small"
                    @click.stop="handleToggleCollect(picture)"
                    :loading="isOperationInProgress(picture.id, 'collect')"
                    class="action-btn"
                    :class="{ 'collected': picture.collected }"
                  >
                    <StarFilled v-if="picture.collected" />
                    <StarOutlined v-else />
                  </a-button>
                </a-tooltip>
              </div>
            </div>
          </div>
          
          <!-- 图片统计信息 -->
          <div class="picture-stats">
            <div class="stat-item">
              <EyeOutlined />
              <span>{{ picture.viewCount || 0 }}</span>
            </div>
            <div class="stat-item">
              <HeartOutlined />
              <span>{{ picture.likeCount || 0 }}</span>
            </div>
            <div class="stat-item">
              <StarOutlined />
              <span>{{ picture.collectCount || 0 }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div v-if="totalCount > 0" class="pagination-container">
        <a-pagination
          v-model:current="currentPage"
          v-model:page-size="pageSize"
          :total="totalCount"
          :show-size-changer="true"
          :show-quick-jumper="true"
          :show-total="(total: number, range: [number, number]) => `共 ${total} 张图片，当前显示 ${range[0]}-${range[1]} 张`"
          @change="handlePageChange"
          @show-size-change="handlePageSizeChange"
        />
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  HeartOutlined,
  HeartFilled,
  StarOutlined,
  StarFilled,
  EyeOutlined,
  UserOutlined,
  ReloadOutlined,
  SearchOutlined,
  TagOutlined
} from '@ant-design/icons-vue'
import { getUserLikedPicturesUsingPost, getUserStatsUsingGet, searchLikedPicturesByColorUsingPost } from '@/api/pictureController.ts'
import { togglePictureLikeUsingPost, togglePictureCollectUsingPost } from '@/api/pictureController.ts'
import { ColorPicker } from 'vue3-colorpicker'
import 'vue3-colorpicker/style.css'

const router = useRouter()

// 响应式数据
const dataList = ref<API.PictureVO[]>([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(12)
const totalCount = ref(0)
const sortBy = ref('createTime')
const sortOrder = ref('desc')

// 统计数据
const userStats = ref({
  likedCount: 0,
  collectedCount: 0,
  viewedCount: 0,
  sharedCount: 0
})

// 搜索参数
const searchParams = ref({
  name: '',
  tags: '',
  category: '',
  color: ''
})

// 操作状态管理
const operationStatus = ref<Record<string, { isLoading: boolean }>>({})

// 计算属性
const isOperationInProgress = (pictureId: number | string | undefined, operation: 'like' | 'collect'): boolean => {
  if (!pictureId) return false
  const state = operationStatus.value[pictureId]
  return state?.isLoading || false
}

// 设置操作状态
const setOperationState = (pictureId: number | string | undefined, operation: 'like' | 'collect', isLoading: boolean) => {
  if (!pictureId) return
  if (!operationStatus.value[pictureId]) {
    operationStatus.value[pictureId] = { isLoading: false }
  }
  operationStatus.value[pictureId].isLoading = isLoading
}

    // 加载数据
const loadData = async () => {
  try {
    loading.value = true
    
    // 检查是否有颜色搜索
    const hasColorSearch = searchParams.value.color && searchParams.value.color.trim() && searchParams.value.color !== '#000000'
    
    if (hasColorSearch) {
      // 使用颜色搜索API
      const colorSearchParams: API.SearchPictureByColorRequest = {
        picColor: searchParams.value.color
      }
      
      const res = await searchLikedPicturesByColorUsingPost(colorSearchParams)
      
      if (res.data.code === 0 && res.data.data) {
        dataList.value = res.data.data || []
        totalCount.value = res.data.data.length || 0
      } else {
        message.error(res.data.message || '颜色搜索失败')
        dataList.value = []
        totalCount.value = 0
      }
    } else {
      // 使用普通搜索API
      const queryParams: API.PictureQueryRequest = {
        current: currentPage.value,
        pageSize: pageSize.value,
        sortField: sortBy.value,
        sortOrder: sortOrder.value,
        name: searchParams.value.name || undefined,
        category: searchParams.value.category || undefined,
        searchText: searchParams.value.name || undefined, // 使用name作为searchText
        tags: searchParams.value.tags ? [searchParams.value.tags] : undefined
      }
      
      const res = await getUserLikedPicturesUsingPost(queryParams)
      
      if (res.data.code === 0 && res.data.data) {
        dataList.value = res.data.data.records || []
        totalCount.value = res.data.data.total || 0
      } else {
        message.error(res.data.message || '加载失败')
        dataList.value = []
        totalCount.value = 0
      }
    }
  } catch (error) {
    console.error('加载点赞图片失败:', error)
    message.error('加载失败，请稍后重试')
    dataList.value = []
    totalCount.value = 0
  } finally {
    loading.value = false
  }
}
// 搜索处理
const handleSearch = () => {
  currentPage.value = 1
  loadData()
}

// 颜色变化处理
const onColorChange = (color: string) => {
  searchParams.value.color = color
  // 延迟搜索，避免频繁请求
  setTimeout(() => {
    handleSearch()
  }, 300)
}

// 重置搜索
const handleReset = () => {
  searchParams.value = {
    name: '',
    tags: '',
    category: '',
    color: ''
  }
  sortBy.value = 'createTime'
  sortOrder.value = 'desc'
  currentPage.value = 1
  loadData()
}

// 刷新数据
const handleRefresh = () => {
  loadData()
}

// 分页处理
const handlePageChange = (page: number) => {
  currentPage.value = page
  loadData()
}

const handlePageSizeChange = (current: number, size: number) => {
  currentPage.value = current
  pageSize.value = size
  loadData()
}

// 图片错误处理
const handleImageError = (event: Event) => {
  const img = event.target as HTMLImageElement
  img.src = '/placeholder-image.png' // 设置默认图片
}

// 头像错误处理
const handleAvatarError = (event: Event) => {
  const img = event.target as HTMLImageElement
  img.src = '/default-avatar.png' // 设置默认头像
}

// 生成用户提示内容
const getUserTooltipContent = (userVO: API.UserVO | undefined) => {
  if (!userVO) {
    return '未知用户'
  }
  
  const parts = []
  if (userVO.userName) {
    parts.push(`昵称: ${userVO.userName}`)
  }
  if (userVO.userAccount) {
    parts.push(`账号: ${userVO.userAccount}`)
  }
  if (userVO.userProfile) {
    parts.push(`简介: ${userVO.userProfile}`)
  }
  if (userVO.userRole) {
    parts.push(`角色: ${userVO.userRole === 'admin' ? '管理员' : '普通用户'}`)
  }
  
  return parts.length > 0 ? parts.join('\n') : '用户信息'
}

// 跳转到图片详情
const goToPictureDetail = (pictureId: number | undefined) => {
  if (pictureId) {
    router.push(`/picture/${pictureId}`)
  }
}

// 跳转到公共图库
const goToPublicGallery = () => {
  router.push('/')
}

// 取消点赞
const handleUnlike = async (picture: API.PictureVO) => {
  if (!picture.id) return
  
  try {
    setOperationState(picture.id, 'like', true)
    const res = await togglePictureLikeUsingPost(picture.id)
    
    if (res.data.code === 0) {
      // 从列表中移除这张图片
      dataList.value = dataList.value.filter(p => p.id !== picture.id)
      totalCount.value = Math.max(0, totalCount.value - 1)
      message.success('已取消点赞')
    } else {
      message.error(res.data.message || '操作失败')
    }
  } catch (error) {
    console.error('取消点赞失败:', error)
    message.error('操作失败，请稍后重试')
  } finally {
    setOperationState(picture.id, 'like', false)
  }
}

// 切换收藏状态
const handleToggleCollect = async (picture: API.PictureVO) => {
  if (!picture.id) return
  
  try {
    setOperationState(picture.id, 'collect', true)
    const res = await togglePictureCollectUsingPost(picture.id)
    
    if (res.data.code === 0 && res.data.data) {
      const result = res.data.data as any
      picture.collected = result.collected
      picture.collectCount = result.collectCount
      message.success(picture.collected ? '已收藏' : '已取消收藏')
    } else {
      message.error(res.data.message || '操作失败')
    }
  } catch (error) {
    console.error('收藏操作失败:', error)
    message.error('操作失败，请稍后重试')
  } finally {
    setOperationState(picture.id, 'collect', false)
  }
}

// 组件挂载时加载数据
// 加载统计数据
const loadUserStats = async () => {
  try {
    const res = await getUserStatsUsingGet()
    if (res.data.code === 0 && res.data.data) {
      userStats.value = res.data.data as any
    }
  } catch (error) {
    console.error('加载用户统计数据失败:', error)
  }
}

onMounted(() => {
  loadData()
  loadUserStats()
})
</script>

<style scoped>
.liked-pictures-page {
  min-height: 100vh;
  background: #f5f5f5;
  padding: 20px;
}

/* 顶部信息区域和搜索筛选区域 */
.top-section {
  margin-bottom: 30px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  overflow: hidden;
}

.info-content {
  position: relative;
  padding: 30px 30px 60px 30px;
  overflow: hidden;
}

.info-left {
  position: relative;
  z-index: 2;
}

.page-title {
  font-size: 1.8rem;
  font-weight: 600;
  color: #2c3e50;
  margin: 0 0 8px 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.title-icon {
  color: #e74c3c;
  font-size: 1.6rem;
}

.page-description {
  font-size: 0.95rem;
  color: #7f8c8d;
  margin: 0;
  line-height: 1.4;
}

.info-right {
  position: absolute;
  top: 20px;
  right: 20px;
  background: linear-gradient(225deg, #ff9a9e 0%, #fecfef 50%, #fecfef 100%);
  padding: 30px 25px;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(255, 154, 158, 0.25);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.3);
  min-width: 160px;
  z-index: 10;
  transform: translateY(-2px);
  transition: all 0.3s ease;
  overflow: hidden;
}

.info-right::before {
  content: '';
  position: absolute;
  top: 0;
  right: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.4), transparent);
  animation: wave-flow-right 4s ease-in-out infinite;
}

@keyframes wave-flow-right {
  0% {
    right: -100%;
  }
  50% {
    right: 100%;
  }
  100% {
    right: 100%;
  }
}

.info-right:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 40px rgba(255, 154, 158, 0.35);
}

.count-info {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.count-number {
  font-size: 2.5rem;
  font-weight: 700;
  color: #ffffff;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

.count-label {
  font-size: 1rem;
  color: rgba(255, 255, 255, 0.9);
  margin-top: 8px;
  font-weight: 500;
}

/* 搜索和筛选区域 */
.search-filter-section {
  padding: 35px 30px 30px 30px;
}

.search-row {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 15px;
  flex-wrap: wrap;
}

.search-item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 200px;
}

.search-label {
  font-size: 0.9rem;
  color: #2c3e50;
  font-weight: 500;
  white-space: nowrap;
  min-width: 90px;
  text-align: right;
}

.search-input-small {
  flex: 1;
  min-width: 150px;
}

.filter-item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 150px;
}

.search-actions {
  display: flex;
  gap: 10px;
  margin-left: auto;
}

/* 颜色选择器样式 */
.color-picker-wrapper {
  min-width: 150px;
}

.color-picker-wrapper :deep(.vc-color-picker) {
  width: 100% !important;
}

.color-picker-wrapper :deep(.vc-color-picker .vc-color-picker__trigger) {
  width: 100% !important;
  height: 32px !important;
  border-radius: 6px !important;
  border: 1px solid #d9d9d9 !important;
}

.content-section {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 16px;
  padding: 30px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  backdrop-filter: blur(10px);
  min-height: 400px;
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.loading-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 300px;
}

.loading-text {
  margin-top: 20px;
  color: #7f8c8d;
  font-size: 1.1rem;
}

.empty-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}

.empty-content {
  text-align: center;
  padding: 40px;
}

.empty-icon {
  font-size: 4rem;
  color: #bdc3c7;
  margin-bottom: 20px;
}

.empty-title {
  font-size: 1.5rem;
  color: #2c3e50;
  margin-bottom: 10px;
}

.empty-description {
  color: #7f8c8d;
  margin-bottom: 30px;
}

.pictures-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 25px;
  margin-bottom: 30px;
}

.picture-card {
  background: white;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
  cursor: pointer;
}

.picture-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.15);
}

.picture-container {
  position: relative;
  aspect-ratio: 4/3;
  overflow: hidden;
}

.picture-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s ease;
  user-select: none;
  -webkit-user-select: none;
  -moz-user-select: none;
  -ms-user-select: none;
  -webkit-user-drag: none;
  -khtml-user-drag: none;
  -moz-user-drag: none;
  -o-user-drag: none;
  user-drag: none;
  pointer-events: none;
}

.picture-card:hover .picture-image {
  transform: scale(1.05);
}

.picture-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(to bottom, transparent 0%, rgba(0, 0, 0, 0.7) 100%);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 20px;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.picture-card:hover .picture-overlay {
  opacity: 1;
}

.picture-info {
  color: white;
}

.picture-title {
  font-size: 1.1rem;
  font-weight: 600;
  margin: 0 0 8px 0;
  color: white;
}

.picture-meta {
  font-size: 0.9rem;
  color: rgba(255, 255, 255, 0.8);
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-avatar-container {
  display: flex;
  align-items: center;
  cursor: pointer;
}

.user-avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid rgba(255, 255, 255, 0.3);
  transition: border-color 0.3s ease;
}

.user-avatar-container:hover .user-avatar {
  border-color: rgba(255, 255, 255, 0.6);
}

.user-name {
  font-size: 0.9rem;
  color: rgba(255, 255, 255, 0.9);
}

.picture-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

.action-btn {
  color: white !important;
  border: 1px solid rgba(255, 255, 255, 0.3) !important;
  background: rgba(255, 255, 255, 0.1) !important;
}

.action-btn:hover {
  background: rgba(255, 255, 255, 0.2) !important;
  border-color: rgba(255, 255, 255, 0.5) !important;
}

.unlike-btn {
  color: #e74c3c !important;
}

.collected {
  color: #f39c12 !important;
}

.picture-stats {
  display: flex;
  justify-content: space-around;
  padding: 15px;
  background: #f8f9fa;
  border-top: 1px solid #e9ecef;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 0.9rem;
  color: #6c757d;
}

.pagination-container {
  display: flex;
  justify-content: center;
  margin-top: 30px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .liked-pictures-page {
    padding: 10px;
  }
  
  .header-content {
    flex-direction: column;
    text-align: center;
    gap: 20px;
  }
  
  .page-title {
    font-size: 2rem;
  }
  
  .pictures-grid {
    grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
    gap: 15px;
  }
  
  .filter-container {
    justify-content: center;
  }
}
</style>
