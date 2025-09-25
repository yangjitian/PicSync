<template>
  <div>
    <a-modal v-model:visible="visible" :title="title" :footer="false" @cancel="closeModal">
      <h4>复制分享链接</h4>
      <a-typography-link copyable>
        {{ link }}
      </a-typography-link>
      <div style="margin-bottom: 16px" />
      <h4>手机扫码查看</h4>
      <div v-if="link">
        <a-qrcode 
          :value="link" 
          :size="200"
          type="svg"
          :bordered="false"
          :error-level="'M'"
        />
      </div>
      <div v-else style="color: red;">
        分享链接为空，无法生成二维码
      </div>
      <div style="margin-top: 10px; font-size: 12px; color: #666;">
        分享链接: {{ link }}
      </div>
      <!-- 调试信息 -->
      <div v-if="!link" style="margin-top: 10px; font-size: 12px; color: #999;">
        调试信息: link值为空
      </div>
    </a-modal>
  </div>
</template>
<script lang="ts" setup>
import { ref } from 'vue'

interface Props {
  title: string;
  link: string;
}

const props = withDefaults(defineProps<Props>(), {
  title: "分享图片",
  link: 'https://www.codefather.cn'
})

// 是否可见
const visible = ref(false)

// 打开弹窗
const openModal = () => {
  visible.value = true
}

// 关闭弹窗
const closeModal = () => {
  visible.value = false;
}

// 暴露函数给父组件
defineExpose({
  openModal,
})
</script>
