<template>
  <div class="reset-password-page">
    <div class="container">
      <div class="form-container">
        <div class="header">
          <h2>重置密码</h2>
          <p>请输入验证码和新密码</p>
        </div>
        
        <a-form
          :model="form"
          :rules="rules"
          @finish="handleSubmit"
          class="reset-form"
        >
          <a-form-item name="email">
            <a-input
              v-model:value="form.email"
              placeholder="邮箱地址"
              size="large"
              disabled
            >
              <template #prefix>
                <MailOutlined />
              </template>
            </a-input>
          </a-form-item>

          <a-form-item name="verificationCode">
            <a-input
              v-model:value="form.verificationCode"
              placeholder="请输入验证码"
              size="large"
              :disabled="loading"
              maxlength="6"
            >
              <template #prefix>
                <SafetyOutlined />
              </template>
              <template #suffix>
                <a-button
                  type="link"
                  :disabled="countdown > 0 || loading"
                  @click="resendCode"
                  class="resend-btn"
                >
                  {{ countdown > 0 ? `${countdown}s后重发` : '重新发送' }}
                </a-button>
              </template>
            </a-input>
          </a-form-item>

          <a-form-item name="newPassword">
            <a-input-password
              v-model:value="form.newPassword"
              placeholder="请输入新密码"
              size="large"
              :disabled="loading"
            >
              <template #prefix>
                <LockOutlined />
              </template>
            </a-input-password>
          </a-form-item>

          <a-form-item name="confirmPassword">
            <a-input-password
              v-model:value="form.confirmPassword"
              placeholder="请确认新密码"
              size="large"
              :disabled="loading"
            >
              <template #prefix>
                <LockOutlined />
              </template>
            </a-input-password>
          </a-form-item>

          <a-form-item>
            <a-button
              type="primary"
              html-type="submit"
              size="large"
              block
              :loading="loading"
            >
              {{ loading ? '重置中...' : '重置密码' }}
            </a-button>
          </a-form-item>

          <div class="footer">
            <a-button type="link" @click="goToLogin">
              <ArrowLeftOutlined />
              返回登录
            </a-button>
          </div>
        </a-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { 
  MailOutlined, 
  SafetyOutlined, 
  LockOutlined, 
  ArrowLeftOutlined 
} from '@ant-design/icons-vue'
import { 
  resetPasswordUsingPost, 
  sendResetPasswordCodeUsingPost 
} from '@/api/authController'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const countdown = ref(0)
let countdownTimer: number | null = null

const form = reactive({
  email: '',
  verificationCode: '',
  newPassword: '',
  confirmPassword: ''
})

const rules = {
  email: [
    { required: true, message: '邮箱地址不能为空', trigger: 'blur' }
  ],
  verificationCode: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { len: 6, message: '验证码为6位数字', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (rule: any, value: string) => {
        if (value && value !== form.newPassword) {
          return Promise.reject('两次输入的密码不一致')
        }
        return Promise.resolve()
      },
      trigger: 'blur'
    }
  ]
}

onMounted(() => {
  // 从路由参数获取邮箱
  const email = route.query.email as string
  if (email) {
    form.email = email
    // 页面加载时自动启动倒计时，防止用户立即重新发送
    startCountdown()
  } else {
    // 如果没有邮箱参数，跳转到忘记密码页面
    router.push('/user/forgot-password')
  }
})

onUnmounted(() => {
  // 组件卸载时清理定时器
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
})

const handleSubmit = async () => {
  if (loading.value) return
  
  loading.value = true
  try {
    await resetPasswordUsingPost({
      email: form.email,
      verificationCode: form.verificationCode,
      newPassword: form.newPassword,
      confirmPassword: form.confirmPassword
    })
    
    message.success('密码重置成功，请使用新密码登录')
    router.push('/user/login')
  } catch (error: any) {
    message.error(error.message || '重置失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const resendCode = async () => {
  if (countdown.value > 0 || loading.value) return
  
  try {
    await sendResetPasswordCodeUsingPost({ email: form.email })
    message.success('验证码已重新发送')
    startCountdown()
  } catch (error: any) {
    message.error(error.message || '发送失败，请稍后重试')
  }
}

const goToLogin = () => {
  router.push('/user/login')
}

const startCountdown = () => {
  // 清理之前的定时器
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
  
  countdown.value = 60
  countdownTimer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(countdownTimer!)
      countdownTimer = null
    }
  }, 1000)
}
</script>

<style scoped>
.reset-password-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.container {
  width: 100%;
  max-width: 400px;
}

.form-container {
  background: white;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  padding: 40px;
}

.header {
  text-align: center;
  margin-bottom: 32px;
}

.header h2 {
  color: #333;
  margin-bottom: 8px;
  font-size: 24px;
  font-weight: 600;
}

.header p {
  color: #666;
  font-size: 14px;
  margin: 0;
}

.reset-form {
  margin-top: 24px;
}

.footer {
  text-align: center;
  margin-top: 16px;
}

.resend-btn {
  padding: 0;
  height: auto;
  font-size: 12px;
  color: #1890ff;
}

.resend-btn:hover {
  color: #40a9ff;
}

:deep(.ant-input-affix-wrapper) {
  border-radius: 8px;
}

:deep(.ant-btn-primary) {
  border-radius: 8px;
  height: 48px;
  font-size: 16px;
  font-weight: 500;
}

:deep(.ant-btn-link) {
  padding: 0;
  height: auto;
  color: #1890ff;
}

:deep(.ant-btn-link:hover) {
  color: #40a9ff;
}

:deep(.ant-input[disabled]) {
  background-color: #f5f5f5;
  color: #999;
}
</style>