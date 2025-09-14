<template>
  <div id="basicLayout">
    <a-layout style="min-height: 100vh">
      <!-- 浮动导航栏 -->
      <div 
        class="header-wrapper"
        :class="{ 'header-hidden': !isHeaderActuallyVisible }"
      >
        <a-layout-header class="header">
          <GlobalHeader />
        </a-layout-header>
      </div>
      
      <a-layout>
        <GlobalSider class="sider" />
        <a-layout-content class="content">
          <router-view :key="$route.fullPath" v-slot="{ Component }">
            <component :is="Component" :key="$route.fullPath" />
          </router-view>
        </a-layout-content>
      </a-layout>
      <a-layout-footer class="footer">
        <router-link to="/about">PicSync云见图 by 雨滴</router-link>
      </a-layout-footer>
    </a-layout>
    
    <!-- 浮动侧边菜单 -->
    <FloatingSidebar v-if="loginUserStore.loginUser.id" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, nextTick, ref, onBeforeUnmount, computed } from 'vue'
import { useRouter } from 'vue-router'
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalSider from "@/components/GlobalSider.vue"
import FloatingSidebar from "@/components/FloatingSidebar.vue"
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

const router = useRouter()
const loginUserStore = useLoginUserStore()

// --- 联动显示逻辑 ---
const isSidebarHovered = ref(false)
const handleSidebarHoverStart = () => (isSidebarHovered.value = true)
const handleSidebarHoverEnd = () => (isSidebarHovered.value = false)
// --- 联动显示逻辑结束 ---

// 控制顶部导航栏显示状态（基于滚动）
const showHeader = ref(true)
let lastScrollTop = 0

// 最终决定导航栏是否可见的计算属性
const isHeaderActuallyVisible = computed(() => {
  // 如果鼠标悬浮在侧边栏上，则强制显示导航栏
  if (isSidebarHovered.value) {
    return true
  }
  // 否则，遵循基于滚动的可见性规则
  return showHeader.value
})

// 防抖处理
let scrollTimeout: number | null = null

// 处理滚动事件
const handleScroll = () => {
  // 清除之前的定时器
  if (scrollTimeout) {
    clearTimeout(scrollTimeout)
  }

  // 使用防抖，避免频繁触发
  scrollTimeout = setTimeout(() => {
    const currentScrollTop = window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0

    // 判断滚动方向
    const scrollDelta = currentScrollTop - lastScrollTop
    
    if (currentScrollTop > 50) {
      // 向下滚动超过50px时隐藏
      if (scrollDelta > 0) {
        showHeader.value = false
      } else if (scrollDelta < 0) {
        showHeader.value = true
      }
    } else {
      // 在顶部区域时始终显示
      showHeader.value = true
    }

    lastScrollTop = currentScrollTop
  }, 10) // 10ms防抖
}


// 全局路由变化监听器，确保数据刷新
onMounted(() => {
  // 添加滚动事件监听
  window.addEventListener('scroll', handleScroll, { passive: true })
  
  // 强制设置body样式，确保可以滚动
  document.body.style.height = 'auto'
  document.body.style.minHeight = '100vh'
  document.documentElement.style.height = 'auto'
  document.documentElement.style.minHeight = '100vh'
  
  // 添加侧边栏悬浮事件监听
  window.addEventListener('sidebar-hover-start', handleSidebarHoverStart)
  window.addEventListener('sidebar-hover-end', handleSidebarHoverEnd)
  
  router.afterEach((to, from) => {
    
    // 延迟执行，确保组件已经渲染
    nextTick(() => {
      
      // 根据不同的路由调用对应的强制刷新函数
      if (to.path.startsWith('/space/')) {
        if ((window as any).refreshSpaceDetail) {
          (window as any).refreshSpaceDetail()
        }
      } else if (to.path === '/published_list') {
        if ((window as any).refreshPublishedList) {
          (window as any).refreshPublishedList()
        }
      } else if (to.path === '/my_space') {
        if ((window as any).refreshMySpace) {
          (window as any).refreshMySpace()
        }
      }
    })
  })
})

// 组件销毁前清理事件监听
onBeforeUnmount(() => {
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('sidebar-hover-start', handleSidebarHoverStart)
  window.removeEventListener('sidebar-hover-end', handleSidebarHoverEnd)
})
</script>

<style scoped>
/* 浮动导航栏包装器 */
.header-wrapper {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 64px;
  z-index: 1000;
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 导航栏隐藏状态 - 应用到包装器 */
.header-wrapper.header-hidden {
  transform: translateY(-64px);
}

/* 浮动导航栏样式 */
#basicLayout .header {
  position: relative;
  width: 100%;
  height: 100%;
  padding-inline: 20px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
  color: unset;
  line-height: 64px;
  z-index: 1001;
}


#basicLayout .sider {
  background: #fff;
  border-right: 0.5px solid #eee;
  padding-top: 20px;
  display: none; /* 隐藏原来的侧边栏，使用浮动侧边栏 */
}

#basicLayout :deep(.ant-menu-root) {
  border-bottom: none !important;
  border-inline-end: none !important;
}

/* 内容区域调整，为浮动导航栏留出空间 */
#basicLayout .content {
  padding: 28px;
  background: linear-gradient(to right, #fefefe, #fff);
  margin-bottom: 80px; /* 为footer留出空间 */
  margin-top: 64px; /* 为浮动导航栏留出空间 */
  min-height: calc(100vh - 200px);
  overflow: visible; /* 确保内容可以正常滚动 */
  height: auto; /* 确保高度可以自动扩展 */
}

#basicLayout .footer {
  background: #efefef;
  padding: 16px;
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  text-align: center;
  z-index: 100; /* 确保footer在内容之上 */
}


/* 响应式设计 */
@media screen and (max-width: 768px) {
  .header-wrapper {
    height: 48px;
  }
  
  .header-wrapper.header-hidden {
    transform: translateY(-48px);
  }
  
  #basicLayout .header {
    line-height: 48px;
    padding-inline: 16px;
  }
  
  #basicLayout .content {
    margin-top: 48px;
  }
}
</style>