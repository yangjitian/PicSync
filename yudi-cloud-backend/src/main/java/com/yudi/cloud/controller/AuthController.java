package com.yudi.cloud.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.crypto.digest.BCrypt;
import com.yudi.cloud.common.BaseResponse;
import com.yudi.cloud.common.Result;
import com.yudi.cloud.exception.BusinessException;
import com.yudi.cloud.exception.ErrorCode;
import com.yudi.cloud.exception.ThrowUtils;
import com.yudi.cloud.model.dto.auth.EmailRequest;
import com.yudi.cloud.model.dto.auth.ResetPasswordRequest;
import com.yudi.cloud.model.entity.User;
import com.yudi.cloud.service.EmailService;
import com.yudi.cloud.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.beans.factory.annotation.Autowired;
import com.wf.captcha.SpecCaptcha;
import com.wf.captcha.base.Captcha;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/auth")
@Slf4j
public class AuthController {

    public static final String CAPTCHA_SESSION_KEY = "captcha_code";
    private static final String VERIFICATION_CODE_PREFIX = "verification:code:";

    @Resource
    private UserService userService;

    @Resource
    private EmailService emailService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @SaIgnore
    @PostMapping("/send-verification-code")
    public BaseResponse<Boolean> sendVerificationCode(@RequestBody EmailRequest emailRequest) {
        log.info("收到发送验证码请求: {}", emailRequest);
        String email = emailRequest.getEmail();
        ThrowUtils.throwIf(email == null, ErrorCode.PARAMETER_ERROR, "邮箱不能为空");

        // 1. 校验邮箱格式
        ThrowUtils.throwIf(!EmailValidator.getInstance().isValid(email), ErrorCode.PARAMETER_ERROR, "无效的邮箱地址");

        // 2. 校验邮箱是否已注册
        User user = userService.lambdaQuery().eq(User::getUserAccount, email).one();
        ThrowUtils.throwIf(user != null, ErrorCode.PARAMETER_ERROR, "该邮箱已被注册");

        // 3. 生成6位验证码
        String code = RandomStringUtils.randomNumeric(6);
        log.info("生成验证码: {}", code);

        // 4. 存储到Redis，有效期5分钟
        try {
            redisTemplate.opsForValue().set(VERIFICATION_CODE_PREFIX + email, code, 5, TimeUnit.MINUTES);
            log.info("验证码已存储到Redis");
        } catch (Exception e) {
            log.error("Redis 存储验证码失败", e);
            ThrowUtils.throwIf(true, ErrorCode.SYSTEM_ERROR, "验证码发送失败，请稍后重试");
        }

        // 5. 发送邮件
        String subject = "【PicSync云见图】注册验证码";
        String content = "您好，您的注册验证码是：" + code + "，有效期为5分钟。请勿泄露给他人。";
        log.info("准备发送邮件到: {}", email);
        emailService.sendEmail(email, subject, content);
        log.info("邮件发送完成");

        return Result.success(true);
    }

