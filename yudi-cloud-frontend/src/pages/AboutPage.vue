<template>
  <div class="about-page">
    <div class="about-container">
      <div class="about-content">
        <h1 class="about-title">Hello, I am 雨滴</h1>
        <div class="about-description">
          <p>欢迎来到 PicSync 云见图！</p>
          <p>我是雨滴，这个项目的开发者。</p>
          <p>感谢您的使用和支持！</p>
        </div>
        <div class="about-actions">
          <a-button type="primary" @click="goHome">返回首页</a-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const goHome = () => {
  router.push('/')
}

</script>

<style scoped>
.about-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.about-container {
  max-width: 600px;
  width: 100%;
}

.about-content {
  background: white;
  border-radius: 16px;
  padding: 48px 32px;
  text-align: center;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.1);
  animation: fadeInUp 0.8s ease-out;
}

.about-title {
  font-size: 2.5rem;
  font-weight: 700;
  color: #333;
  margin-bottom: 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.about-description {
  margin-bottom: 32px;
}

.about-description p {
  font-size: 1.1rem;
  color: #666;
  line-height: 1.8;
  margin-bottom: 12px;
}

.about-actions {
  margin-top: 32px;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .about-content {
    padding: 32px 24px;
  }
  
  .about-title {
    font-size: 2rem;
  }
  
  .about-description p {
    font-size: 1rem;
  }
}
</style>