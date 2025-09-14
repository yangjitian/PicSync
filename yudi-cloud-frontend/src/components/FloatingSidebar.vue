<template>
  <div class="floating-sidebar" :class="{ 'expanded': isExpanded }">
    <!-- 收起状态的触发按钮 -->
    <div 
      class="sidebar-trigger" 
      @mouseenter="expandSidebar"
      @mouseleave="collapseSidebar"
    >
      <div class="trigger-icon">
        <LeftOutlined v-if="!isExpanded" />
        <RightOutlined v-else />
      </div>
    </div>

    <!-- 展开的菜单内容 -->
    <div 
      class="sidebar-content"
      @mouseenter="expandSidebar"
      @mouseleave="collapseSidebar"
    >
      <div class="sidebar-header">
        <div class="header-title">
          <MenuOutlined class="title-icon" />
          <span class="title-text">菜单栏</span>
        </div>
      </div>
      
      <div class="sidebar-menu">
        <a-menu
          v-model:selectedKeys="current"
          mode="inline"
          :items="menuItems"
          @click="doMenuClick"
          class="custom-menu"
          :class="{ 'menu-selected': current.length > 0 }"
          :forceSubMenuRender="false"
        />
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, h, ref, watchEffect, onMounted, onUnmounted, nextTick } from 'vue'
import { 
  MenuOutlined, 
  TeamOutlined, 
  UserOutlined, 
  LeftOutlined, 
  RightOutlined,
  PlusOutlined,
  UsergroupAddOutlined,
  PictureOutlined,
  FileTextOutlined
} from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { SPACE_TYPE_ENUM } from '@/constants/space.ts'
import { listMyTeamSpaceUsingPost } from '@/api/spaceUserController.ts'
import { message } from 'ant-design-vue'

const loginUserStore = useLoginUserStore()
const router = useRouter()

// 菜单展开状态
const isExpanded = ref(false)
const expandTimer = ref<number | null>(null)
const collapseTimer = ref<number | null>(null)

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
  
  // 我创建的团队空间子菜单 - 始终显示
  const createdSubMenus = createdSpaces.length > 0 
    ? createdSpaces.map((spaceUser) => {
        const space = spaceUser.space
        return {
          key: '/space/' + spaceUser.spaceId,
          label: space?.spaceName,
        }
      })
    : [{
        key: 'empty-created-spaces',
        label: '暂无创建的团队空间',
        disabled: true,
        style: { color: '#999', fontStyle: 'italic' }
      }]
  
  teamSpaceMenus.push({
    key: 'my-created-spaces',
    label: '我创建的团队空间',
    icon: () => h(PlusOutlined),
    children: createdSubMenus,
  })
  
  // 我加入的团队空间子菜单 - 始终显示
  const joinedSubMenus = joinedSpaces.length > 0 
    ? joinedSpaces.map((spaceUser) => {
        const space = spaceUser.space
        return {
          key: '/space/' + spaceUser.spaceId,
          label: space?.spaceName,
        }
      })
    : [{
        key: 'empty-joined-spaces',
        label: '暂无加入的团队空间',
        disabled: true,
        style: { color: '#999', fontStyle: 'italic' }
      }]
  
  teamSpaceMenus.push({
    key: 'my-joined-spaces',
    label: '我加入的团队空间',
    icon: () => h(UsergroupAddOutlined),
    children: joinedSubMenus,
  })
  
  // 始终创建团队空间一级菜单
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

// 展开侧边栏
const expandSidebar = () => {
  if (collapseTimer.value) {
    clearTimeout(collapseTimer.value)
    collapseTimer.value = null
  }
  
  if (expandTimer.value) {
    clearTimeout(expandTimer.value)
  }
  
  expandTimer.value = setTimeout(() => {
    isExpanded.value = true
    window.dispatchEvent(new CustomEvent('sidebar-hover-start'));
  }, 100)
}

// 收起侧边栏
const collapseSidebar = () => {
  if (expandTimer.value) {
    clearTimeout(expandTimer.value)
    expandTimer.value = null
  }
  
  if (collapseTimer.value) {
    clearTimeout(collapseTimer.value)
  }
  
  collapseTimer.value = setTimeout(() => {
    isExpanded.value = false
    window.dispatchEvent(new CustomEvent('sidebar-hover-end'));
  }, 300)
}

