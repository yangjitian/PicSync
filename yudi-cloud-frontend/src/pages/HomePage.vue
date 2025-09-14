<template>
  <div id="homePage">
    <!-- 搜索框 -->
    <div class="search-bar">
      <a-input-search
        v-model:value="searchParams.searchText"
        placeholder="从海量图片中搜索"
        enter-button="搜索"
        size="large"
        @search="doSearch"
      />
    </div>
    <!-- 分类和标签筛选 -->
    <a-tabs v-model:active-key="selectedCategory" @change="doSearch">
      <a-tab-pane key="all" tab="全部" />
      <a-tab-pane v-for="category in categoryList" :tab="category" :key="category" />
    </a-tabs>
    <div class="tag-bar">
      <span style="margin-right: 8px">标签：</span>
      <a-space :size="[0, 8]" wrap>
        <a-checkable-tag
          v-for="(tag, index) in tagList"
          :key="tag"
          v-model:checked="selectedTagList[index]"
          @change="doSearch"
        >
          {{ tag }}
        </a-checkable-tag>
      </a-space>
    </div>
    <!-- 图片列表 -->
    <PictureList :dataList="dataList" :loading="loading && searchParams.current === 1" />
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
import { onMounted, onBeforeUnmount, reactive, ref } from 'vue'
import {
  listPictureTagCategoryUsingGet,
  listPictureVoByPageUsingPost,
} from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import PictureList from '@/components/PictureList.vue'

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
const tagList = ref<string[]>([])
const selectedTagList = ref<boolean[]>([])

// --- 数据获取 ---
const fetchData = async () => {
  if (loading.value || noMoreData.value) {
    return
  }
  loading.value = true

  // 构造请求参数
  const params = {
    ...searchParams,
    tags: [] as string[],
  }
  if (selectedCategory.value !== 'all') {
    params.category = selectedCategory.value
  }
  selectedTagList.value.forEach((useTag, index) => {
    if (useTag) {
      params.tags.push(tagList.value[index])
    }
  })

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

const getTagCategoryOptions = async () => {
  const res = await listPictureTagCategoryUsingGet()
  if (res.data.code === 0 && res.data.data) {
    tagList.value = res.data.data.tagList ?? []
    categoryList.value = res.data.data.categoryList ?? []
  } else {
    message.error('获取标签分类列表失败，' + res.data.message)
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

// --- 生命周期钩子 ---
onMounted(() => {
  getTagCategoryOptions()
  fetchData() // 初始加载
  setupObserver()
})

onBeforeUnmount(() => {
  if (observer) {
    observer.disconnect()
  }
})
</script>

<style scoped>
#homePage {
  margin-bottom: 16px;
}

#homePage .search-bar {
  max-width: 480px;
  margin: 0 auto 16px;
}

#homePage .tag-bar {
  margin-bottom: 16px;
}
</style>
