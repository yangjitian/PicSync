import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getLoginUserUsingGet, getUserVoByIdUsingGet } from '@/api/userController.ts'

/**
 * 存储登录用户信息的状态
 */
export const useLoginUserStore = defineStore('loginUser', () => {
  const loginUser = ref<API.LoginUserVO>({
    userName: '未登录',
  })

  /**
   * 远程获取登录用户信息
   */
  async function fetchLoginUser() {
    const res = await getLoginUserUsingGet()
    if (res.data.code === 0 && res.data.data) {
      loginUser.value = res.data.data
      // 进一步拉取完整的用户信息（包含 VIP 相关字段）
      if (loginUser.value?.id) {
        try {
          const detailRes = await getUserVoByIdUsingGet({ id: loginUser.value.id })
          if (detailRes.data.code === 0 && detailRes.data.data) {
            // 合并关键字段到登录用户信息中
            loginUser.value = {
              ...loginUser.value,
              userRole: detailRes.data.data.userRole ?? loginUser.value.userRole,
              vipNumber: detailRes.data.data.vipNumber,
              vipExpireTime: detailRes.data.data.vipExpireTime,
            }
          }
        } catch (e) {
          // 忽略错误，保持基础登录信息
        }
      }
    }
  }

  /**
   * 设置登录用户
   * @param newLoginUser
   */
  function setLoginUser(newLoginUser: any) {
    loginUser.value = newLoginUser
  }

  /**
   * 更新登录用户信息
   * @param userInfo 用户信息
   */
  function updateLoginUser(userInfo: API.UserVO) {
    if (userInfo) {
      loginUser.value = {
        ...loginUser.value,
        userName: userInfo.userName || loginUser.value.userName,
        userAvatar: userInfo.userAvatar || loginUser.value.userAvatar,
        userRole: userInfo.userRole || loginUser.value.userRole,
        vipNumber: userInfo.vipNumber || loginUser.value.vipNumber,
        vipExpireTime: userInfo.vipExpireTime || loginUser.value.vipExpireTime
      }
    }
  }

  // 返回
  return { loginUser, fetchLoginUser, setLoginUser, updateLoginUser }
})
