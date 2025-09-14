package com.yudi.cloud.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yudi.cloud.model.dto.user.ChangePasswordDTO;
import com.yudi.cloud.model.dto.user.UserQueryDTO;
import com.yudi.cloud.model.dto.user.UserUpdateDTO;
import com.yudi.cloud.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yudi.cloud.model.vo.user.UserLoginVO;
import com.yudi.cloud.model.vo.user.UserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author yudi
 * date 2025-07-20
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param userAccount
     * @param userPassword
     * @param checkPassword
     * @param verificationCode
     * @param captcha
     * @param request
     * @return
     */
    long userRegister(String userAccount, String userPassword,String checkPassword, String verificationCode, String captcha, HttpServletRequest request);

    /**
     * 密码加密
     *
     * @param userPassword
     * @return
     */
    String getEncryptPassword(String userPassword);

    /**
     * 用户登录
     *
     * @param userAccount
     * @param userPassword
     * @param captcha
     * @param request
     * @return
     */
    UserLoginVO userLogin(String userAccount, String userPassword, String captcha, HttpServletRequest request);

    /**
     * 脱敏
     *
     * @param user
     * @return
     */
    UserLoginVO getLoginUserVO(User user);

    /**
     *获取当前用户
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 用户登出
     *
     * @param request
     * @return
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 脱敏
     *
     * @param user
     * @return
     */
    UserVO getUserVO(User user);

    /**
     *获取脱敏后的用户信息列表
     *
     * @param userList
     * @return
     */
    List<UserVO> getUserVOList(List<User> userList);


    /**
     * 增加用户
     *
     * @param userVO
     * @return
     */
    Long addUser(UserVO userVO);

    /**
     *构建查询器
     * @param userQueryDTO
     * @return
     */
    QueryWrapper<User> getQueryWrapper(UserQueryDTO userQueryDTO);

    /**
     * 是否为管理员
     *
     * @param user
     * @return
     */
    boolean isAdmin(User user);

    /**
     * 分页查询用户（带缓存）
     *
     * @param userQueryDTO 查询条件
     * @param request     请求对象
     * @return 分页用户数据
     */
    Page<UserVO> getUserVOPageWithCache(UserQueryDTO userQueryDTO, HttpServletRequest request);

    /**
     * 用户兑换会员（会员码兑换）
     *
     * @param user
     * @param vipCode
     * @return
     */
    boolean exchangeVip(User user,String vipCode);

    /**
     * 用户更新自己的信息
     *
     * @param userUpdateDTO
     * @return
     */
    boolean updateUserSelf(UserUpdateDTO userUpdateDTO);


    /**
     * 用户修改密码
     *
     * @param user
     * @param changePasswordDTO
     * @return
     */
    boolean changePassword(User user, ChangePasswordDTO changePasswordDTO);
}