<template>
  <div id="profilePage">
    <a-card :bordered="false" class="profile-card">
      <template #title>
        <div class="profile-header">
          <a-avatar :size="80" :src="userInfo.userAvatar" class="profile-avatar">
            <UserOutlined v-if="!userInfo.userAvatar" />
          </a-avatar>
          <div class="profile-info">
            <div class="profile-name-container">
              <h2 class="profile-name">{{ userInfo.userName || '未设置' }}</h2>
              <a-tag :color="getRoleColor(userInfo.userRole)" class="profile-role">
                {{ getUserRoleText(userInfo.userRole) }}
              </a-tag>
            </div>
            <p class="profile-account">用户ID：{{ userInfo.id || '未知' }}</p>
          </div>
          <div class="profile-actions">
            <a-space size="middle" class="action-buttons">
              <a-button 
                type="primary" 
                size="middle" 
                class="action-btn edit-btn"
                @click="goToEditProfile"
              >
                <EditOutlined />
                编辑资料
              </a-button>
              <a-button 
                size="middle" 
                class="action-btn password-btn"
                @click="goToChangePassword"
              >
                <KeyOutlined />
                修改密码
              </a-button>
              <a-button 
                v-if="userInfo.userRole === 'admin'" 
                size="middle" 
                class="action-btn admin-btn"
                @click="goToAdmin"
              >
                <SettingOutlined />
                管理后台
              </a-button>
            </a-space>
          </div>
        </div>
      </template>
      
      <a-row :gutter="[24, 24]">
        <!-- 基本信息 -->
        <a-col :span="24">
          <a-card title="基本信息" size="small" class="info-card">
            <a-descriptions :column="2" bordered>
              <a-descriptions-item label="✉️ 邮箱">
                {{ userInfo.userAccount || '未设置' }}
              </a-descriptions-item>
              <a-descriptions-item label="🎂 生日">
                {{ formatBirthday(userInfo.birthday) }}
              </a-descriptions-item>
              <a-descriptions-item label="📅 注册时间">
                {{ formatDate(userInfo.createTime) }}
              </a-descriptions-item>
              <a-descriptions-item label="📝 个人简介" :span="2">
                {{ userInfo.userProfile || '暂无简介' }}
              </a-descriptions-item>
            </a-descriptions>
          </a-card>
        </a-col>


        <!-- 管理员特权信息 -->
        <a-col :span="24" v-if="userInfo.userRole === 'admin'">
          <a-card title="管理员特权" size="small" class="info-card admin-card">
            <a-descriptions :column="2" bordered>
              <a-descriptions-item label="管理员等级">
                <a-tag color="red">
                  <CrownOutlined />
                  超级管理员
                </a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="权限范围">
                <a-tag color="green">全部权限</a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="系统管理" :span="2">
                <a-space wrap>
                  <a-tag color="blue">用户管理</a-tag>
                  <a-tag color="blue">数据统计</a-tag>
                  <a-tag color="blue">系统配置</a-tag>
                  <a-tag color="blue">内容审核</a-tag>
                </a-space>
              </a-descriptions-item>
            </a-descriptions>
            <div class="admin-note">
              <a-alert 
                message="管理员提示" 
                description="您拥有系统的最高权限，可以管理所有用户和数据。VIP功能对管理员开放，无需额外购买。" 
                type="info" 
                show-icon 
              />
            </div>
          </a-card>
        </a-col>

        <!-- VIP用户信息 -->
        <a-col :span="24" v-else-if="userInfo.userRole === 'vip' && !isVipExpired">
          <a-card title="VIP会员信息" size="small" class="info-card vip-card">
            <a-descriptions :column="2" bordered>
              <a-descriptions-item label="VIP等级">
                <a-tag color="gold">
                  <CrownOutlined />
                  VIP {{ userInfo.vipNumber || '会员' }}
                </a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="到期时间">
                {{ formatDate(userInfo.vipExpireTime) }}
              </a-descriptions-item>
              <a-descriptions-item label="会员状态" :span="2">
                <a-tag color="green">
                  <CheckCircleOutlined />
                  有效会员
                </a-tag>
              </a-descriptions-item>
            </a-descriptions>
            <div class="vip-benefits-preview">
              <h4>您享受的会员特权</h4>
              <a-row :gutter="[16, 8]">
                <a-col :span="12" v-for="benefit in vipBenefits.slice(0, 4)" :key="benefit.title">
                  <div class="benefit-item">
                    <CheckCircleOutlined class="benefit-icon" />
                    <span>{{ benefit.title }}</span>
                  </div>
                </a-col>
              </a-row>
            </div>
          </a-card>
        </a-col>

        <!-- VIP过期用户 -->
        <a-col :span="24" v-else-if="userInfo.userRole === 'vip' && isVipExpired">
          <a-card title="VIP会员信息" size="small" class="info-card vip-expired-card">
            <a-descriptions :column="2" bordered>
              <a-descriptions-item label="VIP等级">
                <a-tag color="default">
                  <CrownOutlined />
                  VIP {{ userInfo.vipNumber || '会员' }}
                </a-tag>
              </a-descriptions-item>
              <a-descriptions-item label="到期时间">
                {{ formatDate(userInfo.vipExpireTime) }}
              </a-descriptions-item>
              <a-descriptions-item label="会员状态" :span="2">
                <a-tag color="red">
                  <ExclamationCircleOutlined />
                  已过期
                </a-tag>
              </a-descriptions-item>
            </a-descriptions>
            <div class="vip-renewal">
              <a-alert 
                message="会员已过期" 
                description="您的VIP会员已过期，部分功能将受到限制。立即续费可继续享受会员特权。" 
                type="warning" 
                show-icon 
              />
              <div class="renewal-actions">
                <a-button type="primary" size="large" @click="showVipModal" class="renewal-btn">
                  <CrownOutlined />
                  立即续费
                </a-button>
              </div>
            </div>
          </a-card>
        </a-col>

        <!-- 普通用户升级区域 -->
        <a-col :span="24" v-else-if="userInfo.userRole === 'user'">
          <a-card title="会员特权" size="small" class="info-card upgrade-card">
            <div class="upgrade-content">
              <div class="upgrade-header">
                <h3>升级为会员享受更多特权</h3>
                <p>解锁更多功能，提升使用体验</p>
              </div>
              <div class="upgrade-benefits">
                <a-row :gutter="[16, 8]">
                  <a-col :span="12" v-for="benefit in vipBenefits.slice(0, 4)" :key="benefit.title">
                    <div class="benefit-item">
                      <CheckCircleOutlined class="benefit-icon" />
                      <span>{{ benefit.title }}</span>
                    </div>
                  </a-col>
                </a-row>
              </div>
              <div class="upgrade-actions">
                <a-button type="primary" size="large" @click="showVipModal" class="upgrade-btn">
                  <CrownOutlined />
                  立即升级
                </a-button>
              </div>
            </div>
          </a-card>
        </a-col>

      </a-row>
    </a-card>

    <!-- 会员特权弹窗 -->
    <a-modal
      v-model:open="vipModalVisible"
      title="会员特权"
      :width="600"
      :footer="null"
      centered
    >
      <div class="vip-modal-content">
        <!-- 会员特权列表 -->
        <div class="vip-benefits">
          <h4>会员专享特权</h4>
          <a-list :data-source="vipBenefits" size="small">
            <template #renderItem="{ item }">
              <a-list-item>
                <a-list-item-meta>
                  <template #avatar>
                    <a-avatar :style="{ backgroundColor: '#52c41a' }" size="small">
                      <CheckOutlined />
                    </a-avatar>
                  </template>
                  <template #title>
                    <span class="benefit-title">{{ item.title }}</span>
                  </template>
                  <template #description>
                    <span class="benefit-desc">{{ item.description }}</span>
                  </template>
                </a-list-item-meta>
              </a-list-item>
            </template>
          </a-list>
        </div>

        <!-- 兑换码输入区域 -->
        <div class="exchange-section">
          <a-divider>会员兑换</a-divider>
          <a-form :model="exchangeForm" @finish="handleExchange">
            <a-form-item>
              <a-input
                v-model:value="exchangeForm.vipCode"
                placeholder="请输入会员兑换码"
                size="large"
                :maxlength="20"
              />
            </a-form-item>
            <a-form-item>
              <a-button
                type="primary"
                html-type="submit"
                size="large"
                block
                :loading="exchanging"
              >
                立即兑换
              </a-button>
            </a-form-item>
          </a-form>
        </div>
      </div>
    </a-modal>

    <!-- 编辑资料弹窗 -->
    <EditProfileModal
      v-model:visible="editProfileVisible"
      :user-info="userInfo"
      @success="handleEditProfileSuccess"
    />

    <!-- 修改密码弹窗 -->
    <ChangePasswordModal
      v-model:visible="changePasswordVisible"
      @success="handleChangePasswordSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { 
  UserOutlined, 
  EditOutlined, 
  KeyOutlined, 
  SettingOutlined,
  CrownOutlined,
  CheckOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined
} from '@ant-design/icons-vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { getUserVoByIdUsingGet, exchangeVipUsingPost } from '@/api/userController.ts'
import EditProfileModal from '@/components/EditProfileModal.vue'
import ChangePasswordModal from '@/components/ChangePasswordModal.vue'

