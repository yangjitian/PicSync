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
        <a-card-meta :title="picture.name">
          <template #description>
            <a-flex>
              <a-tag color="green">
                {{ picture.category ?? '默认' }}
              </a-tag>
              <a-tag v-for="tag in picture.tags" :key="tag">
                {{ tag }}
              </a-tag>
            </a-flex>
          </template>
        </a-card-meta>
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
}

const props = withDefaults(defineProps<Props>(), {
  dataList: () => [],
  loading: false,
  showOp: false,
  canEdit: false,
  canDelete: false,
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
  initMasonry()
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
      }
      msnry?.reloadItems()
      layout()
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
</script>

<style scoped>
.picture-list-masonry {
  width: 100%;
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