<template>
  <a-modal
    :open="visible"
    title="修改密码"
    :width="500"
    :footer="null"
    centered
    @cancel="handleCancel"
    @update:open="(val) => emit('update:visible', val)"
  >
    <div class="change-password-modal">
      <a-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        layout="vertical"
        @finish="handleSubmit"
      >
        <!-- 当前密码 -->
        <a-form-item label="当前密码" name="currentPassword">
          <a-input-password
            v-model:value="formData.currentPassword"
            placeholder="请输入当前密码"
            :maxlength="50"
            autocomplete="current-password"
          />
        </a-form-item>

        <!-- 新密码 -->
        <a-form-item label="新密码" name="newPassword">
          <a-input-password
            v-model:value="formData.newPassword"
            placeholder="请输入新密码"
            :maxlength="50"
            autocomplete="new-password"
          />
          <div class="password-strength">
            <div class="strength-bar">
              <div 
                class="strength-fill" 
                :class="strengthClass"
                :style="{ width: strengthWidth }"
              ></div>
            </div>
            <div class="strength-text" :class="strengthClass">
              {{ strengthText }}
            </div>
          </div>
        </a-form-item>

        <!-- 确认新密码 -->
        <a-form-item label="确认新密码" name="confirmPassword">
          <a-input-password
            v-model:value="formData.confirmPassword"
            placeholder="请再次输入新密码"
            :maxlength="50"
            autocomplete="new-password"
          />
        </a-form-item>

        <!-- 密码提示 -->
        <div class="password-tips">
          <a-alert
            message="密码安全提示"
            type="info"
            show-icon
            :closable="false"
          >
            <template #description>
              <ul>
                <li>密码长度至少6个字符</li>
                <li>建议包含字母、数字和特殊字符</li>
                <li>不要使用过于简单的密码</li>
                <li>定期更换密码以保证账户安全</li>
              </ul>
            </template>
          </a-alert>
        </div>

        <!-- 操作按钮 -->
        <a-form-item class="form-actions">
          <a-space size="middle">
            <a-button @click="handleCancel">
              取消
            </a-button>
            <a-button 
              type="primary" 
              html-type="submit" 
              :loading="loading"
              :disabled="!isFormValid"
              class="submit-btn"
            >
              确认修改
            </a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { message } from 'ant-design-vue'
import { changePasswordUsingPost } from '@/api/userController'

interface Props {
  visible: boolean
}

