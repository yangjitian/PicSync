package com.yudi.cloud.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yudi.cloud.exception.BusinessException;
import com.yudi.cloud.exception.ErrorCode;
import com.yudi.cloud.exception.ThrowUtils;
import com.yudi.cloud.model.dto.space.SpaceAddDTO;
import com.yudi.cloud.model.dto.space.SpaceQueryDTO;
import com.yudi.cloud.model.entity.Space;
import com.yudi.cloud.model.entity.SpaceUser;
import com.yudi.cloud.model.entity.User;
import com.yudi.cloud.model.enums.SpaceLevelEnum;
import com.yudi.cloud.model.enums.SpaceRoleEnum;
import com.yudi.cloud.model.enums.SpaceTypeEnum;
import com.yudi.cloud.model.vo.space.SpaceVO;
import com.yudi.cloud.model.vo.user.UserVO;
import com.yudi.cloud.service.SpaceService;
import com.yudi.cloud.mapper.SpaceMapper;
import com.yudi.cloud.service.SpaceUserService;
import com.yudi.cloud.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author yudi
 * date 2025-07-20
 */
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
        implements SpaceService {

    private final Map<Long, Object> userIdLockMap = new ConcurrentHashMap<>();

    @Resource
    private UserService userService;

    @Resource
    private SpaceUserService spaceUserService;

//    @Lazy
//    @Resource
//    private DynamicShardingManager dynamicShardingManager;

    /**
     * 空间基础校验
     *
     * @param space
     * @param add
     */
    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMETER_ERROR, "请求参数不能为空");
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        Integer spaceType = space.getSpaceType();
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getSpaceTypeEnum(spaceType);
        // 创建时
        if (add) {
            ThrowUtils.throwIf(StrUtil.isBlank(spaceName), ErrorCode.PARAMETER_ERROR, "空间名称不能为空");
            ThrowUtils.throwIf(spaceLevel == null, ErrorCode.PARAMETER_ERROR, "空间级别不能为空");
            ThrowUtils.throwIf(spaceType == null, ErrorCode.PARAMETER_ERROR, "空间类别不能为空");
        }
        // 通用校验
        if (StrUtil.isNotBlank(spaceName)) {
            if (spaceName.length() > 20) {
                throw new BusinessException(ErrorCode.PARAMETER_ERROR, "空间名称过长");
            }
            if (!Pattern.matches("^[\\u4e00-\\u9fa5a-zA-Z0-9_-]+$", spaceName)) {
                throw new BusinessException(ErrorCode.PARAMETER_ERROR, "空间名称不合法");
            }
            if (spaceType == null) {
                throw new BusinessException(ErrorCode.PARAMETER_ERROR, "空间类别不能为空");
            }

        }
        if (spaceLevel != null && SpaceLevelEnum.getSpaceLevelEnum(spaceLevel) == null) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "空间级别不合法");
        }
        if (spaceType != null && spaceTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "空间类别不存在");
        }
    }

    /**
     * 参数填充
     * @param space
     */
    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getSpaceLevelEnum(space.getSpaceLevel());
        if (spaceLevelEnum != null) {
            long maxSize = spaceLevelEnum.getMaxSize();
            if (space.getMaxSize() == null) {
                space.setMaxSize(maxSize);
            }
            long maxCount = spaceLevelEnum.getMaxCount();
            if (space.getMaxCount() == null) {
                space.setMaxCount(maxCount);
            }
        }
    }

    /**
     * 获取查询对象
     *
     * @param spaceQueryDTO
     * @return
     */
    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryDTO spaceQueryDTO) {
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        if (spaceQueryDTO == null){
            return queryWrapper;
        }
        // 取值
        Long id = spaceQueryDTO.getId();
        Long userId = spaceQueryDTO.getUserId();
        String spaceName = spaceQueryDTO.getSpaceName();
        Integer spaceLevel = spaceQueryDTO.getSpaceLevel();
        String sortField = spaceQueryDTO.getSortField();
        String sortOrder = spaceQueryDTO.getSortOrder();
        Integer spaceType = spaceQueryDTO.getSpaceType();
        // 拼接查询条件
        queryWrapper.eq(ObjUtil.isNotEmpty(id),"id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceType), "spaceType", spaceType);
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortOrder),sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    /**
     * 创建空间
     *
     * @param spaceAddDTO
     * @param loginUser
     * @return
     */
    @Override
    @Transactional
    public long addSpace(SpaceAddDTO spaceAddDTO, User loginUser) {
        // 1. 填充参数默认值
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddDTO, space);
        if (StrUtil.isBlank(space.getSpaceName())) {
            space.setSpaceName("默认空间");
        }
        if (space.getSpaceLevel() == null) {
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        if (space.getSpaceType() == null){
            space.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());
        }
        this.fillSpaceBySpaceLevel(space);
        // 2. 校验参数
        this.validSpace(space, true);
        // 3. 校验权限，非管理员只能创建普通级别的空间
        Long userId = loginUser.getId();
        space.setUserId(userId);
        if (SpaceLevelEnum.COMMON.getValue() != space.getSpaceLevel() && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限创建指定级别的空间");
        }
        // computeIfAbsent 保证了对于同一个 userId，我们总是能拿到同一个锁对象实例
        Object userLock = userIdLockMap.computeIfAbsent(userId, k -> new Object());
        synchronized (userLock) {
            boolean exists = this.lambdaQuery()
                    .eq(Space::getUserId, userId)
                    .eq(Space::getSpaceType,space.getSpaceType())
                    .exists();
            ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "每个用户每类空间只能创建一个");
            // 5. 不存在则创建
            boolean result = this.save(space);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "保存空间到数据库失败");
            //  创建成功后，如果是团队空间，关联新增团队成员记录
            if (SpaceTypeEnum.TEAM.getValue() == space.getSpaceType()) {
                SpaceUser spaceUser = new SpaceUser();
                spaceUser.setSpaceId(space.getId());
                spaceUser.setUserId(userId);
                spaceUser.setSpaceRole(SpaceRoleEnum.ADMIN.getValue());
                result = spaceUserService.save(spaceUser);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "创建团队成员记录失败");
            }