const router = useRouter()
const loginUserStore = useLoginUserStore()

const userInfo = ref<API.UserVO>({})
const loading = ref(false)

// 弹窗状态
const editProfileVisible = ref(false)
const changePasswordVisible = ref(false)

// 会员升级相关
const vipModalVisible = ref(false)
const exchanging = ref(false)
const exchangeForm = ref({
  vipCode: ''
})

// 会员特权列表
const vipBenefits = ref([
  {
    title: '无限存储空间',
    description: '享受无限制的图片存储空间，不再受容量限制'
  },
  {
    title: '高清图片上传',
    description: '支持更高分辨率的图片上传和处理'
  },
  {
    title: '批量操作功能',
    description: '支持批量上传、下载、删除等高效操作'
  },
  {
    title: '专属客服支持',
    description: '享受优先客服响应和专业技术支持'
  },
  {
    title: '高级图片编辑',
    description: '使用更多高级图片编辑工具和滤镜效果'
  },
  {
    title: '数据备份保障',
    description: '自动备份重要数据，确保数据安全'
  }
])

// 计算VIP是否过期
const isVipExpired = computed(() => {
  if (!userInfo.value.vipExpireTime) {
    return true
  }
  try {
    return new Date(userInfo.value.vipExpireTime) < new Date()
  } catch (error) {
    return true
  }
})

