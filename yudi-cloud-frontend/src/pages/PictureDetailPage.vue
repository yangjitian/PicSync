<template>
  <div id="pictureDetailPage">
    <a-row :gutter="[16, 16]">
      <!-- 图片预览 -->
      <a-col :sm="24" :md="16" :xl="18">
        <a-card title="图片预览">
          <a-image 
            :src="picture.url" 
            style="max-height: 600px; object-fit: contain"
            :preview="{
              mask: '点击预览',
              maskClassName: 'custom-preview-mask'
            }"
            @contextmenu.prevent
            @dragstart.prevent
            @selectstart.prevent
          />
        </a-card>
      </a-col>
      <!-- 图片信息区域 -->
      <a-col :sm="24" :md="8" :xl="6">
        <a-card title="图片信息">
          <a-descriptions :column="1">
            <a-descriptions-item label="作者">
              <a-space>
                <a-avatar :size="24" :src="picture.userVO?.userAvatar" />
                <div>{{ picture.userVO?.userName }}</div>
              </a-space>
            </a-descriptions-item>
            <a-descriptions-item label="名称">
              {{ picture.name ?? '未命名' }}
            </a-descriptions-item>
            <a-descriptions-item label="简介">
              {{ picture.introduction ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="分类">
              {{ picture.category ?? '默认' }}
            </a-descriptions-item>
            <a-descriptions-item label="标签">
              <a-tag v-for="tag in picture.tags" :key="tag">
                {{ tag }}
              </a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="格式">
              {{ picture.picFormat ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="宽度">
              {{ picture.picWidth ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="高度">
              {{ picture.picHeight ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="宽高比">
              {{ picture.picScale ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="大小">
              {{ formatSize(picture.picSize) }}
            </a-descriptions-item>
            <a-descriptions-item label="主色调">
              <a-space>
                {{ picture.picColor ?? '-' }}
                <div
                  v-if="picture.picColor"
                  :style="{
                    width: '16px',
                    height: '16px',
                    backgroundColor: toHexColor(picture.picColor),
                  }"
                />
              </a-space>
            </a-descriptions-item>
            <a-descriptions-item label="下载量">
              {{ (picture as any).downloadCount ?? 0 }}
            </a-descriptions-item>
          </a-descriptions>
          <!-- 图片操作 -->
          <a-space wrap>
            <a-button type="primary" @click="doDownload" :loading="downloadLoading">
              免费下载
              <template #icon>
                <DownloadOutlined />
              </template>
            </a-button>
            <a-button :icon="h(ShareAltOutlined)" type="primary" ghost @click="doShare">
              分享
            </a-button>
            <a-button v-if="canEdit" :icon="h(EditOutlined)" type="default" @click="doEdit">
              编辑
            </a-button>
            <a-button v-if="canDelete" :icon="h(DeleteOutlined)" danger @click="doDelete">
              删除
            </a-button>
          </a-space>
        </a-card>
      </a-col>
    </a-row>
    <ShareModal ref="shareModalRef" title="分享图片" :link="shareLink || ''" />
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, onBeforeUnmount, ref } from 'vue'
import { deletePictureUsingPost, getPictureVoByIdUsingGet, generatePictureShareLinkUsingGet, addPictureShareUsingPost, addPictureDownloadUsingPost } from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import {
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  ShareAltOutlined,
} from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import { downloadImage, formatSize, toHexColor } from '@/utils'
import ShareModal from '@/components/ShareModal.vue'
import { SPACE_PERMISSION_ENUM } from '@/constants/space.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

interface Props {
  id: string | number
}

const props = defineProps<Props>()
const picture = ref<API.PictureVO>({})
const downloadLoading = ref(false)

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

// 获取图片详情
const fetchPictureDetail = async () => {
  try {
    const res = await getPictureVoByIdUsingGet({
      id: props.id,
    })
    if (res.data.code === 0 && res.data.data) {
      picture.value = res.data.data
    } else {
      message.error('获取图片详情失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('获取图片详情失败：' + e.message)
  }
}

onMounted(() => {
  fetchPictureDetail()
  
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
    
    // 根据删除响应决定跳转目标
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
  
  try {
    downloadLoading.value = true
    
    // 先调用下载量统计接口
    const res = await addPictureDownloadUsingPost(picture.value.id)
    
    if (res.data.code === 0) {
      // 下载量统计成功，执行实际下载
      downloadImage(picture.value.url, picture.value.name || '图片')
      
      // 更新图片的下载量显示
      if (res.data.data && res.data.data.downloadCount !== undefined) {
        (picture.value as any).downloadCount = res.data.data.downloadCount
      }
      
      message.success('下载成功')
    } else {
      message.error('下载失败: ' + res.data.message)
    }
  } catch (error: any) {
    console.error('下载失败:', error)
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
    console.log('分享计数API响应:', shareRes)
    
    if (shareRes.data.code === 0) {
      // 分享计数成功，再生成分享链接
      const linkRes = await generatePictureShareLinkUsingGet(picture.value.id)
      
      if (linkRes.data.code === 0) {
        // 分享链接在message字段中，而不是data字段
        const shareLinkFromServer = linkRes.data.message
        
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
</script>

<style scoped>
#pictureDetailPage {
  margin-bottom: 16px;
}

/* 禁用图片的右键菜单、拖拽和选择 */
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
  user-drag: none;
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
  user-drag: none;
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
