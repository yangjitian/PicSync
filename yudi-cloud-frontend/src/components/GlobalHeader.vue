<template>
  <div id="globalHeader">
    <div class="left-content">
      <router-link to="/">
        <div class="title-bar">
          <img class="logo" src="../assets/logo.png" alt="logo" />
          <div class="title">PicSync云见图</div>
        </div>
      </router-link>
    </div>
    <div class="center-content">
      <!-- 主页菜单 -->
      <a-menu
        v-model:selectedKeys="current"
        mode="horizontal"
        :items="mainItems"
        @click="doMenuClick"
        class="main-menu"
      />
      <!-- 管理菜单 -->
      <a-menu
        v-if="loginUserStore.loginUser?.userRole === 'admin'"
        v-model:selectedKeys="current"
        mode="horizontal"
        :items="adminItems"
        @click="doMenuClick"
        class="admin-menu"
      />
    </div>
    <div class="search-content">
      <a-input-search
        v-model:value="searchText"
        placeholder="🔍 从海量图片中搜索"
        size="middle"
        @search="doSearch"
        class="header-search"
      >
        <template #enterButton>
          <SearchOutlined />
        </template>
      </a-input-search>
    </div>
    <div class="right-content">
      <div v-if="loginUserStore.loginUser.id">
        <a-dropdown>
          <a-space class="user-avatar-space">
            <a-avatar :src="loginUserStore.loginUser.userAvatar" />
            <span>{{ loginUserStore.loginUser.userName ?? '无名' }}</span>
          </a-space>
          <template #overlay>
            <a-menu>
              <a-menu-item @click="goToAddPicture">
                <CameraOutlined />
                发布图片
              </a-menu-item>
              <a-menu-item @click="goToProfile">
                <ProfileOutlined />
                个人信息
              </a-menu-item>
              <a-menu-item @click="doLogout">
                <LogoutOutlined />
                退出登录
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </div>
      <div v-else>
        <a-button type="primary" @click="goToLogin">登录</a-button>
      </div>
    </div>
  </div>
</template>
<script lang="ts" setup>
import { computed, h, ref, nextTick } from 'vue'
import { HomeOutlined, LogoutOutlined, UserOutlined, CameraOutlined, ProfileOutlined, SearchOutlined, SettingOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import type { MenuProps } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { userLogoutUsingPost } from '@/api/userController.ts'

const loginUserStore = useLoginUserStore()

// 主页菜单项
const mainItems = computed(() => [
  {
    key: '/',
    icon: () => h(HomeOutlined),
    label: '主页',
    title: '主页',
  }
])

// 管理菜单项 - 使用下拉菜单，响应式检查用户角色
const adminItems = computed(() => {
  if (loginUserStore.loginUser?.userRole === 'admin') {
    return [
      {
        key: 'admin',
        label: '管理',
        title: '管理',
        icon: () => h(SettingOutlined),
        children: [
          {
            key: '/admin/userManage',
            label: '用户管理',
            title: '用户管理',
          },
          {
            key: '/admin/pictureManage',
            label: '图片管理',
            title: '图片管理',
          },
          {
            key: '/admin/spaceManage',
            label: '空间管理',
            title: '空间管理',
          },
        ]
      }
    ]
  }
  return []
})

const router = useRouter()
// 当前要高亮的菜单项
const current = ref<string[]>([])
// 搜索文本
const searchText = ref<string>('')
// 监听路由变化，更新高亮菜单项
router.afterEach((to, from, next) => {
  current.value = [to.path]
})

// 路由跳转事件
const doMenuClick = ({ key }: { key: string }) => {
  // 如果是管理菜单本身，不执行跳转
  if (key === 'admin') {
    return
  }
  
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
  router.push({
    path: key,
  }).then(() => {
  }).catch((error) => {
  })
}

// 跳转到发布图片页面
const goToAddPicture = () => {
  router.push('/add_picture')
}

// 跳转到个人信息页面
const goToProfile = () => {
  router.push('/profile')
}

// 跳转到登录页面
const goToLogin = () => {
  router.push('/user/login')
}

// 执行搜索
const doSearch = (value: string) => {
  if (!value.trim()) {
    message.warning('请输入搜索关键词')
    return
  }
  // 跳转到首页并传递搜索参数
  router.push({
    path: '/',
    query: { searchText: value.trim() }
  })
}

// 用户注销
const doLogout = async () => {
  const res = await userLogoutUsingPost()
  if (res.data.code === 0) {
    loginUserStore.setLoginUser({
      userName: '未登录',
    })
    message.success('退出登录成功')
    await router.push('/user/login')
  } else {
message.error('退出登录失败，' + res.data.message)
  }
}
</script>

<style scoped>
#globalHeader {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
}

.left-content {
  display: flex;
  align-items: center;
}

.center-content {
  flex-grow: 1;
  display: flex;
  justify-content: flex-start;
  align-items: center;
  margin-left: 64px;
  gap: 16px;
}

.main-menu {
  margin: 0;
}

.admin-menu {
  margin: 0;
}

.right-content {
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.search-content {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  z-index: 10;
  display: flex;
  align-items: center;
}

.header-search {
  width: 320px;
  border-radius: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transition: all 0.3s ease;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e8e8e8;
  position: relative;
}

.header-search::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, rgba(24, 144, 255, 0.05), rgba(64, 169, 255, 0.05));
  opacity: 0;
  transition: opacity 0.3s ease;
  pointer-events: none;
}

