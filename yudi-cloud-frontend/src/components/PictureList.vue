<template>
  <div v-show="dataList.length > 0" ref="grid" class="picture-list-masonry">
    <!-- Sizer element for column width -->
    <div class="grid-sizer"></div>
    <div v-for="picture in dataList" :key="picture.id" class="grid-item">
      <a-card hoverable class="picture-card" @click="doClickPicture(picture)">
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
              <a-avatar :size="24" :src="picture.userVO?.userAvatar || getDefaultAvatar(picture.userVO?.userName)">
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
              <!-- 点赞数 -->
              <div class="stat-item">
                <a-space size="small">
                  <HeartOutlined class="stat-icon" />
                  <span class="stat-text">{{ picture.likeCount || 0 }}</span>
                </a-space>
              </div>
              <!-- 收藏数 -->
              <div class="stat-item">
                <a-space size="small">
                  <StarOutlined class="stat-icon" />
                  <span class="stat-text">{{ picture.collectCount || 0 }}</span>
                </a-space>
              </div>
              <!-- 分享数 -->
              <div class="stat-item">
                <a-space size="small">
                  <ShareIcon class="stat-icon" />
                  <span class="stat-text">{{ picture.shareCount || 0 }}</span>
                </a-space>
              </div>
              <!-- 以图搜图 -->
              <div class="stat-item search-icon-container">
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
  <ShareModal ref="shareModalRef" :link="shareLink" />
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
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
  StarOutlined,
  ShareAltOutlined as ShareIcon,
} from '@ant-design/icons-vue'
import { deletePictureUsingPost } from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import ShareModal from '@/components/ShareModal.vue'

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
    msnry?.layout()
  })
}

onMounted(() => {
  nextTick(() => {
    initMasonry()
    // 确保在组件挂载后重新布局
    setTimeout(() => {
      if (msnry) {
        msnry.layout()
      }
    }, 100)
  })
})

onBeforeUnmount(() => {
  msnry?.destroy()
})

watch(
  () => props.dataList,
  () => {
    nextTick(() => {
      if (!msnry) {
        initMasonry()
      } else {
        // 重新加载项目并重新布局
        msnry.reloadItems()
        layout()
      }
    })
  },
  { deep: true }
)

// --- Router and Actions ---
const router = useRouter()

const doClickPicture = (picture: API.PictureVO) => {
  router.push({ path: `/picture/${picture.id}` })
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

const handleAuthorClick = (user: API.UserVO, e: Event) => {
  e.stopPropagation()
  if (user?.id) {
    router.push(`/user/${user.id}`)
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
}

.stat-text {
  font-size: 12px;
  color: #8c8c8c;
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