// 获取用户信息
const fetchUserInfo = async () => {
  if (!loginUserStore.loginUser.id) {
    message.error('请先登录')
    router.push('/user/login')
    return
  }
  
  loading.value = true
  try {
    const res = await getUserVoByIdUsingGet({ id: loginUserStore.loginUser.id })
    if (res.data.code === 0 && res.data.data) {
      userInfo.value = res.data.data
    } else {
      message.error('获取用户信息失败：' + res.data.message)
    }
  } catch (error) {
    message.error('获取用户信息失败')
  } finally {
    loading.value = false
  }
}

// 格式化日期
const formatDate = (dateStr: string | undefined) => {
  if (!dateStr) return '未知'
  return new Date(dateStr).toLocaleString('zh-CN')
}

// 格式化生日（只显示日期）
const formatBirthday = (dateStr: string | undefined) => {
  if (!dateStr) return '未设置'
  return new Date(dateStr).toLocaleDateString('zh-CN')
}

// 获取角色颜色
const getRoleColor = (role: string | undefined) => {
  switch (role) {
    case 'admin':
      return 'red'
    case 'user':
      return 'blue'
    case 'vip':
      return 'gold'
    default:
      return 'default'
  }
}

// 获取角色文本
const getUserRoleText = (role: string | undefined) => {
  switch (role) {
    case 'admin':
      return '管理员'
    case 'user':
      return '普通用户'
    case 'vip':
      return 'VIP用户'
    default:
      return '未知'
  }
}

// 跳转到编辑资料页面
const goToEditProfile = () => {
  editProfileVisible.value = true
}

// 跳转到修改密码
const goToChangePassword = () => {
  changePasswordVisible.value = true
}

// 编辑资料成功回调
const handleEditProfileSuccess = async () => {
  // 刷新用户信息
  await fetchUserInfo()
  // 更新登录用户信息
  loginUserStore.updateLoginUser(userInfo.value)
}

// 修改密码成功回调
const handleChangePasswordSuccess = () => {
  message.success('密码修改成功，请重新登录')
  // 这里可以添加登出逻辑
  setTimeout(() => {
    router.push('/user/login')
  }, 1500)
}

// 跳转到管理后台
const goToAdmin = () => {
  router.push('/admin/userManage')
}

// 显示会员特权弹窗
const showVipModal = () => {
  vipModalVisible.value = true
  exchangeForm.value.vipCode = ''
}