// 加载团队空间列表
const fetchTeamSpaceList = async () => {
  const res = await listMyTeamSpaceUsingPost()
  if (res.data.code === 0 && res.data.data) {
    teamSpaceList.value = res.data.data
  } else {
    message.error('加载我的团队空间失败，' + res.data.message)
    console.error('加载团队空间失败:', res.data.message)
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

// 重试刷新函数
const retryRefresh = (refreshFunctionName: string, maxRetries = 5, delay = 200) => {
  let retries = 0
  
  const attemptRefresh = () => {
    if ((window as any)[refreshFunctionName]) {
      ;(window as any)[refreshFunctionName]()
      return
    }
    
    retries++
    if (retries < maxRetries) {
      setTimeout(attemptRefresh, delay)
    } else {
      console.warn(`FloatingSidebar: ${refreshFunctionName} 函数在 ${maxRetries} 次重试后仍然不存在`)
    }
  }
  
  attemptRefresh()
}

// 监听页面刷新事件
const handlePageRefresh = async (event: Event) => {
  
  // 根据事件类型调用对应的刷新函数
  switch (event.type) {
    case 'refreshSpaceDetailPage':
      if ((window as any).refreshSpaceDetail) {
        (window as any).refreshSpaceDetail()
      } else {
        console.warn('FloatingSidebar: refreshSpaceDetail 函数不存在，开始重试')
        retryRefresh('refreshSpaceDetail')
      }
      break
    case 'refreshPublishedListPage':
      if ((window as any).refreshPublishedList) {
        (window as any).refreshPublishedList()
      } else {
        console.warn('FloatingSidebar: refreshPublishedList 函数不存在，开始重试')
        retryRefresh('refreshPublishedList')
      }
      break
    case 'refreshMySpacePage':
      if ((window as any).refreshMySpace) {
        (window as any).refreshMySpace()
      } else {
        console.warn('FloatingSidebar: refreshMySpace 函数不存在，开始重试')
        retryRefresh('refreshMySpace')
      }
      break
    default:
  }
}

// 将刷新函数暴露到全局，供其他组件调用
;(window as any).refreshTeamSpaceMenu = handleMenuRefresh

// 在组件挂载时添加事件监听器
onMounted(() => {
  
  // 添加菜单刷新事件监听器
  window.addEventListener('refreshTeamSpaceMenu', handleMenuRefresh)
  
  // 添加页面刷新事件监听器
  window.addEventListener('refreshSpaceDetailPage', handlePageRefresh)
  window.addEventListener('refreshPublishedListPage', handlePageRefresh)
  window.addEventListener('refreshMySpacePage', handlePageRefresh)
  
  // 确保事件监听器在全局范围内可用
  
  // 测试事件监听器是否正常工作
  setTimeout(() => {
    window.dispatchEvent(new CustomEvent('refreshTeamSpaceMenu'))
  }, 1000)
})

onUnmounted(() => {
  window.removeEventListener('refreshTeamSpaceMenu', handleMenuRefresh)
  
  // 移除页面刷新事件监听器
  window.removeEventListener('refreshSpaceDetailPage', handlePageRefresh)
  window.removeEventListener('refreshPublishedListPage', handlePageRefresh)
  window.removeEventListener('refreshMySpacePage', handlePageRefresh)
  
  delete (window as any).refreshTeamSpaceMenu
  
  // 清理定时器
  if (expandTimer.value) {
    clearTimeout(expandTimer.value)
  }
  if (collapseTimer.value) {
    clearTimeout(collapseTimer.value)
  }
})

// 当前要高亮的菜单项
const current = ref<string[]>([])

// 初始化菜单高亮状态
const initMenuHighlight = () => {
  const currentPath = router.currentRoute.value.path
  
  // 精确匹配路由
  if (currentPath === '/my_space') {
    current.value = ['/my_space']
  } else if (currentPath.startsWith('/space/')) {
    // 团队空间子菜单，直接使用当前路径作为key
    current.value = [currentPath]
  } else {
    current.value = [currentPath]
  }
  
}

// 页面加载时初始化
onMounted(() => {
  initMenuHighlight()
})

// 监听路由变化，更新高亮菜单项
router.afterEach((to, from) => {
  
  // 精确匹配路由
  if (to.path === '/my_space') {
    current.value = ['/my_space']
  } else if (to.path.startsWith('/space/')) {
    // 团队空间子菜单，直接使用当前路径作为key
    current.value = [to.path]
  } else {
    current.value = [to.path]
  }
  
  
  // 强制触发响应式更新
  nextTick(() => {
  })
})

// 路由跳转事件
const doMenuClick = ({ key }: { key: string }) => {
  
  // 处理空状态菜单项
  if (key === 'empty-created-spaces') {
    message.info('您还没有创建任何团队空间，点击"创建团队"来创建您的第一个团队空间吧！')
    return
  }
  
  if (key === 'empty-joined-spaces') {
    message.info('您还没有加入任何团队空间，等待团队管理员邀请您加入团队空间')
    return
  }
  
  // 如果点击的是当前路由，强制刷新
  if (router.currentRoute.value.path === key) {
    
    // 根据不同的路由发送对应的刷新事件
    if (key.startsWith('/space/')) {
      window.dispatchEvent(new CustomEvent('refreshSpaceDetailPage'))
    } else if (key === '/published_list') {
      window.dispatchEvent(new CustomEvent('refreshPublishedListPage'))
    } else if (key === '/my_space') {
      window.dispatchEvent(new CustomEvent('refreshMySpacePage'))
    }
    
    // 同时使用 replace 来强制重新渲染当前页面
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
    
    // 路由跳转成功后，延迟发送对应的事件，确保目标页面已经加载
    setTimeout(() => {
      if (key.startsWith('/space/')) {
        window.dispatchEvent(new CustomEvent('refreshSpaceDetailPage'))
      } else if (key === '/published_list') {
        window.dispatchEvent(new CustomEvent('refreshPublishedListPage'))
      } else if (key === '/my_space') {
        window.dispatchEvent(new CustomEvent('refreshMySpacePage'))
      }
    }, 100) // 延迟100ms确保页面完全加载
  }).catch((error) => {
    console.error('路由跳转失败:', error)
  })
}
</script>

<style scoped>
.floating-sidebar {
  position: fixed;
  top: 64px; /* 调整到与导航栏底部对齐 */
  left: 0;
  z-index: 999;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  height: calc(100vh - 64px); /* 从导航栏下方到屏幕底部 */
  display: flex;
  align-items: center;
  /* 调试样式 - 确保容器可见 */
  opacity: 1 !important;
  visibility: visible !important;
  display: flex !important;
}

.sidebar-trigger {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 28px;
  height: 28px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 20px rgba(102, 126, 234, 0.35);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 998;
  border: 2px solid rgba(255, 255, 255, 0.2);
  will-change: transform, left;
  /* 调试样式 - 确保按钮可见 */
  opacity: 1 !important;
  visibility: visible !important;
  display: flex !important;
}

.sidebar-trigger:hover {
  background: linear-gradient(135deg, #5a6fd8 0%, #6a4190 100%);
  box-shadow: 0 8px 30px rgba(102, 126, 234, 0.45);
  border-color: rgba(255, 255, 255, 0.3);
}

/* 只在非展开状态下应用hover的scale效果 */
.floating-sidebar:not(.expanded) .sidebar-trigger:hover {
  transform: translateY(-50%) scale(1.05);
}

.trigger-icon {
  color: white;
  font-size: 10px;
  transition: all 0.3s ease;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
}

.sidebar-content {
  position: absolute;
  left: -10px;
  top: 0;
  width: 180px;
  height: 100%;
  background: rgba(255, 255, 255, 0.98);
  backdrop-filter: blur(25px);
  border-radius: 0px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.15);
  border: none;
  opacity: 0;
  visibility: hidden;
  transform: translateX(-100%);
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  will-change: transform, opacity, visibility;
}

.floating-sidebar.expanded .sidebar-content {
  opacity: 1;
  visibility: visible;
  transform: translateX(0);
  border: none;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.15);
  position: absolute;
}

