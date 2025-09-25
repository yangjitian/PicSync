<template>
  <div id="app">
    <a-config-provider :locale="zhCN">
      <BasicLayout />
    </a-config-provider>
  </div>
</template>

<script setup lang="ts">
import zhCN from 'ant-design-vue/es/locale/zh_CN';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
dayjs.locale('zh-cn');

import BasicLayout from '@/layouts/BasicLayout.vue'
// import { healthUsingGet } from '@/api/mainController.ts'
// import {useLoginUserStore} from "@/stores/useLoginUserStore.ts";

// 已经改为在权限校验文件中获取
// const loginUserStore = useLoginUserStore()
// loginUserStore.fetchLoginUser()

// healthUsingGet().then((res) => {
// })
</script>

<style scoped></style>

<style>
/* 全局样式，确保页面可以正常滚动 */
html, body {
  height: auto;
  min-height: 100%;
  overflow-x: hidden;
  overflow-y: auto;
}

#app {
  min-height: 100vh;
  position: relative;
}

/* 修复Ant Design Vue下拉选项滚动问题 */
.ant-select-dropdown {
  position: absolute !important;
  z-index: 1050 !important;
  transform: none !important;
}

.ant-auto-complete-dropdown {
  position: absolute !important;
  z-index: 1050 !important;
  transform: none !important;
}
</style>
