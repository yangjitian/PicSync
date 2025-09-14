<template>
  <a-modal
    :open="visible"
    title="编辑个人资料"
    :width="600"
    :footer="null"
    centered
    @cancel="handleCancel"
    @update:open="(val) => emit('update:visible', val)"
  >
    <div class="edit-profile-modal">
      <a-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        layout="vertical"
        @finish="handleSubmit"
      >
        <!-- 头像上传区域 -->
        <a-form-item label="头像" name="userAvatar">
          <div class="avatar-upload-section">
            <div class="avatar-preview">
              <a-avatar :size="100" :src="formData.userAvatar" class="preview-avatar">
                <UserOutlined v-if="!formData.userAvatar" />
              </a-avatar>
              <div class="avatar-overlay">
                <a-upload
                  :show-upload-list="false"
                  :custom-request="handleAvatarUpload"
                  :before-upload="beforeAvatarUpload"
                  accept="image/*"
                >
                  <div class="upload-trigger">
                    <CameraOutlined />
                    <span>更换头像</span>
                  </div>
                </a-upload>
              </div>
            </div>
            <div class="avatar-tips">
              <p>支持 JPG、PNG、GIF 格式，文件大小不超过 5MB</p>
            </div>
          </div>
        </a-form-item>

        <!-- 用户名 -->
        <a-form-item label="用户名" name="userName">
          <a-input
            v-model:value="formData.userName"
            placeholder="请输入用户名"
            :maxlength="20"
            show-count
          />
        </a-form-item>

        <!-- 个人简介 -->
        <a-form-item label="个人简介" name="userProfile">
          <a-textarea
            v-model:value="formData.userProfile"
            placeholder="请输入个人简介"
            :rows="4"
            :maxlength="200"
            show-count
          />
        </a-form-item>

        <!-- 生日 -->
        <a-form-item label="生日" name="birthday">
          <a-date-picker
            v-model:value="formData.birthday"
            placeholder="请选择生日"
            style="width: 100%"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
          />
        </a-form-item>

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
              class="submit-btn"
            >
              保存修改
            </a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </div>

  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { message } from 'ant-design-vue'
import { UserOutlined, CameraOutlined } from '@ant-design/icons-vue'
import { updateUserSelfUsingPost } from '@/api/userController'
import { uploadPictureUsingPost } from '@/api/pictureController'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import dayjs from 'dayjs'
import type { UploadProps } from 'ant-design-vue'

interface Props {
  visible: boolean
  userInfo: API.UserVO
}

interface Emits {
  (e: 'update:visible', visible: boolean): void
  (e: 'success'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const loginUserStore = useLoginUserStore()
const formRef = ref()
const loading = ref(false)
const avatarUploading = ref(false)

// 表单数据
const formData = reactive({
  userName: '',
  userProfile: '',
  userAvatar: '',
  birthday: null as string | null
})

// 表单验证规则
const rules = {
  userName: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '用户名长度为2-20个字符', trigger: 'blur' }
  ],
  userProfile: [
    { max: 200, message: '个人简介不能超过200个字符', trigger: 'blur' }
  ]
}

// 监听弹窗显示状态
watch(() => props.visible, (newVal) => {
  if (newVal) {
    initFormData()
  }
})

// 初始化表单数据
const initFormData = () => {
  formData.userName = props.userInfo.userName || ''
  formData.userProfile = props.userInfo.userProfile || ''
  formData.userAvatar = props.userInfo.userAvatar || ''
  formData.birthday = props.userInfo.birthday ? dayjs(props.userInfo.birthday).format('YYYY-MM-DD') : null
}

// 头像上传处理
const handleAvatarUpload = async ({ file }: any) => {
  avatarUploading.value = true
  try {
    const res = await uploadPictureUsingPost({}, {}, file)
    if (res.data.code === 0 && res.data.data) {
      formData.userAvatar = res.data.data.url
      message.success('头像上传成功')
    } else {
      message.error('头像上传失败，' + res.data.message)
    }
  } catch (error) {
    console.error('头像上传失败:', error)
    message.error('头像上传失败，请稍后重试')
  } finally {
    avatarUploading.value = false
  }
}

// 上传前校验
const beforeAvatarUpload = (file: UploadProps['fileList'][number]) => {
  const isJpgOrPng = file.type === 'image/jpeg' || file.type === 'image/png' || file.type === 'image/gif'
  if (!isJpgOrPng) {
    message.error('不支持上传该格式的图片，推荐 jpg、png 或 gif')
    return false
  }
  // 校验图片大小
  const isLt5M = file.size / 1024 / 1024 < 5
  if (!isLt5M) {
    message.error('不能上传超过 5MB 的图片')
    return false
  }
  return true
}

// 提交表单
const handleSubmit = async () => {
  try {
    loading.value = true
    
    const res = await updateUserSelfUsingPost({
      userName: formData.userName,
      userProfile: formData.userProfile,
      userAvatar: formData.userAvatar,
      birthday: formData.birthday
    })
    
    if (res.data.code === 0) {
      message.success('个人资料更新成功')
      emit('success')
      handleCancel()
    } else {
      message.error(res.data.message || '更新失败')
    }
  } catch (error) {
    console.error('更新个人资料失败:', error)
    message.error('更新失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

// 取消操作
const handleCancel = () => {
  emit('update:visible', false)
  // 重置表单
  formRef.value?.resetFields()
}
</script>

<style scoped>
.edit-profile-modal {
  padding: 0;
}

.avatar-upload-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.avatar-preview {
  position: relative;
  display: inline-block;
}

.preview-avatar {
  border: 3px solid #f0f0f0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.avatar-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: white;
  border-radius: 50%;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.avatar-preview:hover .avatar-overlay {
  opacity: 1;
}

.upload-trigger {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  width: 100%;
  height: 100%;
}

.upload-trigger .anticon {
  font-size: 24px;
  margin-bottom: 4px;
}

.upload-trigger span {
  font-size: 12px;
  font-weight: 500;
}

:deep(.avatar-overlay .ant-upload) {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

:deep(.avatar-overlay .ant-upload .ant-upload-select) {
  width: 100%;
  height: 100%;
  border: none;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-tips {
  text-align: center;
}

.avatar-tips p {
  margin: 0;
  color: #8c8c8c;
  font-size: 12px;
}

.form-actions {
  margin-bottom: 0;
  text-align: right;
}

.submit-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  border-radius: 6px;
  height: 40px;
  padding: 0 24px;
  font-weight: 500;
  transition: all 0.3s ease;
}

.submit-btn:hover {
  background: linear-gradient(135deg, #5a6fd8 0%, #6a4190 100%);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

:deep(.ant-form-item-label > label) {
  font-weight: 600;
  color: #262626;
}

:deep(.ant-input),
:deep(.ant-input-number),
:deep(.ant-picker) {
  border-radius: 6px;
  border: 1px solid #d9d9d9;
  transition: all 0.3s ease;
}

:deep(.ant-input:focus),
:deep(.ant-input-number:focus),
:deep(.ant-picker:focus) {
  border-color: #667eea;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.1);
}

:deep(.ant-textarea) {
  border-radius: 6px;
  border: 1px solid #d9d9d9;
  transition: all 0.3s ease;
}

:deep(.ant-textarea:focus) {
  border-color: #667eea;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.1);
}
</style>