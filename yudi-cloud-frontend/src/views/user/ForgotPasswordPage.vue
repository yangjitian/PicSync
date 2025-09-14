<template>
  <div class="forgot-password-page">
    <div class="container">
      <div class="form-container">
        <div class="header">
          <h2>忘记密码</h2>
          <p>请输入您的邮箱地址，我们将发送验证码到您的邮箱</p>
        </div>
        
        <a-form
          :model="form"
          :rules="rules"
          @finish="handleSubmit"
          class="forgot-form"
        >
          <a-form-item name="email">
            <a-input
              v-model:value="form.email"
              placeholder="请输入邮箱地址"
              size="large"
              :disabled="loading"
            >
              <template #prefix>
                <MailOutlined />
              </template>
            </a-input>
          </a-form-item>

          <a-form-item>
            <a-button
              type="primary"
              html-type="submit"
              size="large"
              block
              :loading="loading"
              :disabled="!form.email"
            >
              {{ loading ? '发送中...' : '发送验证码' }}
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
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { MailOutlined, ArrowLeftOutlined } from '@ant-design/icons-vue'
import { sendResetPasswordCodeUsingPost } from '@/api/authController'

const router = useRouter()
const loading = ref(false)

const form = reactive({
  email: ''
})

const rules = {
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' }
  ]
}

const handleSubmit = async () => {
  if (loading.value) return
  
  loading.value = true
  try {
    await sendResetPasswordCodeUsingPost({ email: form.email })
    message.success('验证码已发送到您的邮箱，请查收')
    
    // 跳转到重置密码页面，携带邮箱参数
    router.push({
      path: '/user/reset-password',
      query: { email: form.email }
    })
  } catch (error: any) {
    message.error(error.message || '发送失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const goToLogin = () => {
  router.push('/user/login')
}
</script>

<style scoped>
.forgot-password-page {
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

.forgot-form {
  margin-top: 24px;
}

.footer {
  text-align: center;
  margin-top: 16px;
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
</style>