<template>
  <div id="userRegisterPage">
    <div class="register-container">
      <!-- 左侧品牌区域 -->
      <div class="brand-section">
        <div class="brand-content">
          <div class="logo">
            <div class="logo-icon">📸</div>
            <h1 class="brand-title">PicSync</h1>
          </div>
          <p class="brand-subtitle">云见图</p>
          <div class="brand-features">
            <div class="feature-item">
              <span class="feature-icon">🚀</span>
              <span>智能图片管理</span>
            </div>
            <div class="feature-item">
              <span class="feature-icon">🔒</span>
              <span>安全可靠存储</span>
            </div>
            <div class="feature-item">
              <span class="feature-icon">👥</span>
              <span>团队协作共享</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧注册表单区域 -->
      <div class="form-section">
        <div class="form-container">
          <div class="form-header">
            <h2 class="form-title">创建您的账户</h2>
            <p class="form-subtitle">开启您的云端图片协作之旅</p>
          </div>

          <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit" class="register-form">
            <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入邮箱地址' }, { type: 'email', message: '请输入有效的邮箱地址' }]">
              <a-input size="large" v-model:value="formState.userAccount" placeholder="邮箱地址" class="custom-input" />
            </a-form-item>


            <a-form-item name="verificationCode" :rules="[{ required: true, message: '请输入6位验证码' }, { len: 6, message: '请输入6位验证码' }]">
              <div class="input-with-button">
                <a-input size="large" v-model:value="formState.verificationCode" placeholder="6位验证码" class="custom-input" />
                <a-button size="large" @click="sendCode" :disabled="isSending" class="send-code-btn">
                  {{ isSending ? `${countdown}s` : '发送验证码' }}
                </a-button>
              </div>
            </a-form-item>

            <a-form-item
              name="userPassword"
              :rules="[
                { required: true, message: '请输入密码' },
                { min: 8, message: '密码长度不能小于 8 位' },
              ]"
            >
              <a-input-password size="large" v-model:value="formState.userPassword" placeholder="设置密码 (至少8位)" class="custom-input" />
            </a-form-item>

            <a-form-item
              name="checkPassword"
              :rules="[
                { required: true, message: '请再次输入密码' },
                { validator: validatePasswordMatch }
              ]"
            >
              <a-input-password size="large" v-model:value="formState.checkPassword" placeholder="确认密码" class="custom-input" />
            </a-form-item>

            <a-form-item>
              <a-button size="large" class="register-btn" type="primary" html-type="submit" :loading="loading">
                立即注册
              </a-button>
            </a-form-item>
          </a-form>

          <div class="form-footer">
            <span class="has-account">已有账户？</span>
            <RouterLink to="/user/login" class="login-link">立即登录</RouterLink>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
<script lang="ts" setup>
import { onMounted, reactive, ref } from 'vue'
import { userRegisterUsingPost } from '@/api/userController.ts'
import { sendVerificationCodeUsingPost } from '@/api/authController'
import { message } from 'ant-design-vue'
import router from '@/router'

const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
  verificationCode: '',
})

const loading = ref(false)
const isSending = ref(false)
const countdown = ref(60)


const sendCode = async () => {
  if (!formState.userAccount) {
    message.error('请输入邮箱地址');
    return;
  }
  isSending.value = true;
  try {
    const res = await sendVerificationCodeUsingPost({ email: formState.userAccount });
    if (res.data.code === 0) {
      message.success('验证码已发送，请注意查收');
      const timer = setInterval(() => {
        countdown.value--;
        if (countdown.value <= 0) {
          clearInterval(timer);
          isSending.value = false;
          countdown.value = 60;
        }
      }, 1000);
    } else {
      message.error('验证码发送失败，' + res.data.message);
      isSending.value = false;
    }
  } catch (error) {
    message.error('验证码发送失败，请稍后重试');
    isSending.value = false;
  }
};

const validatePasswordMatch = async (_rule: any, value: string) => {
  if (value && value !== formState.userPassword) {
    return Promise.reject('两次输入的密码不一致');
  }
  return Promise.resolve();
};

const handleSubmit = async (values: any) => {
  loading.value = true;
  try {
    const res = await userRegisterUsingPost(values);
    if (res.data && res.data.code === 0) {
      message.success('注册成功！');
      // 使用replace避免返回按钮回到注册页面
      router.replace('/user/login');
    } else {
      message.error('注册失败，' + (res.data?.message || '未知错误'));
    }
  } catch (error) {
    message.error('注册失败，请稍后重试');
  } finally {
    loading.value = false;
  }
};

</script>

<style scoped>
/* 保持原有的 .brand-section, .form-section, etc. 样式 */
#userRegisterPage {
  min-height: 100vh;
  background: linear-gradient(to left, #ffdde1 0%, #a6c1ee 100%);
  position: relative;
  overflow: hidden;
}

.register-container {
  display: flex;
  min-height: 100vh;
  position: relative;
  z-index: 1;
}

.brand-section {
  flex: 4;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px 40px;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-right: 1px solid rgba(255, 255, 255, 0.2);
}

.brand-content, .form-container {
  animation: slideInRight 0.8s ease-out forwards;
}

@keyframes slideInRight {
  from {
    opacity: 0;
    transform: translateX(30px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.form-section {
  flex: 6;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px 40px;
}

.form-container {
  width: 100%;
  max-width: 400px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 24px;
  padding: 48px 40px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.1);
}

.form-header {
  text-align: center;
  margin-bottom: 40px;
}

.form-title {
  font-size: 32px;
  font-weight: 700;
  color: #1f2937;
  margin: 0 0 8px 0;
}

.form-subtitle {
  font-size: 16px;
  color: #6b7280;
  margin: 0;
}

.input-with-button {
  display: flex;
  gap: 8px;
}

.send-code-btn {
  flex-shrink: 0;
}


.register-btn {
  width: 100%;
}

.form-footer {
  text-align: center;
  padding-top: 24px;
  border-top: 1px solid #e5e7eb;
}

.has-account {
  color: #6b7280;
  margin-right: 8px;
}

.login-link {
  color: #667eea;
  text-decoration: none;
  font-weight: 600;
}

/* 保持 brand-* 和其他通用样式不变 */
.brand-content { text-align: center; color: white; max-width: 400px; }
.logo { display: flex; align-items: center; justify-content: center; gap: 16px; margin-bottom: 24px; }
.logo-icon { font-size: 48px; }
.brand-title { font-size: 48px; font-weight: 700; margin: 0; background: linear-gradient(45deg, #fff, #e0e7ff); -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent; }
.brand-subtitle { font-size: 28px; margin-bottom: 40px; font-weight: 400; letter-spacing: 2px; background: linear-gradient(45deg, #fff, #e0e7ff); -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent; text-shadow: 0 0 10px rgba(255, 255, 255, 0.5); }
.brand-features { display: flex; flex-direction: column; gap: 20px; }
.feature-item { display: flex; align-items: center; gap: 12px; font-size: 16px; opacity: 0.9; }
.feature-icon { font-size: 20px; }

</style>