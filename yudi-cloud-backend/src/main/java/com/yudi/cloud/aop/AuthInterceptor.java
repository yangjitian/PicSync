package com.yudi.cloud.aop;

import com.yudi.cloud.annotation.AuthCheck;
import com.yudi.cloud.exception.BusinessException;
import com.yudi.cloud.exception.ErrorCode;
import com.yudi.cloud.model.entity.User;
import com.yudi.cloud.model.enums.UserRoleEnum;
import com.yudi.cloud.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@Component
@Aspect
@Slf4j
public class AuthInterceptor {

    @Resource
    private UserService userService;

    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole = authCheck.mustRole();
        log.info("权限检查开始 - 需要角色: {}", mustRole);
        
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        User loginUser = userService.getLoginUser(request);
        log.info("当前登录用户: {}, 用户角色: {}", loginUser.getUserAccount(), loginUser.getUserRole());
        
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
        if(mustRoleEnum == null){
            log.info("未指定必需角色，允许访问");
            return joinPoint.proceed();
        }
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        
        // 检查用户是否有足够权限
        if (UserRoleEnum.ADMIN.equals(mustRoleEnum)) {
            // 如果需要ADMIN权限，用户必须是ADMIN
            if (!UserRoleEnum.ADMIN.equals(userRoleEnum)) {
                log.warn("权限不足 - 用户角色: {}, 需要角色: {}", userRoleEnum, mustRoleEnum);
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "需要管理员权限");
            }
            log.info("权限检查通过 - 用户具有管理员权限");
        }
        
        return joinPoint.proceed();
    }
}
