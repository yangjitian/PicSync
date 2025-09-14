package com.yudi.cloud.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yudi.cloud.exception.BusinessException;
import com.yudi.cloud.exception.ErrorCode;
import com.yudi.cloud.exception.ThrowUtils;
import com.yudi.cloud.model.dto.spaceuser.SpaceUserAddRequest;
import com.yudi.cloud.model.dto.spaceuser.SpaceUserEditRequest;
import com.yudi.cloud.model.dto.spaceuser.SpaceUserQueryRequest;
import com.yudi.cloud.model.entity.Space;
import com.yudi.cloud.model.entity.SpaceUser;
import com.yudi.cloud.model.entity.User;
import com.yudi.cloud.model.enums.SpaceRoleEnum;
import com.yudi.cloud.model.vo.space.SpaceVO;
import com.yudi.cloud.model.vo.spaceuser.SpaceUserVO;
import com.yudi.cloud.model.vo.user.UserVO;
import com.yudi.cloud.service.SpaceService;
import com.yudi.cloud.service.SpaceUserService;
import com.yudi.cloud.mapper.SpaceUserMapper;
import com.yudi.cloud.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author yudi
 * date 2025-07-20
 */
@Service
public class SpaceUserServiceImpl extends ServiceImpl<SpaceUserMapper, SpaceUser>
    implements SpaceUserService{

    @Resource
    private UserService userService;

    @Lazy
    @Resource
    private SpaceService spaceService;

    /**
     * 校验空间成员
     *
     * @param spaceUser
     * @param add       是否为创建时检验
     */
    @Override
    public void validSpaceUser(SpaceUser spaceUser, boolean add) {
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.PARAMETER_ERROR);
        Long userId = spaceUser.getUserId();
        Long spaceId = spaceUser.getSpaceId();
        if (add){
            ThrowUtils.throwIf(ObjectUtil.hasEmpty(spaceId, userId), ErrorCode.PARAMETER_ERROR);
            User user = userService.getById(userId);
            ThrowUtils.throwIf(user == null, ErrorCode.CANNOT_FOUND_DATA_ERROR, "用户不存在");
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.CANNOT_FOUND_DATA_ERROR, "空间不存在");
        }
        String spaceRole = spaceUser.getSpaceRole();
        if (StrUtil.isNotBlank(spaceRole)) {
            SpaceRoleEnum spaceRoleEnum = SpaceRoleEnum.getSpaceRoleEnum(spaceRole);
            ThrowUtils.throwIf(spaceRoleEnum == null, ErrorCode.PARAMETER_ERROR, "空间角色不存在");
        }
    }


    /**
     * 创建空间成员
     *
     * @param spaceUserAddRequest
     * @return
     */
    @Override
    @Transactional
    public long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest) {
        // 1. 校验请求参数
        ThrowUtils.throwIf(spaceUserAddRequest == null, ErrorCode.PARAMETER_ERROR);
        Long spaceId = spaceUserAddRequest.getSpaceId();
        Long userId = spaceUserAddRequest.getUserId();
        ThrowUtils.throwIf(ObjectUtil.hasEmpty(spaceId, userId), ErrorCode.PARAMETER_ERROR);
        // 2. 校验被邀请的用户是否存在
        User invitedUser = userService.getById(userId);
        ThrowUtils.throwIf(invitedUser == null, ErrorCode.CANNOT_FOUND_DATA_ERROR, "被邀请的用户不存在");
        // 3. 检查用户是否已在空间内
        long count = this.lambdaQuery()
                .eq(SpaceUser::getSpaceId, spaceId)
                .eq(SpaceUser::getUserId, userId)
                .count();
        ThrowUtils.throwIf(count > 0, ErrorCode.OPERATION_ERROR, "用户已在空间内");
        // 4. 创建并保存SpaceUser
        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(spaceUserAddRequest, spaceUser);
        if (StrUtil.isBlank(spaceUser.getSpaceRole())) {
            spaceUser.setSpaceRole(SpaceRoleEnum.VIEWER.getValue());
        }
        this.validSpaceUser(spaceUser, true);
        boolean result = this.save(spaceUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "添加用户到空间失败");
        return spaceUser.getId();
    }

    /**
     * 更新空间成员信息
     *
     * @param spaceUserEditRequest
     * @param request
     * @return
     */
    @Override
    public boolean updateSpaceUser(SpaceUserEditRequest spaceUserEditRequest, HttpServletRequest request) {
        // 1. 校验请求参数
        ThrowUtils.throwIf(spaceUserEditRequest == null || spaceUserEditRequest.getId() == null, ErrorCode.PARAMETER_ERROR);
        Long spaceUserId = spaceUserEditRequest.getId();
        String spaceRole = spaceUserEditRequest.getSpaceRole();
        ThrowUtils.throwIf(StrUtil.isBlank(spaceRole), ErrorCode.PARAMETER_ERROR, "空间角色不能为空");

        // 2. 校验待更新的记录是否存在
        SpaceUser oldSpaceUser = this.getById(spaceUserId);
        ThrowUtils.throwIf(oldSpaceUser == null, ErrorCode.CANNOT_FOUND_DATA_ERROR);

        // 3. 校验权限（空间所有者或管理员）
        User loginUser = userService.getLoginUser(request);
        Space space = spaceService.getById(oldSpaceUser.getSpaceId());
        ThrowUtils.throwIf(space == null, ErrorCode.CANNOT_FOUND_DATA_ERROR, "空间不存在");
        if (!space.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权修改空间成员角色");
        }

        // 4. 更新数据
        SpaceUser spaceUser = new SpaceUser();
        spaceUser.setId(spaceUserId);
        spaceUser.setSpaceRole(spaceRole);
        this.validSpaceUser(spaceUser, false);

        return this.updateById(spaceUser);
    }


    /**
     * 获取空间成员包装类（单条）
     *
     * @param spaceUser
     * @param request
     * @return
     */
    @Override
    public SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request) {
        SpaceUserVO spaceUserVO = SpaceUserVO.convertToSpaceVO(spaceUser);
        // 填充用户信息
        Long userId = spaceUser.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            spaceUserVO.setUser(userVO);
        }
        // 填充空间信息
        Long spaceId = spaceUser.getSpaceId();
        if (spaceId != null && spaceId > 0) {
            Space space = spaceService.getById(spaceId);
            SpaceVO spaceVO = spaceService.getSpaceVO(space, request);
            spaceUserVO.setSpace(spaceVO);
        }
        return spaceUserVO;
    }

    /**
     * 获取空间成员包装类（列表）
     *
     * @param spaceUserList
     * @return
     */
    @Override
    public List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList) {
        if (CollUtil.isEmpty(spaceUserList)) {
            return Collections.emptyList();
        }
        // 1. 收集需要关联查询的用户 ID 和空间 ID
        Set<Long> userIdSet = spaceUserList.stream()
                .map(SpaceUser::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> spaceIdSet = spaceUserList.stream()
                .map(SpaceUser::getSpaceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 2. 批量查询用户和空间，使用 toMap 避免重复数据
        Map<Long, User> userMap = userIdSet.isEmpty() ?
                Collections.emptyMap() :
                userService.listByIds(userIdSet).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity(), (v1, v2) -> v1));
        Map<Long, Space> spaceMap = spaceIdSet.isEmpty() ?
                Collections.emptyMap() :
                spaceService.listByIds(spaceIdSet).stream()
                        .collect(Collectors.toMap(Space::getId, Function.identity(), (v1, v2) -> v1));

        // 3. 转换并填充数据
        return spaceUserList.stream().map(spaceUser -> {
            // 转换为 VO 对象（根据实际使用选择合适的方法）
            SpaceUserVO spaceUserVO = SpaceUserVO.convertToSpaceVO(spaceUser);
            // 填充用户信息
            Long userId = spaceUser.getUserId();
            if (userId != null && userMap.containsKey(userId)) {
                spaceUserVO.setUser(userService.getUserVO(userMap.get(userId)));
            }
            // 填充空间信息
            Long spaceId = spaceUser.getSpaceId();
            if (spaceId != null && spaceMap.containsKey(spaceId)) {
                Space space = spaceMap.get(spaceId);
                spaceUserVO.setSpace(SpaceVO.convertToSpaceVO(space));
            }
            return spaceUserVO;
        }).collect(Collectors.toList());
    }

    /**
     * 获取查询对象
     *
     * @param spaceUserQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest) {
        QueryWrapper<SpaceUser> queryWrapper = new QueryWrapper<>();
        if (spaceUserQueryRequest == null) {
            return queryWrapper;
        }
        Long id = spaceUserQueryRequest.getId();
        Long spaceId = spaceUserQueryRequest.getSpaceId();
        Long userId = spaceUserQueryRequest.getUserId();
        String spaceRole = spaceUserQueryRequest.getSpaceRole();

        queryWrapper.eq(ObjectUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.eq(ObjectUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(StrUtil.isNotBlank(spaceRole), "spaceRole", spaceRole);
        return queryWrapper;
    }
}