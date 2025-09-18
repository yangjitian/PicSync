<template>
  <div id="globalSider">
    <a-layout-sider
      v-if="loginUserStore.loginUser.id"
      width="200"
      breakpoint="lg"
      collapsed-width="0"
    >
      <a-menu
        v-model:selectedKeys="current"
        mode="inline"
        :items="menuItems"
        @click="doMenuClick"
      />
    </a-layout-sider>
  </div>
</template>
<script lang="ts" setup>
import { computed, h, ref, watchEffect, onMounted, onUnmounted, nextTick } from 'vue'
import { PictureOutlined, TeamOutlined, UserOutlined, FileTextOutlined } from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { SPACE_TYPE_ENUM } from '@/constants/space.ts'
import { listMyTeamSpaceUsingPost } from '@/api/spaceUserController.ts'
import { message } from 'ant-design-vue'

const loginUserStore = useLoginUserStore()

// 固定的菜单列表
const fixedMenuItems = [
  {
    key: '/',
    icon: () => h(PictureOutlined),
    label: '公共图库',
  },
  {
    key: '/my_space',
    label: '我的空间',
    icon: () => h(UserOutlined),
  },
  {
    key: '/published_list',
    label: '发布列表',
    icon: () => h(FileTextOutlined),
  },
  {
    key: '/add_space?type=' + SPACE_TYPE_ENUM.TEAM,
    label: '创建团队',
    icon: () => h(TeamOutlined),
  },
]

const teamSpaceList = ref<API.SpaceUserVO[]>([])
const menuItems = computed(() => {
  
  // 如果用户没有团队空间，则只展示固定菜单（包含"创建团队"）
  if (teamSpaceList.value.length < 1) {
    return fixedMenuItems
  }
  
  // 如果用户有团队空间，则展示固定菜单和团队空间菜单
  // 分离我创建的团队空间和我加入的团队空间
  const createdSpaces = teamSpaceList.value.filter(spaceUser => 
    spaceUser.space?.userId === loginUserStore.loginUser.id
  )
  const joinedSpaces = teamSpaceList.value.filter(spaceUser => 
    spaceUser.space?.userId !== loginUserStore.loginUser.id
  )
  
  
  // 构建团队空间菜单
  const teamSpaceMenus = []
  
  // 我创建的团队空间子菜单
  if (createdSpaces.length > 0) {
    const createdSubMenus = createdSpaces.map((spaceUser) => {
      const space = spaceUser.space
      return {
        key: '/space/' + spaceUser.spaceId,
        label: space?.spaceName,
      }
    })
    teamSpaceMenus.push({
      key: 'my-created-spaces',
      label: '我创建的团队空间',
      children: createdSubMenus,
    })
  }
  
  // 我加入的团队空间子菜单
  if (joinedSpaces.length > 0) {
    const joinedSubMenus = joinedSpaces.map((spaceUser) => {
      const space = spaceUser.space
      return {
        key: '/space/' + spaceUser.spaceId,
        label: space?.spaceName,
      }
    })
    teamSpaceMenus.push({
      key: 'my-joined-spaces',
      label: '我加入的团队空间',
      children: joinedSubMenus,
    })
  }
  
  // 团队空间一级菜单
  const teamSpaceMenuGroup = {
    key: 'teamSpace',
    label: '团队空间',
    icon: () => h(TeamOutlined),
    children: teamSpaceMenus,
  }
  
  // 移除固定菜单中的"创建团队"，因为现在有团队空间了
  const filteredFixedMenus = fixedMenuItems.filter(item => 
    !item.key.includes('/add_space?type=')
  )
  
  return [...filteredFixedMenus, teamSpaceMenuGroup]
})

// 加载团队空间列表
const fetchTeamSpaceList = async () => {
  const res = await listMyTeamSpaceUsingPost()
  if (res.data.code === 0 && res.data.data) {
    teamSpaceList.value = res.data.data
  } else {
    message.error('加载我的团队空间失败，' + res.data.message)
  }
}

/**
 * 监听变量，改变时触发数据的重新加载
 */
watchEffect(() => {
  // 登录才加载
  if (loginUserStore.loginUser.id) {
    fetchTeamSpaceList()
  }
})

// 监听菜单刷新事件
const handleMenuRefresh = async () => {
  if (loginUserStore.loginUser.id) {
    await fetchTeamSpaceList()
    await nextTick()
    
    // 强制触发菜单重新计算
    teamSpaceList.value = [...teamSpaceList.value]
  } else {
  }
}

// 将刷新函数暴露到全局，供其他组件调用
window.refreshTeamSpaceMenu = handleMenuRefresh

// 在组件挂载时添加事件监听器
onMounted(() => {
  window.addEventListener('refreshTeamSpaceMenu', handleMenuRefresh)
  
})

onUnmounted(() => {
  window.removeEventListener('refreshTeamSpaceMenu', handleMenuRefresh)
  delete window.refreshTeamSpaceMenu
})

const router = useRouter()
// 当前要高亮的菜单项
const current = ref<string[]>([])
// 监听路由变化，更新高亮菜单项
router.afterEach((to, from, next) => {
  current.value = [to.path]
})

// 路由跳转事件
const doMenuClick = ({ key }) => {
  
  // 如果点击的是当前路由，强制刷新
  if (router.currentRoute.value.path === key) {
    // 使用 replace 来强制重新渲染当前页面
    router.replace({ path: key, query: { t: Date.now() } }).then(() => {
      // 清理查询参数
      nextTick(() => {
        router.replace({ path: key })
      })
    })
    return
  }
  
  // 正常路由跳转
  router.push(key).then(() => {
  }).catch((error) => {
  })
}
</script>

<style scoped>
#globalSider .ant-layout-sider {
  background: none;
}
</style>
