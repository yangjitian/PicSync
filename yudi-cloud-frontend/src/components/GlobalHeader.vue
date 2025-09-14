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
      <a-menu
        v-model:selectedKeys="current"
        mode="horizontal"
        :items="items"
        @click="doMenuClick"
      />
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
import { HomeOutlined, LogoutOutlined, UserOutlined, CameraOutlined, ProfileOutlined } from '@ant-design/icons-vue'
import { MenuProps, message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { userLogoutUsingPost } from '@/api/userController.ts'

const loginUserStore = useLoginUserStore()

// 未经过滤的菜单项
const originItems = [
  {
    key: '/',
    icon: () => h(HomeOutlined),
    label: '主页',
    title: '主页',
  },
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

// 根据权限过滤菜单项
const filterMenus = (menus = [] as MenuProps['items']) => {
  return menus?.filter((menu) => {
    const loginUser = loginUserStore.loginUser
    
    // 管理员才能看到 /admin 开头的菜单
    if (menu?.key?.startsWith('/admin')) {
      if (!loginUser || loginUser.userRole !== 'admin') {
        return false
      }
    }
    
    // 需要登录才能看到的菜单
    const needLoginMenus = []
    if (needLoginMenus.includes(menu?.key)) {
      if (!loginUser || !loginUser.id) {
        return false
      }
    }
    
    return true
  })
}

// 展示在菜单的路由数组
const items = computed(() => filterMenus(originItems))

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
  router.push({
    path: key,
  }).then(() => {
  }).catch((error) => {
    console.error('GlobalHeader 路由跳转失败:', error)
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
}

.left-content {
  display: flex;
  align-items: center;
}

.center-content {
  flex-grow: 1;
  display: flex;
  justify-content: flex-start;
  margin-left: 64px;
}

.right-content {
  display: flex;
  align-items: center;
  justify-content: flex-end;
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

/* 响应式设计 */
@media screen and (max-width: 768px) {
  .center-content {
    display: none; /* 在小屏幕上隐藏中间的菜单 */
  }
  .left-content {
    flex-grow: 1; /* 让左侧内容占据剩余空间 */
  }
}
</style>
