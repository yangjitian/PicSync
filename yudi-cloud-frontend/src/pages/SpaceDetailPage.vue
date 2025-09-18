<template>
  <div id="spaceDetailPage" :key="componentKey">
    <!-- 空间信息 -->
    <a-flex justify="space-between">
      <h2>{{ space.spaceName }}（{{ SPACE_TYPE_MAP[space.spaceType] }}）</h2>
      <a-space size="middle">
        <a-button
          v-if="canUploadPicture"
          type="primary"
          @click="goToAddPicture"
        >
          + 创建图片
        </a-button>
        <a-button
          v-if="canManageSpaceUser"
          type="primary"
          ghost
          :icon="h(TeamOutlined)"
          @click="goToSpaceUserManage"
        >
          成员管理
        </a-button>
        <a-button
          v-if="canManageSpaceUser"
          type="primary"
          ghost
          :icon="h(BarChartOutlined)"
          @click="goToSpaceAnalyze"
        >
          空间分析
        </a-button>
        <a-button v-if="canEditPicture" :icon="h(EditOutlined)" @click="doBatchEdit"> 批量编辑</a-button>
        <a-tooltip
          :title="`占用空间 ${formatSize(space.totalSize)} / ${formatSize(space.maxSize)}`"
        >
          <a-progress
            type="circle"
            :size="42"
            :percent="((space.totalSize * 100) / space.maxSize).toFixed(1)"
          />
        </a-tooltip>
      </a-space>
    </a-flex>
    <div style="margin-bottom: 16px" />
    <!-- 搜索表单 -->
    <PictureSearchForm :onSearch="onSearch" />
    <div style="margin-bottom: 16px" />
    <!-- 按颜色搜索，跟其他搜索条件独立 -->
    <a-form-item label="按颜色搜索">
      <color-picker format="hex" @pureColorChange="onColorChange" />
    </a-form-item>
    <!-- 图片列表 -->
    <PictureList
      :dataList="dataList"
      :loading="loading"
      :showOp="true"
      :canEdit="canEditPicture"
      :canDelete="canDeletePicture"
      :onReload="fetchData"
      layoutMode="detailed"
      @picture-click="handlePictureClick"
    />
    <!-- 分页 -->
    <a-pagination
      style="text-align: right"
      v-model:current="searchParams.current"
      v-model:pageSize="searchParams.pageSize"
      :total="total"
      @change="onPageChange"
    />
    <BatchEditPictureModal
      ref="batchEditPictureModalRef"
      :spaceId="id"
      :pictureList="dataList"
      :onSuccess="onBatchEditPictureSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, onActivated, ref, watch, nextTick } from 'vue'
import { getSpaceVoByIdUsingGet } from '@/api/spaceController.ts'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  listPictureVoByPageUsingPost,
  searchPictureByColorUsingPost,
} from '@/api/pictureController.ts'
import { formatSize } from '@/utils'
import PictureList from '@/components/PictureList.vue'
import PictureSearchForm from '@/components/PictureSearchForm.vue'
import { ColorPicker } from 'vue3-colorpicker'
import 'vue3-colorpicker/style.css'
import BatchEditPictureModal from '@/components/BatchEditPictureModal.vue'
import { BarChartOutlined, EditOutlined, TeamOutlined } from '@ant-design/icons-vue'
import { SPACE_PERMISSION_ENUM, SPACE_TYPE_MAP } from '../constants/space.ts'

interface Props {
  id: string | number
}

const props = defineProps<Props>()
const router = useRouter()
const route = useRoute()
const space = ref<API.SpaceVO>({})

// 添加一个强制重新渲染的 key，基于路由参数
const componentKey = computed(() => {
  return `space-${props.id}-${route.params.id}-${Date.now()}`
})

// 通用权限检查函数
function createPermissionChecker(permission: string) {
  return computed(() => {
    return (space.value.permissionList ?? []).includes(permission)
  })
}

// 定义权限检查
const canManageSpaceUser = createPermissionChecker(SPACE_PERMISSION_ENUM.SPACE_USER_MANAGE)
const canUploadPicture = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_UPLOAD)
const canEditPicture = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_EDIT)
const canDeletePicture = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_DELETE)

