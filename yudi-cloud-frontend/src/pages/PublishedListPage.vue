<template>
  <div id="publishedListPage" :key="componentKey">
    <!-- 页面标题和操作按钮 -->
    <a-flex justify="space-between">
      <h2>我的发布</h2>
      <a-space>
        <a-button type="primary" @click="goToAddPicture">
          + 发布图片
        </a-button>
      </a-space>
    </a-flex>
    <div style="margin-bottom: 16px" />
    
    <!-- 搜索表单 -->
    <a-form layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item label="关键词" name="searchText">
        <a-input
          v-model:value="searchParams.searchText"
          placeholder="从名称和简介搜索"
          allow-clear
        />
      </a-form-item>
      <a-form-item name="category" label="类型">
        <a-auto-complete
          v-model:value="searchParams.category"
          style="min-width: 180px"
          placeholder="请输入类型"
          :options="categoryOptions"
          allow-clear
        />
      </a-form-item>
      <a-form-item name="tags" label="标签">
        <a-select
          v-model:value="searchParams.tags"
          style="min-width: 180px"
          mode="tags"
          placeholder="请输入标签"
          :options="tagOptions"
          allow-clear
        />
      </a-form-item>
      <a-form-item name="reviewStatus" label="审核状态">
        <a-select
          v-model:value="searchParams.reviewStatus"
          style="min-width: 180px"
          placeholder="请选择审核状态"
          :options="PIC_REVIEW_STATUS_OPTIONS"
          allow-clear
        />
      </a-form-item>
      <a-form-item>
        <a-space>
          <a-button type="primary" html-type="submit">搜索</a-button>
          <a-button html-type="reset" @click="doClear">重置</a-button>
        </a-space>
      </a-form-item>
    </a-form>
    <div style="margin-bottom: 16px" />
    
    <!-- 图片列表 -->
    <PictureList
      :dataList="dataList"
      :loading="loading"
      :showOp="true"
      :canEdit="true"
      :canDelete="true"
      :onReload="fetchData"
      layoutMode="simple"
      displayMode="grid"
      @picture-click="handlePictureClick"
    />
    
    <!-- 分页 -->
    <a-pagination
      style="text-align: right; margin-top: 16px"
      v-model:current="searchParams.current"
      v-model:pageSize="searchParams.pageSize"
      :total="total"
      :show-size-changer="true"
      :show-quick-jumper="true"
        :page-size-options="['8', '12', '16', '20']"
      :show-total="(total: number, range: [number, number]) => `第 ${range[0]}-${range[1]} 条/共 ${total} 条`"
      @change="onPageChange"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onActivated, reactive, ref, watch, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { 
  listPublishedPictureVoByPageUsingPost,
  listPictureTagCategoryUsingGet
} from '@/api/pictureController.ts'
import { 
  PIC_REVIEW_STATUS_OPTIONS
} from '@/constants/picture.ts'
import PictureList from '@/components/PictureList.vue'
import { onPictureUploaded } from '@/utils/crossPageCommunication'

const router = useRouter()
const route = useRoute()
const loginUserStore = useLoginUserStore()

// 添加一个强制重新渲染的 key，基于路由参数
const componentKey = computed(() => {
  return `published-list-${route.path}-${Date.now()}`
})

// 定义数据
const dataList = ref<API.PictureVO[]>([])
const total = ref(0)
const loading = ref(false)

// 搜索条件
const searchParams = reactive<API.PictureQueryRequest>({
  current: 1,
  pageSize: 12,
  sortField: 'createTime',
  sortOrder: 'descend',
})

// 标签和分类选项
const categoryOptions = ref<Array<{label: string, value: string}>>([])
const tagOptions = ref<Array<{label: string, value: string}>>([])

// 获取数据
const fetchData = async () => {
  loading.value = true
  try {
    const res = await listPublishedPictureVoByPageUsingPost(searchParams)
    if (res.data.code === 0 && res.data.data) {
      dataList.value = res.data.data.records || []
      total.value = res.data.data.total || 0
    } else {
      message.error('获取我的发布失败，' + res.data.message)
    }
  } catch (error: any) {
    message.error('获取我的发布失败，' + error.message)
  } finally {
    loading.value = false
  }
}

// 搜索
const doSearch = () => {
  searchParams.current = 1
  fetchData()
}

