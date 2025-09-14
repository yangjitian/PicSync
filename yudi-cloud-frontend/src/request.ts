import axios from "axios";
import {message} from "ant-design-vue";

// 区分开发和生产环境
const DEV_BASE_URL = "http://localhost:8123";
// const PROD_BASE_URL = "http://81.69.229.63";
// 创建 Axios 实例
const myAxios = axios.create({
    baseURL: DEV_BASE_URL,
    timeout: 10000,
    withCredentials: true,
});

// 全局请求拦截器
myAxios.interceptors.request.use(
  function (config) {
    // 从localStorage获取Sa-Token并添加到请求头
    const satoken = localStorage.getItem('satoken');
    if (satoken) {
      config.headers['satoken'] = satoken;
    }
    return config
  },
  function (error) {
    // Do something with request error
    return Promise.reject(error)
  },
)

// 全局响应拦截器
myAxios.interceptors.response.use(
  function (response) {
    const { data } = response
    
    // 保存Sa-Token到localStorage
    const satoken = response.headers['satoken'];
    if (satoken) {
      localStorage.setItem('satoken', satoken);
    }
    
    // 处理业务错误
    if (data.code !== 0) {
      // 未登录
      if (data.code === 40100) {
        // 清除无效的token
        localStorage.removeItem('satoken');
        // 不是获取用户信息的请求，并且用户目前不是已经在用户登录或注册页面，则跳转到登录页面
        if (
          !response.request.responseURL.includes('user/get/login') &&
          !window.location.pathname.includes('/user/login') &&
          !window.location.pathname.includes('/user/register')
        ) {
          message.warning('请先登录')
          window.location.href = `/user/login?redirect=${window.location.href}`
        }
      } else {
        // 其他业务错误，抛出异常让组件处理
        const error = new Error(data.message || '请求失败');
        (error as any).response = response;
        return Promise.reject(error);
      }
    }
    return response
  },
  function (error) {
    // Any status codes that falls outside the range of 2xx cause this function to trigger
    // Do something with response error
    return Promise.reject(error)
  },
)

export default myAxios;