interface Emits {
  (e: 'update:visible', visible: boolean): void
  (e: 'success'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const formRef = ref()
const loading = ref(false)

// 表单数据
const formData = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 密码强度计算
const passwordStrength = computed(() => {
  const password = formData.newPassword
  if (!password) return { score: 0, text: '', width: '0%', class: '' }
  
  let score = 0
  let text = ''
  let width = '0%'
  let className = ''
  
  // 长度检查
  if (password.length >= 6) score += 1
  if (password.length >= 8) score += 1
  if (password.length >= 12) score += 1
  
  // 字符类型检查
  if (/[a-z]/.test(password)) score += 1
  if (/[A-Z]/.test(password)) score += 1
  if (/[0-9]/.test(password)) score += 1
  if (/[^a-zA-Z0-9]/.test(password)) score += 1
  
  // 根据分数确定强度
  if (score <= 2) {
    text = '弱'
    width = '25%'
    className = 'weak'
  } else if (score <= 4) {
    text = '中等'
    width = '50%'
    className = 'medium'
  } else if (score <= 6) {
    text = '强'
    width = '75%'
    className = 'strong'
  } else {
    text = '很强'
    width = '100%'
    className = 'very-strong'
  }
  
  return { score, text, width, class: className }
})

const strengthText = computed(() => passwordStrength.value.text)
const strengthWidth = computed(() => passwordStrength.value.width)
const strengthClass = computed(() => passwordStrength.value.class)

// 表单验证规则
const rules = {
  currentPassword: [
    { required: true, message: '请输入当前密码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少6个字符', trigger: 'blur' },
    { 
      validator: (rule: any, value: string) => {
        if (value && value === formData.currentPassword) {
          return Promise.reject('新密码不能与当前密码相同')
        }
        return Promise.resolve()
      }, 
      trigger: 'blur' 
    }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { 
      validator: (rule: any, value: string) => {
        if (value && value !== formData.newPassword) {
          return Promise.reject('两次输入的密码不一致')
        }
        return Promise.resolve()
      }, 
      trigger: 'blur' 
    }
  ]
}

// 表单是否有效
const isFormValid = computed(() => {
  return formData.currentPassword && 
         formData.newPassword && 
         formData.confirmPassword &&
         formData.newPassword === formData.confirmPassword &&
         formData.newPassword !== formData.currentPassword &&
         formData.newPassword.length >= 6
})

// 监听弹窗显示状态
watch(() => props.visible, (newVal) => {
  if (newVal) {
    resetForm()
  }
})

// 重置表单
const resetForm = () => {
  formData.currentPassword = ''
  formData.newPassword = ''
  formData.confirmPassword = ''
  formRef.value?.resetFields()
}

// 提交表单
const handleSubmit = async () => {
  try {
    loading.value = true
    
    const res = await changePasswordUsingPost({
      currentPassword: formData.currentPassword,
      newPassword: formData.newPassword,
      confirmPassword: formData.confirmPassword
    })
    
    if (res.data.code === 0) {
      message.success('密码修改成功，请重新登录')
      emit('success')
      handleCancel()
    } else {
      message.error(res.data.message || '密码修改失败')
    }
  } catch (error) {
    message.error('修改失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

// 取消操作
const handleCancel = () => {
  emit('update:visible', false)
  resetForm()
}
</script>

<style scoped>
.change-password-modal {
  padding: 0;
}

.password-strength {
  margin-top: 8px;
}

.strength-bar {
  width: 100%;
  height: 4px;
  background: #f0f0f0;
  border-radius: 2px;
  overflow: hidden;
  margin-bottom: 4px;
}

.strength-fill {
  height: 100%;
  transition: all 0.3s ease;
  border-radius: 2px;
}

.strength-fill.weak {
  background: #ff4d4f;
}

.strength-fill.medium {
  background: #faad14;
}

.strength-fill.strong {
  background: #52c41a;
}

.strength-fill.very-strong {
  background: #1890ff;
}

.strength-text {
  font-size: 12px;
  font-weight: 500;
  text-align: right;
}

.strength-text.weak {
  color: #ff4d4f;
}

.strength-text.medium {
  color: #faad14;
}

.strength-text.strong {
  color: #52c41a;
}

.strength-text.very-strong {
  color: #1890ff;
}

.password-tips {
  margin: 16px 0;
}

.password-tips ul {
  margin: 8px 0 0 0;
  padding-left: 20px;
}

.password-tips li {
  margin-bottom: 4px;
  font-size: 12px;
  color: #666;
}

.form-actions {
  margin-bottom: 0;
  text-align: right;
}

.submit-btn {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  border: none;
  border-radius: 6px;
  height: 40px;
  padding: 0 24px;
  font-weight: 500;
  transition: all 0.3s ease;
}

.submit-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #ee82f0 0%, #f3455a 100%);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(240, 147, 251, 0.3);
}

.submit-btn:disabled {
  background: #d9d9d9;
  color: #999;
  cursor: not-allowed;
}

:deep(.ant-form-item-label > label) {
  font-weight: 600;
  color: #262626;
}

:deep(.ant-input-password) {
  border-radius: 6px;
  border: 1px solid #d9d9d9;
  transition: all 0.3s ease;
}

:deep(.ant-input-password:focus) {
  border-color: #f093fb;
  box-shadow: 0 0 0 2px rgba(240, 147, 251, 0.1);
}

:deep(.ant-alert) {
  border-radius: 6px;
  border: none;
  background: #f6f8ff;
}

:deep(.ant-alert-message) {
  color: #1890ff;
  font-weight: 600;
}
</style>