/* 移除右侧彩色边框装饰 */

/* 菜单展开时触发按钮移动到菜单栏的最右边 */
.floating-sidebar.expanded .sidebar-trigger {
  left: 160px;
  transform: translateY(-50%) scale(1.05);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.sidebar-header {
  padding: 8px 16px 6px;
  background: linear-gradient(135deg, #f8f9ff 0%, #e8f0ff 100%);
  border-bottom: 1px solid rgba(102, 126, 234, 0.15);
  position: relative;
  overflow: hidden;
}

.sidebar-header::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, #667eea 0%, #764ba2 50%, #667eea 100%);
  background-size: 200% 100%;
  animation: shimmer 3s ease-in-out infinite;
}

@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}

.header-title {
  display: flex;
  align-items: center;
  gap: 10px;
  position: relative;
  z-index: 1;
}

.title-icon {
  color: #667eea;
  font-size: 14px;
  filter: drop-shadow(0 2px 4px rgba(102, 126, 234, 0.3));
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.1); }
}

.title-text {
  font-size: 13px;
  font-weight: 700;
  color: #2c3e50;
  letter-spacing: 0.4px;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.sidebar-menu {
  padding: 6px 0;
  flex: 1;
  overflow-y: auto;
  max-height: calc(100vh - 120px);
}

.custom-menu {
  border: none !important;
  background: transparent !important;
}

.custom-menu :deep(.ant-menu-item) {
  margin: 2px 8px !important;
  border-radius: 8px !important;
  height: 36px !important;
  line-height: 36px !important;
  color: #5a6c7d !important;
  font-weight: 600 !important;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1) !important;
  position: relative !important;
  overflow: hidden !important;
  font-size: 13px !important;
  padding-left: 10px !important;
}

