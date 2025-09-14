// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** getCaptcha GET /api/auth/captcha */
export async function getCaptchaUsingGet(options?: { [key: string]: any }) {
  return request<API.BaseResponseMapStringString_>('/api/auth/captcha', {
    method: 'GET',
    ...(options || {}),
  })
}

/** sendLoginVerificationCode POST /api/auth/send-login-verification-code */
export async function sendLoginVerificationCodeUsingPost(
  body: API.EmailRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean_>('/api/auth/send-login-verification-code', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** sendVerificationCode POST /api/auth/send-verification-code */
export async function sendVerificationCodeUsingPost(
  body: API.EmailRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean_>('/api/auth/send-verification-code', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** sendResetPasswordCode POST /api/auth/send-reset-password-code */
export async function sendResetPasswordCodeUsingPost(
  body: API.EmailRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean_>('/api/auth/send-reset-password-code', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** resetPassword POST /api/auth/reset-password */
export async function resetPasswordUsingPost(
  body: API.ResetPasswordRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean_>('/api/auth/reset-password', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}