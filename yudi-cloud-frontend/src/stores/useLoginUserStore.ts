import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getLoginUserUsingGet } from '@/api/userController.ts'

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
    }
    // // 测试用户登录，3 秒后自动登录
    // setTimeout(() => {
    //   loginUser.value = { userName: '测试用户', id: 1 }
    // }, 3000)
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