.custom-menu :deep(.ant-menu-item:hover) {
  background: linear-gradient(135deg, #f0f4ff 0%, #e8f0ff 100%) !important;
  color: #667eea !important;
  transform: translateX(6px) scale(1.02) !important;
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.2) !important;
  border: 1px solid rgba(102, 126, 234, 0.2) !important;
}

.custom-menu :deep(.ant-menu-item-selected) {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%) !important;
  color: white !important;
  box-shadow: 0 6px 24px rgba(102, 126, 234, 0.4) !important;
  transform: translateX(4px) scale(1.02) !important;
  border: 1px solid rgba(255, 255, 255, 0.2) !important;
}

/* 确保选中状态样式优先级最高 */
.custom-menu :deep(.ant-menu-item.ant-menu-item-selected) {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%) !important;
  color: white !important;
  box-shadow: 0 6px 24px rgba(102, 126, 234, 0.4) !important;
  transform: translateX(4px) scale(1.02) !important;
  border: 1px solid rgba(255, 255, 255, 0.2) !important;
}

/* 针对可能的其他选中状态类名 */
.custom-menu :deep(.ant-menu-item[aria-selected="true"]) {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%) !important;
  color: white !important;
  box-shadow: 0 6px 24px rgba(102, 126, 234, 0.4) !important;
  transform: translateX(4px) scale(1.02) !important;
  border: 1px solid rgba(255, 255, 255, 0.2) !important;
}

/* 最高优先级的选中状态样式 */
.custom-menu.menu-selected :deep(.ant-menu-item-selected),
.custom-menu :deep(.ant-menu-item-selected),
.custom-menu :deep(.ant-menu-item.ant-menu-item-selected),
.custom-menu :deep(.ant-menu-item[aria-selected="true"]) {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%) !important;
  color: white !important;
  box-shadow: 0 6px 24px rgba(102, 126, 234, 0.4) !important;
  transform: translateX(4px) scale(1.02) !important;
  border: 1px solid rgba(255, 255, 255, 0.2) !important;
}

/* 强制应用选中状态样式 - 使用最高优先级 */
.floating-sidebar .sidebar-menu .custom-menu :deep(.ant-menu-item-selected) {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%) !important;
  color: white !important;
  box-shadow: 0 6px 24px rgba(102, 126, 234, 0.4) !important;
  transform: translateX(4px) scale(1.02) !important;
  border: 1px solid rgba(255, 255, 255, 0.2) !important;
}

.custom-menu :deep(.ant-menu-item-selected::before) {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  width: 5px;
  height: 100%;
  background: linear-gradient(135deg, #ff6b6b 0%, #ffa500 100%);
  border-radius: 0 3px 3px 0;
  box-shadow: 0 0 8px rgba(255, 107, 107, 0.5);
}

.custom-menu :deep(.ant-menu-submenu-title) {
  margin: 2px 8px !important;
  border-radius: 8px !important;
  height: 36px !important;
  line-height: 36px !important;
  color: #5a6c7d !important;
  font-weight: 600 !important;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1) !important;
  font-size: 13px !important;
  padding-left: 8px !important;
  padding-right: 6px !important;
  display: flex !important;
  align-items: center !important;
  justify-content: space-between !important;
}


