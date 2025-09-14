<template>
  <div id="userLoginPage">
    <div class="login-container">
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

      <!-- 右侧登录表单区域 -->
      <div class="form-section">
        <div class="form-container">
          <div class="form-header">
            <h2 class="form-title">欢迎回来</h2>
            <p class="form-subtitle">登录您的 PicSync 账户</p>
          </div>

          <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit" class="login-form">
            <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号' }]">
              <a-input size="large" v-model:value="formState.userAccount" placeholder="请输入账号" class="custom-input" />
            </a-form-item>

            <a-form-item
              name="userPassword"
              :rules="[
                { required: true, message: '请输入密码' },
                { min: 6, message: '密码长度不能小于 6 位' },
              ]"
            >
              <a-input-password size="large" v-model:value="formState.userPassword" placeholder="请输入密码" class="custom-input" />
            </a-form-item>

            <a-form-item name="captcha" :rules="[{ required: true, message: '请输入图形验证码' }]">
              <div class="captcha-wrapper">
                <a-input size="large" v-model:value="formState.captcha" placeholder="图形验证码" class="custom-input" />
                <img :src="captchaImage" @click="getCaptcha" class="captcha-image" alt="Captcha" />
              </div>
            </a-form-item>

            <div class="form-actions">
              <a-checkbox v-model:checked="formState.rememberMe" class="remember-me">记住我</a-checkbox>
              <RouterLink to="/user/forgot-password" class="forgot-password">忘记密码？</RouterLink>
            </div>

            <a-form-item>
              <a-button size="large" class="login-btn" type="primary" html-type="submit" :loading="loading">
                登录
              </a-button>
            </a-form-item>
          </a-form>

          <div class="form-footer">
            <span class="no-account">还没有账户？</span>
            <RouterLink to="/user/register" class="register-link">立即注册</RouterLink>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
<script lang="ts" setup>
import { onMounted, reactive, ref } from 'vue'
import { userLoginUsingPost } from '@/api/userController.ts'
import { getCaptchaUsingGet } from '@/api/authController'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { message } from 'ant-design-vue'
import router from '@/router'

const formState = reactive<any>({
  userAccount: '',
  userPassword: '',
  captcha: '',
  rememberMe: false,
})

const captchaImage = ref('')
const loading = ref(false)

const getCaptcha = async () => {
  try {
    const res = await getCaptchaUsingGet();
    if (res && res.data.code === 0 && res.data.data) {
      captchaImage.value = res.data.data.captchaImage;
    } else {
      message.error('获取验证码数据失败');
    }
  } catch (error) {
    console.error("Failed to get captcha:", error);
    message.error('获取验证码请求失败');
  }
};

onMounted(() => {
  getCaptcha();
});

const loginUserStore = useLoginUserStore()

const handleSubmit = async (values: any) => {
  loading.value = true;
  try {
    const res = await userLoginUsingPost(values);
    if (res.data.code === 0 && res.data.data) {
      await loginUserStore.fetchLoginUser();
      message.success('登录成功');
      // 检查是否有重定向参数
      const redirect = router.currentRoute.value.query.redirect as string;
      if (redirect) {
        router.replace(redirect);
      } else {
        router.replace('/');
      }
    } else {
      message.error('登录失败，' + res.data.message);
      // 登录失败后，刷新验证码
      getCaptcha();
    }
  } catch (error) {
    console.error('登录错误:', error);
    message.error('登录失败，请稍后重试');
    getCaptcha();
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
/* 保持之前的布局和品牌区样式 */
#userLoginPage { min-height: 100vh; background: linear-gradient(to left, #ffdde1 0%, #a6c1ee 100%); position: relative; overflow: hidden; }
.login-container { display: flex; min-height: 100vh; position: relative; z-index: 1; }
.brand-section { flex: 4; display: flex; align-items: center; justify-content: center; padding: 60px 40px; background: rgba(255, 255, 255, 0.1); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px); border-right: 1px solid rgba(255, 255, 255, 0.2); }
.brand-content { text-align: center; color: white; max-width: 400px; }
.logo { display: flex; align-items: center; justify-content: center; gap: 16px; margin-bottom: 24px; }
.logo-icon { font-size: 48px; }
.brand-title { font-size: 48px; font-weight: 700; margin: 0; background: linear-gradient(45deg, #fff, #e0e7ff); -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent; }
.brand-subtitle { font-size: 28px; margin-bottom: 40px; font-weight: 400; letter-spacing: 2px; background: linear-gradient(45deg, #fff, #e0e7ff); -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent; text-shadow: 0 0 10px rgba(255, 255, 255, 0.5); }
.brand-features { display: flex; flex-direction: column; gap: 20px; }
.feature-item { display: flex; align-items: center; gap: 12px; font-size: 16px; opacity: 0.9; }
.feature-icon { font-size: 20px; }
.form-section { flex: 6; display: flex; align-items: center; justify-content: center; padding: 60px 40px; }
.form-container { width: 100%; max-width: 400px; background: rgba(255, 255, 255, 0.95); border-radius: 24px; padding: 48px 40px; box-shadow: 0 20px 40px rgba(0, 0, 0, 0.1); animation: slideInRight 0.8s ease-out forwards; }
@keyframes slideInRight { from { opacity: 0; transform: translateX(30px); } to { opacity: 1; transform: translateX(0); } }
.form-header { text-align: center; margin-bottom: 40px; }
.form-title { font-size: 32px; font-weight: 700; color: #1f2937; margin: 0 0 8px 0; }
.form-subtitle { font-size: 16px; color: #6b7280; margin: 0; }
.login-form { margin-bottom: 24px; }
.form-actions { display: flex; justify-content: space-between; align-items: center; margin-bottom: 32px; }
.remember-me { color: #6b7280; }
.forgot-password { color: #667eea; text-decoration: none; font-weight: 500; }
.login-btn { width: 100%; }
.form-footer { text-align: center; padding-top: 24px; border-top: 1px solid #e5e7eb; }
.no-account { color: #6b7280; margin-right: 8px; }
.register-link { color: #667eea; text-decoration: none; font-weight: 600; }

/* 新增验证码样式 */
.captcha-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
}

.captcha-image {
  height: 44px; /* 与输入框高度保持一致 */
  cursor: pointer;
  border-radius: 6px;
}

.email-captcha-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
}

.send-email-code-btn {
  flex-shrink: 0;
}

.custom-input {
  height: 44px !important;
}

</style>