// 处理会员兑换
const handleExchange = async () => {
  if (!exchangeForm.value.vipCode.trim()) {
    message.warning('请输入兑换码')
    return
  }

  exchanging.value = true
  try {
    const res = await exchangeVipUsingPost({
      vipCode: exchangeForm.value.vipCode.trim()
    })
    
    if (res.data.code === 0 && res.data.data) {
      message.success('会员兑换成功！')
      vipModalVisible.value = false
      // 刷新用户信息
      await fetchUserInfo()
      // 更新登录用户信息
      loginUserStore.updateLoginUser(userInfo.value)
    } else {
      message.error(res.data.message || '兑换失败，请检查兑换码是否正确')
    }
  } catch (error) {
    message.error('兑换失败，请稍后重试')
  } finally {
    exchanging.value = false
  }
}

onMounted(() => {
  fetchUserInfo()
})
</script>

<style scoped>
#profilePage {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
}

.profile-card {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  border-radius: 12px;
}

.profile-header {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 20px 0;
  position: relative;
}

.profile-avatar {
  border: 3px solid #f0f0f0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.profile-info {
  flex: 1;
}

.profile-name-container {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.profile-name {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #262626;
}

.profile-account {
  margin: 0 0 8px 0;
  color: #8c8c8c;
  font-size: 14px;
}

.profile-role {
  font-size: 14px;
  font-weight: 500;
  padding: 4px 12px;
  border-radius: 16px;
  height: auto;
  line-height: 1.4;
}

.profile-actions {
  position: absolute;
  top: 20px;
  right: 0;
  z-index: 10;
}

.action-buttons {
  display: flex;
  gap: 12px;
}

.action-btn {
  height: 40px;
  padding: 0 20px;
  border-radius: 20px;
  font-weight: 500;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  border: none;
  position: relative;
  overflow: hidden;
}

.action-btn::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.2), transparent);
  transition: left 0.5s;
}

.action-btn:hover::before {
  left: 100%;
}

.edit-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
}