//            dynamicShardingManager.createSpacePictureTable(space);
            // 统一失败处理，直接返回 id
            return space.getId();
        }
        // 注意：userIdLockMap 中的锁对象会一直存在，如果用户量巨大，可能会有内存泄漏风险。
        // 对于绝大多数系统这不是问题。若用户量上亿，可考虑更复杂的锁机制，如 Guava 的 Striped Lock。
    }

    /**
     * 查询分装类（单条）
     * @param space
     * @return
     */
    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        // 转化为封装类
        SpaceVO spaceVO = SpaceVO.convertToSpaceVO(space);
        Long userId = space.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            spaceVO.setUserVO(userVO);
        }
        return spaceVO;
    }

    /**
     * 查询分装类（分页）
     *
     * @param spacePage
     * @return
     */
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage,HttpServletRequest request) {
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        if (CollUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }
        // 获取存在空间的所有用户id
        Set<Long> userIdSet = spaceList.stream()
                .map(Space::getUserId)
                .collect(Collectors.toSet());
        // 批量查询用户信息,映射至Map
        Map<Long,User> userMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.toMap(
                        User::getId,
                        Function.identity(),
                        (existing,replacement) -> existing
                ));
        // 遍历数据 entity -> vo  转换之后并填充
        List<SpaceVO> spaceVOList = spaceList.stream().map(space -> {
            SpaceVO spaceVO = SpaceVO.convertToSpaceVO(space);
            User user = userMap.get(space.getUserId());
            spaceVO.setUserVO(userService.getUserVO(user));
            return spaceVO;
        }).collect(Collectors.toList());
        // 将转换后的VO列表设置到分页对象中
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }

    @Override
    public Space validateSpaceAccess(Long spaceId, HttpServletRequest request) {
        if (spaceId == null || spaceId <= 0) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        Space space = this.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.CANNOT_FOUND_DATA_ERROR);
        if (!space.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        return space;
    }
}