// -------- 获取空间详情 --------
const fetchSpaceDetail = async () => {
  try {
    const res = await getSpaceVoByIdUsingGet({
      id: props.id,
    })
    if (res.data.code === 0 && res.data.data) {
      space.value = res.data.data
    } else {
      message.error('获取空间详情失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('获取空间详情失败：' + e.message)
  }
}

onMounted(() => {
  // 使用 nextTick 确保 props.id 已经正确设置
  nextTick(() => {
    if (props.id) {
      fetchSpaceDetail()
      fetchData()
      // 发送页面刷新事件
      sendPageRefreshEvent()
    }
  })
})

// --------- 获取图片列表 --------

// 定义数据
const dataList = ref<API.PictureVO[]>([])
const total = ref(0)
const loading = ref(true)

// 搜索条件
const searchParams = ref<API.PictureQueryRequest>({
  current: 1,
  pageSize: 12,
  sortField: 'createTime',
  sortOrder: 'descend',
})

// 获取数据
const fetchData = async () => {
  loading.value = true
  // 转换搜索参数
  const params = {
    spaceId: props.id,
    ...searchParams.value,
  }
  const res = await listPictureVoByPageUsingPost(params)
  if (res.data.code === 0 && res.data.data) {
    dataList.value = res.data.data.records ?? []
    total.value = res.data.data.total ?? 0
  } else {
    message.error('获取数据失败，' + res.data.message)
  }
  loading.value = false
}

// 分页参数
const onPageChange = (page: number, pageSize: number) => {
  searchParams.value.current = page
  searchParams.value.pageSize = pageSize
  fetchData()
}

// 搜索
const onSearch = (newSearchParams: API.PictureQueryRequest) => {

  searchParams.value = {
    ...searchParams.value,
    ...newSearchParams,
    current: 1,
  }
  fetchData()
}

// 按照颜色搜索
const onColorChange = async (color: string) => {
  loading.value = true
  const res = await searchPictureByColorUsingPost({
    picColor: color,
    spaceId: props.id,
  })
  if (res.data.code === 0 && res.data.data) {
    const data = res.data.data ?? []
    dataList.value = data
    total.value = data.length
  } else {
    message.error('获取数据失败，' + res.data.message)
  }
  loading.value = false
}

// ---- 批量编辑图片 -----
const batchEditPictureModalRef = ref()

// 批量编辑图片成功
const onBatchEditPictureSuccess = () => {
  fetchData()
}

// 打开批量编辑图片弹窗
const doBatchEdit = () => {
  if (batchEditPictureModalRef.value) {
    batchEditPictureModalRef.value.openModal()
  }
}

// 跳转到创建图片页面
const goToAddPicture = () => {
  router.push(`/add_picture?spaceId=${props.id}`)
}

// 跳转到空间成员管理页面
const goToSpaceUserManage = () => {
  router.push(`/spaceUserManage/${props.id}`)
}

// 跳转到空间分析页面
const goToSpaceAnalyze = () => {
  router.push(`/space_analyze?spaceId=${props.id}`)
}

// --- 图片点击处理 ---
const handlePictureClick = async (picture: API.PictureVO) => {
  // 跳转到详情页
  window.open(`/picture/${picture.id}`, '_blank')
}

// 空间 id 改变时，必须重新获取数据
watch(
  () => props.id,
  (newSpaceId, oldSpaceId) => {
    if (newSpaceId && newSpaceId !== oldSpaceId) {
      // 使用 nextTick 确保 props.id 已经更新
      nextTick(() => {
        fetchSpaceDetail()
        fetchData()
      })
    }
  },
  { immediate: true }
)

// 监听路由变化，确保路由参数变化时重新获取数据
watch(
  () => route.params.id,
  (newSpaceId, oldSpaceId) => {
    if (newSpaceId && newSpaceId !== oldSpaceId) {
      // 使用 nextTick 确保路由参数已经更新
      nextTick(() => {
        fetchSpaceDetail()
        fetchData()
      })
    }
  },
  { immediate: true }
)

// 监听完整路由变化，作为备用方案
watch(
  () => route.fullPath,
  (newPath, oldPath) => {
    if (newPath !== oldPath && newPath.startsWith('/space/')) {
      // 使用 nextTick 确保路由已经更新
      nextTick(() => {
        fetchSpaceDetail()
        fetchData()
      })
    }
  }
)

// 监听路由变化，更新高亮菜单项（参考 FloatingSidebar 的实现）
router.afterEach((to, from) => {
  
  // 如果是空间详情页面的路由变化，重新获取数据
  if (to.path.startsWith('/space/') && to.path !== from.path) {
    // 使用 nextTick 确保路由已经更新
    nextTick(() => {
      fetchSpaceDetail()
      fetchData()
    })
  }
})

// 添加 onActivated 钩子，用于处理组件被激活时的情况
onActivated(() => {
  // 强制刷新数据
  nextTick(() => {
    fetchSpaceDetail()
    fetchData()
  })
})

// 添加强制刷新函数，供外部调用
const forceRefresh = () => {
  fetchSpaceDetail()
  fetchData()
}

// 将刷新函数暴露到全局，供其他组件调用
;(window as any).refreshSpaceDetail = forceRefresh

// 发送页面刷新事件
const sendPageRefreshEvent = () => {
  // 使用 nextTick 确保 DOM 更新完成后再发送事件
  nextTick(() => {
    window.dispatchEvent(new CustomEvent('refreshSpaceDetailPage'))
  })
}

</script>

<style scoped>
#spaceDetailPage {
  margin-bottom: 16px;
}
</style>