    @SaIgnore
    @PostMapping("/send-login-verification-code")
    public BaseResponse<Boolean> sendLoginVerificationCode(@RequestBody EmailRequest emailRequest) {
        String email = emailRequest.getEmail();
        ThrowUtils.throwIf(email == null, ErrorCode.PARAMETER_ERROR, "邮箱不能为空");

        // 1. 校验邮箱格式
        ThrowUtils.throwIf(!EmailValidator.getInstance().isValid(email), ErrorCode.PARAMETER_ERROR, "无效的邮箱地址");

        // 2. 校验邮箱是否已注册
        User user = userService.lambdaQuery().eq(User::getUserAccount, email).one();
        ThrowUtils.throwIf(user == null, ErrorCode.PARAMETER_ERROR, "该邮箱未注册");

        // 3. 生成6位验证码
        String code = RandomStringUtils.randomNumeric(6);

        // 4. 存储到Redis，有效期5分钟
        try {
            redisTemplate.opsForValue().set(VERIFICATION_CODE_PREFIX + email, code, 5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Redis 存储验证码失败", e);
            ThrowUtils.throwIf(true, ErrorCode.SYSTEM_ERROR, "验证码发送失败，请稍后重试");
        }

        // 5. 发送邮件
        String subject = "【PicSync云见图】登录验证码";
        String content = "您好，您的登录验证码是：" + code + "，有效期为5分钟。请勿泄露给他人。";
        emailService.sendEmail(email, subject, content);

        return Result.success(true);
    }

    @SaIgnore
    @GetMapping("/captcha")
    public BaseResponse<Map<String, String>> getCaptcha(HttpServletRequest request) {
        // 使用 easy-captcha 生成验证码
        SpecCaptcha captcha = new SpecCaptcha(130, 48, 4);
        captcha.setCharType(Captcha.TYPE_DEFAULT);

        String code = captcha.text().toLowerCase();
        String imageBase64 = captcha.toBase64();

        // 存储验证码
        request.getSession().setAttribute(CAPTCHA_SESSION_KEY, code);

        // 返回完整 DataURL
        Map<String, String> response = new HashMap<>();
        response.put("captchaImage", imageBase64);

        return Result.success(response);
    }

    @SaIgnore
    @PostMapping("/send-reset-password-code")
    public BaseResponse<Boolean> sendResetPasswordCode(@RequestBody EmailRequest emailRequest) {
        String email = emailRequest.getEmail();
        ThrowUtils.throwIf(email == null, ErrorCode.PARAMETER_ERROR, "邮箱不能为空");

        // 1. 校验邮箱格式
        ThrowUtils.throwIf(!EmailValidator.getInstance().isValid(email), ErrorCode.PARAMETER_ERROR, "无效的邮箱地址");

        // 2. 校验邮箱是否已注册
        User user = userService.lambdaQuery().eq(User::getUserAccount, email).one();
        ThrowUtils.throwIf(user == null, ErrorCode.PARAMETER_ERROR, "该邮箱未注册");

        // 3. 生成6位验证码
        String code = RandomStringUtils.randomNumeric(6);

        // 4. 存储到Redis，有效期5分钟
        try {
            redisTemplate.opsForValue().set(VERIFICATION_CODE_PREFIX + email, code, 5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Redis 存储验证码失败", e);
            ThrowUtils.throwIf(true, ErrorCode.SYSTEM_ERROR, "验证码发送失败，请稍后重试");
        }

        // 5. 发送邮件
        String subject = "【PicSync云见图】密码重置验证码";
        String content = "您好，您的密码重置验证码是：" + code + "，有效期为5分钟。请勿泄露给他人。";
        emailService.sendEmail(email, subject, content);

        return Result.success(true);
    }

    @SaIgnore
    @PostMapping("/reset-password")
    public BaseResponse<Boolean> resetPassword(@RequestBody ResetPasswordRequest resetPasswordRequest) {
        String email = resetPasswordRequest.getEmail();
        String verificationCode = resetPasswordRequest.getVerificationCode();
        String newPassword = resetPasswordRequest.getNewPassword();
        String confirmPassword = resetPasswordRequest.getConfirmPassword();

        // 1. 参数校验
        ThrowUtils.throwIf(email == null, ErrorCode.PARAMETER_ERROR, "邮箱不能为空");
        ThrowUtils.throwIf(verificationCode == null, ErrorCode.PARAMETER_ERROR, "验证码不能为空");
        ThrowUtils.throwIf(newPassword == null, ErrorCode.PARAMETER_ERROR, "新密码不能为空");
        ThrowUtils.throwIf(confirmPassword == null, ErrorCode.PARAMETER_ERROR, "确认密码不能为空");

        // 2. 校验密码长度
        ThrowUtils.throwIf(newPassword.length() < 6, ErrorCode.PARAMETER_ERROR, "密码长度不能少于6位");

        // 3. 校验密码确认
        ThrowUtils.throwIf(!newPassword.equals(confirmPassword), ErrorCode.PARAMETER_ERROR, "两次输入的密码不一致");

        // 4. 校验邮箱格式
        ThrowUtils.throwIf(!EmailValidator.getInstance().isValid(email), ErrorCode.PARAMETER_ERROR, "无效的邮箱地址");

        // 5. 校验验证码
        String storedCode = null;
        try {
            storedCode = redisTemplate.opsForValue().get(VERIFICATION_CODE_PREFIX + email);
        } catch (Exception e) {
            log.error("Redis 获取验证码失败", e);
            ThrowUtils.throwIf(true, ErrorCode.SYSTEM_ERROR, "验证码验证失败，请稍后重试");
        }
        ThrowUtils.throwIf(storedCode == null, ErrorCode.PARAMETER_ERROR, "验证码已过期或不存在");
        ThrowUtils.throwIf(!verificationCode.equals(storedCode), ErrorCode.PARAMETER_ERROR, "验证码错误");

        // 6. 查找用户
        User user = userService.lambdaQuery().eq(User::getUserAccount, email).one();
        ThrowUtils.throwIf(user == null, ErrorCode.PARAMETER_ERROR, "用户不存在");

        // 7. 检查新密码是否与当前密码相同
        boolean isSamePassword = BCrypt.checkpw(newPassword, user.getUserPassword());
        
        if (isSamePassword) {
            // 新密码与当前密码相同，跳过数据库更新，但删除验证码并返回成功
            log.info("用户 {} 输入的新密码与当前密码相同，跳过数据库更新", email);
            try {
                redisTemplate.delete(VERIFICATION_CODE_PREFIX + email);
            } catch (Exception e) {
                log.error("Redis 删除验证码失败", e);
            }
            return Result.success(true);
        }

        // 8. 加密新密码并更新
        String encryptedPassword = userService.getEncryptPassword(newPassword);
        boolean result = userService.lambdaUpdate()
                .eq(User::getId, user.getId())
                .set(User::getUserPassword, encryptedPassword)
                .update();

        // 9. 删除验证码（一次性使用）
        try {
            redisTemplate.delete(VERIFICATION_CODE_PREFIX + email);
        } catch (Exception e) {
            log.error("Redis 删除验证码失败", e);
        }

        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "密码重置失败");
        return Result.success(true);
    }

}
