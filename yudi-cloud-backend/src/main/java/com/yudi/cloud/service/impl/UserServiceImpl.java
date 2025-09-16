package com.yudi.cloud.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yudi.cloud.contstant.UserConstant;
import com.yudi.cloud.exception.BusinessException;
import com.yudi.cloud.exception.ErrorCode;
import com.yudi.cloud.exception.ThrowUtils;
import com.yudi.cloud.manager.auth.StpKit;
import com.yudi.cloud.manager.cache.UserCache;
import com.yudi.cloud.manager.cache.UserEntityCache;
import com.yudi.cloud.model.dto.user.ChangePasswordDTO;
import com.yudi.cloud.model.dto.user.UserQueryDTO;
import com.yudi.cloud.model.dto.user.UserUpdateDTO;
import com.yudi.cloud.model.dto.user.VipCode;
import com.yudi.cloud.model.entity.User;
import com.yudi.cloud.model.enums.UserRoleEnum;
import com.yudi.cloud.model.vo.user.UserLoginVO;
import com.yudi.cloud.model.vo.user.UserVO;
import com.yudi.cloud.service.UserService;
import com.yudi.cloud.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * @author yudi
 * date 2025-07-20
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    @Resource
    private UserCache userCache;

    @Resource
    private UserEntityCache userEntityCache;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String VERIFICATION_CODE_PREFIX = "verification:code:";

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
    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword, String verificationCode, String captcha, HttpServletRequest request) {
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword, verificationCode)) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 6 || checkPassword.length() < 6) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "用户密码过短");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "两次输入的密码不一致");
        }
        
        // 验证邮箱验证码
        String storedCode = stringRedisTemplate.opsForValue().get(VERIFICATION_CODE_PREFIX + userAccount);
        if (StrUtil.isBlank(storedCode) || !storedCode.equals(verificationCode)) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "验证码错误或已过期");
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        long count = this.baseMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "账号重复");
        }

        String entryPassword = getEncryptPassword(userPassword);
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(entryPassword);
        user.setUserName(generateRandomUserName());
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean result = this.save(user);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败");
        }
        
        // 注册成功后删除验证码
        stringRedisTemplate.delete(VERIFICATION_CODE_PREFIX + userAccount);
        
        return user.getId();
    }


    /**
     * 密码加密
     *
     * @param userPassword
     * @return
     */
    @Override
    public String getEncryptPassword(String userPassword) {
        return BCrypt.hashpw(userPassword, BCrypt.gensalt());
    }

    /**
     * 用户登录
     *
     * @param userAccount
     * @param userPassword
     * @param captcha
     * @param request
     * @return
     */
    @Override
    public UserLoginVO userLogin(String userAccount, String userPassword, String captcha, HttpServletRequest request) {
        if (StrUtil.hasBlank(userAccount, userPassword, captcha)) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "用户账号错误");
        }
        if (userPassword.length() < 6) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "用户密码错误");
        }
        
        // 验证图形验证码
        String sessionCaptcha = (String) request.getSession().getAttribute("captcha_code");
        if (StrUtil.isBlank(sessionCaptcha) || !sessionCaptcha.equalsIgnoreCase(captcha)) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "验证码错误");
        }
        User user = lambdaQuery()
                .eq(User::getUserAccount, userAccount)
                .one();
        if (user == null || !BCrypt.checkpw(userPassword, user.getUserPassword())) {
            log.info("user login failed, userAccount does not exist or the userPassword is wrong");
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "用户不存在或者密码错误");
        }
        // 使用Sa-Token进行登录，设置用户信息到会话中
        StpKit.SPACE.login(user.getId());
        StpKit.SPACE.getSession().set(UserConstant.USER_LOGIN_STATUS, user);
        
        // 为了兼容前端现有的Session机制，同时设置Session
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATUS, user);
        return this.getLoginUserVO(user);
    }

    /**
     * 脱敏
     *
     * @param user
     * @return
     */
    @Override
    public UserLoginVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserLoginVO userLoginVO = new UserLoginVO();
        BeanUtil.copyProperties(user, userLoginVO);
        return userLoginVO;
    }

    /**
     * 获取当前用户
     *
     * @param request
     * @return
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATUS);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        
        // 使用缓存避免重复查询数据库
        String cacheKey = "user:login:" + currentUser.getId();
        User cachedUser = userEntityCache.get(cacheKey);
        if (cachedUser != null) {
            return cachedUser;
        }
        
        // 缓存未命中，查询数据库
        currentUser = this.getById(currentUser.getId());
        ThrowUtils.throwIf(currentUser == null, ErrorCode.NOT_LOGIN_ERROR);
        
        // 缓存用户信息，缓存一天
        userEntityCache.set(cacheKey, currentUser, 60 * 60 * 24);
        
        return currentUser;
    }

    /**
     * 用户登出
     *
     * @param request
     * @return
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATUS);
        ThrowUtils.throwIf(userObj == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        
        // 清除Sa-Token会话
        StpKit.SPACE.logout();
        
        // 清除Spring Session
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATUS);
        return true;
    }

    /**
     * 脱敏
     *
     * @param user
     * @return
     */
    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    /**
     * 返回信息列表
     *
     * @param userList
     * @return
     */
    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return Collections.emptyList();
        }
        return userList.stream()
                .map(user -> getUserVO(user))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 增加用户
     *
     * @param userVO
     * @return
     */
    @Override
    public Long addUser(UserVO userVO) {
        final String DEFAULT_PASSWORD = "123456";
        User user = new User();
        BeanUtil.copyProperties(userVO, user);
        user.setUserName(generateRandomUserName());
        String encryptPassword = getEncryptPassword(DEFAULT_PASSWORD);
        user.setUserPassword(encryptPassword);
        boolean success = save(user);
        ThrowUtils.throwIf(!success, ErrorCode.OPERATION_ERROR);
        return user.getId();
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryDTO userQueryDTO) {
        if (userQueryDTO == null) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "请求参数为空");
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        if (ObjUtil.isNotNull(userQueryDTO.getId())){
            queryWrapper.eq("id", userQueryDTO.getId());
        }
        if (StrUtil.isNotBlank(userQueryDTO.getUserName())) {
            queryWrapper.like("userName", userQueryDTO.getUserName());
        }
        if (StrUtil.isNotBlank(userQueryDTO.getUserAccount())) {
            queryWrapper.like("userAccount", userQueryDTO.getUserAccount());
        }
        if (StrUtil.isNotBlank(userQueryDTO.getUserProfile())) {
            queryWrapper.like("userProfile", userQueryDTO.getUserProfile());
        }
        if (StrUtil.isNotBlank(userQueryDTO.getUserRole())) {
            queryWrapper.eq("userRole", userQueryDTO.getUserRole());
        }
        if (StrUtil.isNotBlank(userQueryDTO.getSortField()) && StrUtil.isNotBlank(userQueryDTO.getSortOrder())) {
            boolean isAsc = "ascend".equalsIgnoreCase(userQueryDTO.getSortOrder());
            queryWrapper.orderBy(true, isAsc, userQueryDTO.getSortField());
        }
        return queryWrapper;