// 重置搜索条件
const doClear = () => {
  // 清空所有搜索条件
  Object.keys(searchParams).forEach((key) => {
    if (key !== 'current' && key !== 'pageSize' && key !== 'sortField' && key !== 'sortOrder') {
      (searchParams as any)[key] = undefined
    }
  })
  searchParams.current = 1
  fetchData()
}

// 分页变化
const onPageChange = (page: number, pageSize: number) => {
  searchParams.current = page
  searchParams.pageSize = pageSize
  fetchData()
}

// 跳转到创建图片页面
const goToAddPicture = () => {
  router.push('/add_picture')
}

/**
 * 获取标签和分类选项
 */
const getTagCategoryOptions = async () => {
  try {
    const res = await listPictureTagCategoryUsingGet()
    if (res.data.code === 0 && res.data.data) {
      // 修复：将字符串数组转换为Select组件需要的对象数组格式
      tagOptions.value = (res.data.data.tagList ?? []).map((data: string) => ({
        label: data,
        value: data
      }))
      categoryOptions.value = (res.data.data.categoryList ?? []).map((data: string) => ({
        label: data,
        value: data
      }))
    } else {
      message.error('获取标签分类列表失败，' + res.data.message)
    }
  } catch (error: any) {
    message.error('获取标签分类列表失败，' + error.message)
  }
}

// 检查用户登录状态
const checkLogin = () => {
  const loginUser = loginUserStore.loginUser
  if (!loginUser?.id) {
    message.warn('请先登录')
    router.push('/user/login')
    return false
  }
  return true
}

// 页面加载时获取数据
onMounted(() => {
  if (checkLogin()) {
    // 使用 nextTick 确保组件完全挂载
    nextTick(() => {
      getTagCategoryOptions()
      fetchData()
      // 发送页面刷新事件
      sendPageRefreshEvent()
    })
  }
  
  // 监听图片上传事件
  onPictureUploaded(handlePictureUploaded)
})

// 监听路由变化，重新加载数据
watch(() => route.path, (newPath, oldPath) => {
  if (checkLogin()) {
    // 使用 nextTick 确保路由已经更新
    nextTick(() => {
      fetchData()
    })
  }
})

// 监听完整路由变化，作为备用方案
watch(
  () => route.fullPath,
  (newPath, oldPath) => {
    if (newPath !== oldPath && newPath === '/published_list') {
      // 使用 nextTick 确保路由已经更新
      nextTick(() => {
        if (checkLogin()) {
          fetchData()
        }
      })
    }
  }
)

// 监听路由变化，更新高亮菜单项（参考 FloatingSidebar 的实现）
router.afterEach((to, from) => {
  
  // 如果是我的发布页面的路由变化，重新获取数据
  if (to.path === '/published_list' && to.path !== from.path) {
    // 使用 nextTick 确保路由已经更新
    nextTick(() => {
      if (checkLogin()) {
        fetchData()
      }
    })
  }
})

// 添加 onActivated 钩子，用于处理组件被激活时的情况
onActivated(() => {
  // 强制刷新数据
  nextTick(() => {
    if (checkLogin()) {
      fetchData()
    }
  })
})

// 添加强制刷新函数，供外部调用
const forceRefresh = () => {
  if (checkLogin()) {
    fetchData()
  }
}

// 将刷新函数暴露到全局，供其他组件调用
;(window as any).refreshPublishedList = forceRefresh

// 发送页面刷新事件
const sendPageRefreshEvent = () => {
  // 使用 nextTick 确保 DOM 更新完成后再发送事件
  nextTick(() => {
    window.dispatchEvent(new CustomEvent('refreshPublishedListPage'))
  })
}

// 处理图片上传事件
const handlePictureUploaded = (event: any) => {
  const { pictureId, data: pictureData } = event
  console.log('PublishedListPage 收到图片上传事件:', pictureId, pictureData)
  
  // 刷新数据以显示新上传的图片
  if (checkLogin()) {
    nextTick(() => {
      fetchData()
      console.log('PublishedListPage 已刷新数据')
    })
  }
}

// --- 图片点击处理 ---
const handlePictureClick = async (picture: API.PictureVO) => {
  // 跳转到详情页
  window.open(`/picture/${picture.id}`, '_blank')
}
</script>

<style scoped>
#publishedListPage {
  padding: 24px;
}

.ant-form-item {
  margin-bottom: 16px;
}
</style>