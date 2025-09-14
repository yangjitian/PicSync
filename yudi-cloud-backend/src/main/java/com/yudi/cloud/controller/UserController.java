package com.yudi.cloud.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yudi.cloud.annotation.AuthCheck;
import com.yudi.cloud.common.BaseResponse;
import com.yudi.cloud.common.DeleteRequest;
import com.yudi.cloud.common.Result;
import com.yudi.cloud.contstant.UserConstant;
import com.yudi.cloud.exception.BusinessException;
import com.yudi.cloud.exception.ErrorCode;
import com.yudi.cloud.exception.ThrowUtils;
import com.yudi.cloud.model.dto.user.*;
import com.yudi.cloud.model.entity.User;
import com.yudi.cloud.model.vo.user.UserLoginVO;
import com.yudi.cloud.model.vo.user.UserVO;
import com.yudi.cloud.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    @SaIgnore
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterDTO userRegisterDTO, HttpServletRequest request) {
        ThrowUtils.throwIf(userRegisterDTO == null, ErrorCode.PARAMETER_ERROR);
        String userAccount = userRegisterDTO.getUserAccount();
        String userPassword = userRegisterDTO.getUserPassword();
        String checkPassword = userRegisterDTO.getCheckPassword();
        String verificationCode = userRegisterDTO.getVerificationCode();
        long result = userService.userRegister(userAccount, userPassword, checkPassword, verificationCode, null, request);
        return Result.success(result);
    }

    @SaIgnore
    @PostMapping("/login")
    public BaseResponse<UserLoginVO> userLogin(@RequestBody UserLoginDTO userLoginDTO, HttpServletRequest request) {
        ThrowUtils.throwIf(userLoginDTO == null, ErrorCode.PARAMETER_ERROR);
        String userAccount = userLoginDTO.getUserAccount();
        String userPassword = userLoginDTO.getUserPassword();
        String captcha = userLoginDTO.getCaptcha();
        UserLoginVO userLoginVO = userService.userLogin(userAccount, userPassword, captcha, request);
        return Result.success(userLoginVO);
    }

    @GetMapping("/get/login")
    public BaseResponse<UserLoginVO> getUserLogin(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return Result.success(userService.getLoginUserVO(loginUser));
    }

    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMETER_ERROR);
        boolean result = userService.userLogout(request);
        return Result.success(result);
    }

    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> userAdd(@RequestBody UserVO userVO) {
        ThrowUtils.throwIf(userVO == null, ErrorCode.PARAMETER_ERROR);
        Long userId = userService.addUser(userVO);
        return Result.success(userId);
    }


    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(long id) {
        ThrowUtils.throwIf(id < 0, ErrorCode.PARAMETER_ERROR);
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.CANNOT_FOUND_DATA_ERROR);
        return Result.success(user);
    }

    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(long id) {
        BaseResponse<User> response = getUserById(id);
        User user = response.getData();
        return Result.success(userService.getUserVO(user));
    }

    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR);
        }
        boolean result = userService.removeById(deleteRequest.getId());
        return Result.success(result);
    }

    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateDTO userUpdateDTO) {
        if (userUpdateDTO == null || userUpdateDTO.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR);
        }
        User user = new User();
        BeanUtils.copyProperties(userUpdateDTO, user);
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return Result.success(true);
    }

    @PostMapping("/update/self")
    @AuthCheck
    public BaseResponse<Boolean> updateUserSelf(@RequestBody UserUpdateDTO userUpdateDTO, HttpServletRequest request) {
        if (userUpdateDTO == null) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        // 只能更新自己的信息
        userUpdateDTO.setId(loginUser.getId());
        boolean result = userService.updateUserSelf(userUpdateDTO);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return Result.success(true);
    }

    @PostMapping("/change/password")
    @AuthCheck
    public BaseResponse<Boolean> changePassword(@RequestBody ChangePasswordDTO changePasswordDTO, HttpServletRequest request) {
        if (changePasswordDTO == null) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        boolean result = userService.changePassword(loginUser, changePasswordDTO);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return Result.success(true);
    }

    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> pageUserVOList(@RequestBody UserQueryDTO userQueryDTO) {
        ThrowUtils.throwIf(userQueryDTO ==null, ErrorCode.PARAMETER_ERROR);
        int current = userQueryDTO.getCurrent();
        int pageSize = userQueryDTO.getPageSize();
        Page<User> userPage = userService.page(new Page<>(current, pageSize),
                userService.getQueryWrapper(userQueryDTO));
        Page<UserVO> userVOPage = new Page<>(current, pageSize, userPage.getTotal());
        List<UserVO> userVOList = userService.getUserVOList(userPage.getRecords());
        userVOPage.setRecords(userVOList);
        return Result.success(userVOPage);
    }

    @PostMapping("/list/page/vo/cache")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPageWithCache(@RequestBody UserQueryDTO userQueryDTO,
                                                               HttpServletRequest request) {
        // 参数校验
        ThrowUtils.throwIf(userQueryDTO == null, ErrorCode.PARAMETER_ERROR);
        int size = userQueryDTO.getPageSize();
        ThrowUtils.throwIf(size > 50, ErrorCode.PARAMETER_ERROR, "每页数量不能超过50");

        // 调用服务层方法
        Page<UserVO> result = userService.getUserVOPageWithCache(userQueryDTO, request);
        return Result.success(result);
    }

    @PostMapping("/exchange/vip")
    public BaseResponse<Boolean> exchangeVip(@RequestBody VipExchangeRequest vipExchangeRequest,
                                             HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(vipExchangeRequest == null, ErrorCode.PARAMETER_ERROR);
        String vipCode = vipExchangeRequest.getVipCode();
        User loginUser = userService.getLoginUser(httpServletRequest);
        // 调用 service 层的方法进行会员兑换
        boolean result = userService.exchangeVip(loginUser, vipCode);
        return Result.success(result);
    }
}