//        Long userId = userQueryDTO.getId();
//        String userName = userQueryDTO.getUserName();
//        String userAccount = userQueryDTO.getUserAccount();
//        String userProfile = userQueryDTO.getUserProfile();
//        String userRole = userQueryDTO.getUserRole();
//        String sortField = userQueryDTO.getSortField();
//        String sortOrder = userQueryDTO.getSortOrder();
//        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
//        queryWrapper.eq(ObjUtil.isNotNull(userId),"id",userId);
//        queryWrapper.eq(StrUtil.isNotBlank(userRole), "userRole", userRole);
//        queryWrapper.like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount);
//        queryWrapper.like(StrUtil.isNotBlank(userName), "userName", userName);
//        queryWrapper.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);
//        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
//        return queryWrapper;
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }


    /**
     * 生成随机昵称
     *
     * @return
     */
    private String generateRandomUserName() {
        String baseName = "用户_";
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return baseName + uuid;
    }

    /**
     * 分页查询用户（带缓存）
     */
    @Override
    public Page<UserVO> getUserVOPageWithCache(UserQueryDTO userQueryDTO, HttpServletRequest request) {
        // 创建用于生成缓存键的DTO，排除分页参数
        UserQueryDTO cacheKeyDTO = new UserQueryDTO();
        BeanUtils.copyProperties(userQueryDTO, cacheKeyDTO);
        cacheKeyDTO.setCurrent(null);
        cacheKeyDTO.setPageSize(null);
        
        // 生成缓存键
        String queryCondition = JSONUtil.toJsonStr(cacheKeyDTO);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        String cacheKey = String.format("userQuery:%s", hashKey);

        return userCache.getWithCache(cacheKey, () -> {
            // 数据查询逻辑
            int current = userQueryDTO.getCurrent();
            int size = userQueryDTO.getPageSize();

            Page<User> userPage = this.page(
                    new Page<>(current, size),
                    this.getQueryWrapper(userQueryDTO)
            );
            
            // 转换为UserVO分页对象
            Page<UserVO> userVOPage = new Page<>(current, size, userPage.getTotal());
            List<UserVO> userVOList = this.getUserVOList(userPage.getRecords());
            userVOPage.setRecords(userVOList);
            
            return userVOPage;
        });
    }

    // region ------- 以下代码为用户兑换会员功能 --------

    // 新增依赖注入
    @Autowired
    private ResourceLoader resourceLoader;

    // 文件读写锁（确保并发安全）
    private final ReentrantLock fileLock = new ReentrantLock();

    // VIP 角色常量（根据你的需求自定义）
    private static final String VIP_ROLE = "vip";

    /**
     * 兑换会员
     *
     * @param user
     * @param vipCode
     * @return
     */
    @Override
    public boolean exchangeVip(User user, String vipCode) {
        // 1. 参数校验
        if (user == null || StrUtil.isBlank(vipCode)) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR);
        }
        // 2. 读取并校验兑换码
        VipCode targetCode = validateAndMarkVipCode(vipCode);
        // 3. 更新用户信息
        updateUserVipInfo(user, targetCode.getCode());
        return true;
    }

    /**
     * 校验兑换码并标记为已使用
     */
    private VipCode validateAndMarkVipCode(String vipCode) {
        fileLock.lock(); // 加锁保证文件操作原子性
        try {
            // 读取 JSON 文件
            JSONArray jsonArray = readVipCodeFile();

            // 查找匹配的未使用兑换码
            List<VipCode> codes = JSONUtil.toList(jsonArray, VipCode.class);
            VipCode target = codes.stream()
                    .filter(code -> code.getCode().equals(vipCode) && !code.isHasUsed())
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.PARAMETER_ERROR, "无效的兑换码"));

            // 标记为已使用
            target.setHasUsed(true);

            // 写回文件
            writeVipCodeFile(JSONUtil.parseArray(codes));
            return target;
        } finally {
            fileLock.unlock();
        }
    }

    /**
     * 读取兑换码文件
     */
    private JSONArray readVipCodeFile() {
        try {
            org.springframework.core.io.Resource resource = resourceLoader.getResource("classpath:biz/vipCode.json");
            String content = FileUtil.readString(resource.getFile(), StandardCharsets.UTF_8);
            return JSONUtil.parseArray(content);
        } catch (IOException e) {
            log.error("读取兑换码文件失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "系统繁忙");
        }
    }

    /**
     * 写入兑换码文件
     */
    private void writeVipCodeFile(JSONArray jsonArray) {
        try {
            org.springframework.core.io.Resource resource = resourceLoader.getResource("classpath:biz/vipCode.json");
            FileUtil.writeString(jsonArray.toStringPretty(), resource.getFile(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("更新兑换码文件失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "系统繁忙");
        }
    }

    /**
     * 更新用户会员信息
     */
    private void updateUserVipInfo(User user, String usedVipCode) {
        // 计算过期时间（当前时间 + 1 年）
        Date expireTime = DateUtil.offsetMonth(new Date(), 12); // 计算当前时间加 1 年后的时间

        // 构建更新对象
        User updateUser = new User();
        updateUser.setId(user.getId());
        updateUser.setVipExpireTime(expireTime); // 设置过期时间
        updateUser.setVipCode(usedVipCode);     // 记录使用的兑换码
        updateUser.setUserRole(VIP_ROLE);       // 修改用户角色

        // 执行更新
        boolean updated = this.updateById(updateUser);
        if (!updated) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "开通会员失败，操作数据库失败");
        }
    }

    // endregion ------- 以上代码为用户兑换会员功能 --------

    /**
     * 用户更新自己的信息
     *
     * @param userUpdateDTO
     * @return
     */
    @Override
    public boolean updateUserSelf(UserUpdateDTO userUpdateDTO) {
        if (userUpdateDTO == null || userUpdateDTO.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR);
        }
        
        // 构建更新对象，只更新允许的字段
        User updateUser = new User();
        updateUser.setId(userUpdateDTO.getId());
        
        // 只更新非空字段
        if (StrUtil.isNotBlank(userUpdateDTO.getUserName())) {
            updateUser.setUserName(userUpdateDTO.getUserName());
        }
        if (StrUtil.isNotBlank(userUpdateDTO.getUserProfile())) {
            updateUser.setUserProfile(userUpdateDTO.getUserProfile());
        }
        if (StrUtil.isNotBlank(userUpdateDTO.getUserAvatar())) {
            updateUser.setUserAvatar(userUpdateDTO.getUserAvatar());
        }
        if (userUpdateDTO.getBirthday() != null) {
            updateUser.setBirthday(userUpdateDTO.getBirthday());
        }
        
        // 设置更新时间
        updateUser.setUpdateTime(new Date());
        
        return this.updateById(updateUser);
    }

    /**
     * 用户修改密码
     *
     * @param user
     * @param changePasswordDTO
     * @return
     */
    @Override
    public boolean changePassword(User user, ChangePasswordDTO changePasswordDTO) {
        if (user == null || changePasswordDTO == null) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR);
        }
        
        String currentPassword = changePasswordDTO.getCurrentPassword();
        String newPassword = changePasswordDTO.getNewPassword();
        String confirmPassword = changePasswordDTO.getConfirmPassword();
        
        // 参数校验
        if (StrUtil.hasBlank(currentPassword, newPassword, confirmPassword)) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "密码不能为空");
        }
        
        if (newPassword.length() < 6) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "新密码长度至少6个字符");
        }
        
        if (!newPassword.equals(confirmPassword)) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "两次输入的新密码不一致");
        }
        
        if (newPassword.equals(currentPassword)) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "新密码不能与当前密码相同");
        }
        
        // 验证当前密码
        if (!BCrypt.checkpw(currentPassword, user.getUserPassword())) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "当前密码错误");
        }
        
        // 加密新密码
        String encryptedNewPassword = getEncryptPassword(newPassword);
        
        // 更新密码
        User updateUser = new User();
        updateUser.setId(user.getId());
        updateUser.setUserPassword(encryptedNewPassword);
        updateUser.setUpdateTime(new Date());
        
        return this.updateById(updateUser);
    }
}