.custom-menu :deep(.ant-menu-submenu-title:hover) {
  background: linear-gradient(135deg, #f0f4ff 0%, #e8f0ff 100%) !important;
  color: #667eea !important;
  transform: translateX(6px) scale(1.02) !important;
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.2) !important;
  border: 1px solid rgba(102, 126, 234, 0.2) !important;
}

.custom-menu :deep(.ant-menu-submenu-arrow) {
  color: #667eea !important;
  transition: all 0.3s ease !important;
  margin-left: -8px !important;
  margin-right: 4px !important;
}

.custom-menu :deep(.ant-menu-submenu-open > .ant-menu-submenu-title .ant-menu-submenu-arrow) {
  transform: rotate(180deg) !important;
}

.custom-menu :deep(.ant-menu-sub) {
  background: transparent !important;
  border-radius: 0px !important;
  margin: 0px 8px !important;
  padding: 0px 0 !important;
  border: none !important;
  box-shadow: none !important;
}

.custom-menu :deep(.ant-menu-sub .ant-menu-item) {
  margin: 1px 0px !important;
  height: 28px !important;
  line-height: 28px !important;
  font-size: 11px !important;
  color: #6c7b7f !important;
  border-radius: 6px !important;
  font-weight: 500 !important;
  padding-left: 16px !important;
  display: flex !important;
  align-items: center !important;
}

.custom-menu :deep(.ant-menu-sub .ant-menu-item:hover) {
  background: linear-gradient(135deg, #e8f0ff 0%, #d6e4ff 100%) !important;
  color: #5a6fd8 !important;
  transform: translateX(4px) scale(1.02) !important;
  box-shadow: 0 3px 12px rgba(90, 111, 216, 0.2) !important;
}

.custom-menu :deep(.ant-menu-sub .ant-menu-item-selected) {
  background: linear-gradient(135deg, #5a6fd8 0%, #6a4190 100%) !important;
  color: white !important;
  transform: translateX(4px) scale(1.02) !important;
  box-shadow: 0 4px 16px rgba(90, 111, 216, 0.3) !important;
}

/* 团队空间子菜单标题样式（我加入的团队空间、我创建的团队空间） */
.custom-menu :deep(.ant-menu-sub .ant-menu-submenu-title .ant-menu-title-content) {
  font-size: 11px !important;
}

.custom-menu :deep(.ant-menu-sub .ant-menu-submenu-title .anticon) {
  font-size: 11px !important;
}

/* 团队空间子菜单项（具体空间名称）样式 */
.custom-menu :deep(.ant-menu-sub .ant-menu-sub .ant-menu-item) {
  margin: 1px 0px !important;
  height: 28px !important;
  line-height: 28px !important;
  font-size: 10px !important;
  color: #4a5568 !important;
  border-radius: 6px !important;
  font-weight: 500 !important;
  padding-left: 24px !important;
  display: flex !important;
  align-items: center !important;
}

.custom-menu :deep(.ant-menu-sub .ant-menu-sub .ant-menu-item:hover) {
  background: linear-gradient(135deg, #e8f0ff 0%, #d6e4ff 100%) !important;
  color: #4a5568 !important;
  transform: translateX(4px) scale(1.02) !important;
  box-shadow: 0 3px 12px rgba(90, 111, 216, 0.2) !important;
}

.custom-menu :deep(.ant-menu-sub .ant-menu-sub .ant-menu-item-selected) {
  background: linear-gradient(135deg, #5a6fd8 0%, #6a4190 100%) !important;
  color: white !important;
  transform: translateX(4px) scale(1.02) !important;
  box-shadow: 0 4px 16px rgba(90, 111, 216, 0.3) !important;
}

/* 隐藏滚动条 */
.sidebar-menu::-webkit-scrollbar {
  width: 0px;
  display: none;
}

.sidebar-menu {
  scrollbar-width: none;
  -ms-overflow-style: none;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .floating-sidebar {
    display: none;
  }
}

/* 动画效果增强 */
@keyframes slideIn {
  from {
    opacity: 0;
    transform: translateX(-100%);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

@keyframes slideOut {
  from {
    opacity: 1;
    transform: translateX(0);
  }
  to {
    opacity: 0;
    transform: translateX(-100%);
  }
}

.floating-sidebar.expanded .sidebar-content {
  animation: slideIn 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.floating-sidebar:not(.expanded) .sidebar-content {
  animation: slideOut 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

@media (max-width: 768px) {
  .floating-sidebar {
    top: 48px;
    height: calc(100vh - 48px);
  }
}
</style>