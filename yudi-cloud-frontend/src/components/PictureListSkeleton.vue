<template>
  <div class="picture-list-skeleton">
    <!-- 骨架屏网格布局 -->
    <div class="skeleton-grid">
      <div 
        v-for="index in skeletonCount" 
        :key="index" 
        class="skeleton-item"
        :style="{ animationDelay: `${(index - 1) * 0.1}s` }"
      >
        <!-- 图片骨架 -->
        <div class="skeleton-image">
          <div class="skeleton-placeholder"></div>
        </div>
        
        <!-- 内容骨架 -->
        <div class="skeleton-content">
          <!-- 标题骨架 -->
          <div class="skeleton-title"></div>
          
          <!-- 标签骨架 -->
          <div class="skeleton-tags">
            <div class="skeleton-tag"></div>
            <div class="skeleton-tag"></div>
          </div>
          
          <!-- 统计信息骨架 -->
          <div class="skeleton-stats">
            <div class="skeleton-stat"></div>
            <div class="skeleton-stat"></div>
            <div class="skeleton-stat"></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  count?: number // 骨架屏显示的数量
}

const props = withDefaults(defineProps<Props>(), {
  count: 12
})

const skeletonCount = computed(() => props.count)
</script>

<style scoped>
.picture-list-skeleton {
  width: 100%;
  padding: 20px;
}

.skeleton-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 24px;
  max-width: 1200px;
  margin: 0 auto;
}

.skeleton-item {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  animation: skeleton-pulse 1.5s ease-in-out infinite;
}

.skeleton-image {
  width: 100%;
  height: 200px;
  background: #f5f5f5;
  position: relative;
  overflow: hidden;
}

.skeleton-placeholder {
  width: 100%;
  height: 100%;
  background: linear-gradient(
    90deg,
    #f5f5f5 0%,
    #eeeeee 20%,
    #f5f5f5 40%,
    #f5f5f5 100%
  );
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.5s infinite linear;
}

.skeleton-content {
  padding: 16px;
}

.skeleton-title {
  height: 20px;
  background: linear-gradient(
    90deg,
    #f5f5f5 0%,
    #eeeeee 20%,
    #f5f5f5 40%,
    #f5f5f5 100%
  );
  background-size: 200% 100%;
  border-radius: 4px;
  margin-bottom: 12px;
  animation: skeleton-shimmer 1.5s infinite linear;
}

.skeleton-tags {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.skeleton-tag {
  height: 24px;
  width: 60px;
  background: linear-gradient(
    90deg,
    #f5f5f5 0%,
    #eeeeee 20%,
    #f5f5f5 40%,
    #f5f5f5 100%
  );
  background-size: 200% 100%;
  border-radius: 12px;
  animation: skeleton-shimmer 1.5s infinite linear;
}

.skeleton-stats {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.skeleton-stat {
  height: 16px;
  width: 50px;
  background: linear-gradient(
    90deg,
    #f5f5f5 0%,
    #eeeeee 20%,
    #f5f5f5 40%,
    #f5f5f5 100%
  );
  background-size: 200% 100%;
  border-radius: 4px;
  animation: skeleton-shimmer 1.5s infinite linear;
}

/* 动画效果 */
@keyframes skeleton-shimmer {
  0% {
    background-position: -200% 0;
  }
  100% {
    background-position: 200% 0;
  }
}

@keyframes skeleton-pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.8;
  }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .skeleton-grid {
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    gap: 16px;
    padding: 0 8px;
  }
  
  .skeleton-image {
    height: 160px;
  }
  
  .skeleton-content {
    padding: 12px;
  }
}

@media (max-width: 480px) {
  .skeleton-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }
  
  .skeleton-image {
    height: 180px;
  }
}
</style>