.header-search:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transform: translateY(-1px);
  border-color: #d9d9d9;
}

.header-search:hover::before {
  opacity: 1;
}

.header-search:focus-within {
  box-shadow: 0 4px 16px rgba(24, 144, 255, 0.2);
  transform: translateY(-1px);
  border-color: #1890ff;
}

.header-search:focus-within::before {
  opacity: 1;
}

.header-search :deep(.ant-input) {
  border: none;
  border-radius: 0;
  padding: 12px 16px;
  font-size: 14px;
  background: transparent;
  box-shadow: none;
  height: 40px;
  color: #333;
  position: relative;
  z-index: 2;
}

.header-search :deep(.ant-input:focus) {
  border: none;
  box-shadow: none;
  background: transparent;
  color: #333;
}

.header-search :deep(.ant-input::placeholder) {
  color: #999;
  font-style: italic;
}

.header-search :deep(.ant-input-search-button) {
  border: none;
  border-radius: 0;
  background: linear-gradient(135deg, #1890ff, #40a9ff);
  color: white;
  transition: all 0.3s ease;
  height: 40px;
  width: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  position: relative;
  overflow: hidden;
}

.header-search :deep(.ant-input-search-button::before) {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.2), transparent);
  transition: left 0.5s ease;
}

.header-search :deep(.ant-input-search-button:hover) {
  background: linear-gradient(135deg, #40a9ff, #69c0ff);
  transform: scale(1.05);
}

.header-search :deep(.ant-input-search-button:hover::before) {
  left: 100%;
}

.header-search :deep(.ant-input-search-button:active) {
  transform: scale(0.98);
}

.title-bar {
  display: flex;
  align-items: center;
  height: 100%;
}

.title {
  font-size: 22px;
  font-weight: bold;
  margin-left: 8px;
  background: linear-gradient(45deg, #e73c7e, #23a6d5, #8E2DE2);
  background-size: 300% 300%;
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  animation: gradient-text 5s ease infinite;
  white-space: nowrap;
}

@keyframes gradient-text {
  0% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0% 50%;
  }
}

.logo {
  height: 50px;
  margin: 5px;
  border-radius: 8px;
  transition: all 0.3s ease;
}

.logo:hover {
  transform: scale(1.05);
}

.user-avatar-space {
  cursor: pointer;
  padding: 8px 12px;
  border-radius: 6px;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-avatar-space:hover {
  background: rgba(0, 0, 0, 0.05);
}

#globalHeader :deep(.ant-menu) {
  height: 100%;
  line-height: 64px;
  border-bottom: none;
  background: transparent;
}

#globalHeader :deep(.ant-menu-item) {
  height: 64px;
  line-height: 64px;
  margin: 0 4px;
  border-radius: 6px;
  transition: all 0.3s ease;
}

#globalHeader :deep(.ant-menu-item:hover) {
  background: rgba(0, 0, 0, 0.05);
  transform: translateY(-1px);
}

#globalHeader :deep(.ant-menu-item-selected) {
  background: rgba(24, 144, 255, 0.1);
  color: #1890ff;
}

/* 下拉菜单样式 */
#globalHeader :deep(.ant-menu-submenu) {
  height: 64px;
  line-height: 64px;
}

#globalHeader :deep(.ant-menu-submenu-title) {
  height: 64px;
  line-height: 64px;
  margin: 0 4px;
  border-radius: 6px;
  transition: all 0.3s ease;
}

#globalHeader :deep(.ant-menu-submenu-title:hover) {
  background: rgba(0, 0, 0, 0.05);
  transform: translateY(-1px);
}

#globalHeader :deep(.ant-menu-submenu-selected .ant-menu-submenu-title) {
  background: rgba(24, 144, 255, 0.1);
  color: #1890ff;
}

/* 下拉菜单内容样式 */
#globalHeader :deep(.ant-menu-submenu-popup) {
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  border: 1px solid #e8e8e8;
}

#globalHeader :deep(.ant-menu-submenu-popup .ant-menu-item) {
  height: 40px;
  line-height: 40px;
  margin: 0;
  border-radius: 4px;
  transition: all 0.3s ease;
}

#globalHeader :deep(.ant-menu-submenu-popup .ant-menu-item:hover) {
  background: rgba(24, 144, 255, 0.1);
  color: #1890ff;
  transform: translateX(4px);
}

#globalHeader :deep(.ant-menu-submenu-popup .ant-menu-item-selected) {
  background: rgba(24, 144, 255, 0.15);
  color: #1890ff;
}

/* 响应式设计 */
@media screen and (max-width: 768px) {
  .center-content {
    display: none; /* 在小屏幕上隐藏中间的菜单 */
  }
  .left-content {
    flex-grow: 1; /* 让左侧内容占据剩余空间 */
  }
  .search-content {
    position: static;
    transform: none;
    margin: 0 16px;
    flex-grow: 1;
  }
  .header-search {
    width: 100%;
    max-width: 280px;
  }
}

@media screen and (max-width: 480px) {
  .search-content {
    margin: 0 8px;
  }
  .header-search {
    width: 100%;
    max-width: 200px;
  }
  .header-search :deep(.ant-input) {
    font-size: 12px;
    padding: 6px 12px;
  }
}
</style>