.edit-btn:hover {
  background: linear-gradient(135deg, #5a6fd8 0%, #6a4190 100%);
  color: white;
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(102, 126, 234, 0.4);
}

.password-btn {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
  border: none;
}

.password-btn:hover {
  background: linear-gradient(135deg, #ee82f0 0%, #f3455a 100%);
  color: white;
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(240, 147, 251, 0.4);
}

.admin-btn {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
  color: white;
  border: none;
}

.admin-btn:hover {
  background: linear-gradient(135deg, #3d8bfe 0%, #00e6fe 100%);
  color: white;
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(79, 172, 254, 0.4);
}

.info-card {
  margin-bottom: 16px;
  border-radius: 8px;
  border: 1px solid #e8e8e8;
}

.info-card :deep(.ant-card-head) {
  border-bottom: 1px solid #f0f0f0;
  background: #fafafa;
}

.info-card :deep(.ant-card-body) {
  padding: 20px;
}

:deep(.ant-descriptions-item-label) {
  font-weight: 600;
  color: #262626;
  background: #fafafa;
}

:deep(.ant-descriptions-item-content) {
  color: #595959;
}

/* 会员升级区域样式 */
.upgrade-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  border-radius: 12px;
  margin-bottom: 16px;
}

.upgrade-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px;
  color: white;
}

.upgrade-text h3 {
  margin: 0 0 8px 0;
  font-size: 20px;
  font-weight: 600;
  color: white;
}

.upgrade-text p {
  margin: 0;
  font-size: 14px;
  opacity: 0.9;
}

.upgrade-btn {
  background: rgba(255, 255, 255, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.3);
  color: white;
  font-weight: 600;
  height: 48px;
  padding: 0 24px;
  border-radius: 24px;
  transition: all 0.3s ease;
}

.upgrade-btn:hover {
  background: rgba(255, 255, 255, 0.3);
  border-color: rgba(255, 255, 255, 0.5);
  color: white;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

/* 会员特权弹窗样式 */
.vip-modal-content {
  padding: 0;
}

.vip-benefits {
  margin-bottom: 24px;
}

.vip-benefits h4 {
  margin: 0 0 16px 0;
  font-size: 16px;
  font-weight: 600;
  color: #262626;
}

.benefit-title {
  font-weight: 600;
  color: #262626;
}

.benefit-desc {
  color: #8c8c8c;
  font-size: 13px;
}

.exchange-section {
  background: #fafafa;
  padding: 20px;
  border-radius: 8px;
  margin-top: 16px;
}

:deep(.ant-divider) {
  margin: 16px 0;
  color: #8c8c8c;
}

:deep(.ant-input) {
  border-radius: 6px;
}

:deep(.ant-btn-primary) {
  border-radius: 6px;
  height: 48px;
  font-weight: 600;
}

/* 管理员卡片样式 */
.admin-card {
  border-left: 4px solid #ff4d4f;
  background: linear-gradient(135deg, #fff5f5 0%, #fff 100%);
}

.admin-card :deep(.ant-card-head) {
  background: linear-gradient(135deg, #ff4d4f 0%, #ff7875 100%);
  color: white;
}

.admin-card :deep(.ant-card-head-title) {
  color: white;
  font-weight: 600;
}

.admin-note {
  margin-top: 50px;
}

/* 确保管理员描述列表有足够的底部间距 */
.admin-card :deep(.ant-descriptions) {
  margin-bottom: 25px;
}

/* VIP用户卡片样式 */
.vip-card {
  border-left: 4px solid #faad14;
  background: linear-gradient(135deg, #fffbe6 0%, #fff 100%);
}

.vip-card :deep(.ant-card-head) {
  background: linear-gradient(135deg, #faad14 0%, #ffc53d 100%);
  color: white;
}

.vip-card :deep(.ant-card-head-title) {
  color: white;
  font-weight: 600;
}

.vip-benefits-preview {
  margin-top: 16px;
  padding: 16px;
  background: #fafafa;
  border-radius: 8px;
}

.vip-benefits-preview h4 {
  margin: 0 0 12px 0;
  font-size: 16px;
  font-weight: 600;
  color: #262626;
}

.benefit-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 14px;
  color: #595959;
}

.benefit-icon {
  color: #52c41a;
  font-size: 16px;
}

/* VIP过期用户卡片样式 */
.vip-expired-card {
  border-left: 4px solid #d9d9d9;
  background: linear-gradient(135deg, #f5f5f5 0%, #fff 100%);
}

.vip-expired-card :deep(.ant-card-head) {
  background: linear-gradient(135deg, #d9d9d9 0%, #f0f0f0 100%);
  color: #595959;
}

.vip-expired-card :deep(.ant-card-head-title) {
  color: #595959;
  font-weight: 600;
}

.vip-renewal {
  margin-top: 16px;
}

.renewal-actions {
  margin-top: 16px;
  text-align: center;
}

.renewal-btn {
  background: linear-gradient(135deg, #faad14 0%, #ffc53d 100%);
  border: none;
  color: white;
  font-weight: 600;
  height: 48px;
  padding: 0 24px;
  border-radius: 24px;
  transition: all 0.3s ease;
}

.renewal-btn:hover {
  background: linear-gradient(135deg, #e69a0a 0%, #f5b800 100%);
  color: white;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(250, 173, 20, 0.4);
}

/* 普通用户升级卡片样式 */
.upgrade-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  border-radius: 12px;
  margin-bottom: 16px;
}

.upgrade-card :deep(.ant-card-head) {
  background: linear-gradient(135deg, #5a6fd8 0%, #6a4190 100%);
  color: white;
}

.upgrade-card :deep(.ant-card-head-title) {
  color: white;
  font-weight: 600;
}

.upgrade-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 20px;
  color: white;
}

.upgrade-header {
  text-align: center;
}

.upgrade-header h3 {
  margin: 0 0 8px 0;
  font-size: 20px;
  font-weight: 600;
  color: white;
}

.upgrade-header p {
  margin: 0;
  font-size: 14px;
  opacity: 0.9;
}

.upgrade-benefits {
  background: rgba(255, 255, 255, 0.1);
  padding: 16px;
  border-radius: 8px;
  backdrop-filter: blur(10px);
}

.upgrade-actions {
  text-align: center;
}

.upgrade-btn {
  background: rgba(255, 255, 255, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.3);
  color: white;
  font-weight: 600;
  height: 48px;
  padding: 0 24px;
  border-radius: 24px;
  transition: all 0.3s ease;
}

.upgrade-btn:hover {
  background: rgba(255, 255, 255, 0.3);
  border-color: rgba(255, 255, 255, 0.5);
  color: white;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}
</style>