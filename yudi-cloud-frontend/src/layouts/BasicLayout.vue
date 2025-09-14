<template>
  <div id="basicLayout">
    <a-layout style="min-height: 100vh">
      <!-- 浮动导航栏 -->
      <div class="header-wrapper">
        <a-layout-header
          class="header"
          :class="{ 'header-hidden': !isHeaderActuallyVisible }"
        >
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

// 处理滚动事件
const handleScroll = () => {
  const currentScrollTop = window.pageYOffset || document.documentElement.scrollTop

  // 向下滚动时隐藏导航栏，向上滚动时显示导航栏
  if (currentScrollTop > lastScrollTop && currentScrollTop > 100) {
    // 向下滚动超过100px时隐藏
    showHeader.value = false
  } else if (currentScrollTop < lastScrollTop) {
    // 向上滚动时显示
    showHeader.value = true
  }

  lastScrollTop = currentScrollTop
}

// 全局路由变化监听器，确保数据刷新
onMounted(() => {
  // 添加滚动事件监听
  window.addEventListener('scroll', handleScroll)
  
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
}

/* 浮动导航栏样式 */
#basicLayout .header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  padding-inline: 20px;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
  color: unset;
  margin-bottom: 1px;
  height: 64px;
  line-height: 64px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 1000;
}

/* 导航栏隐藏状态 */
.header-hidden {
  transform: translateY(-64px);
  box-shadow: none;
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
  margin-bottom: 28px;
  margin-top: 64px; /* 为浮动导航栏留出空间 */
  min-height: calc(100vh - 200px);
}

#basicLayout .footer {
  background: #efefef;
  padding: 16px;
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  text-align: center;
}

/* 响应式设计 */
@media screen and (max-width: 768px) {
  .header-wrapper {
    height: 48px;
  }
  
  #basicLayout .header {
    height: 48px;
    line-height: 48px;
    padding-inline: 16px;
  }
  
  .header-hidden {
    transform: translateY(-48px);
  }
  
  #basicLayout .content {
    margin-top: 48px;
  }
}
</style>