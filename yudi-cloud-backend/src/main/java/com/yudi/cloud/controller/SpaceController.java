package com.yudi.cloud.controller;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yudi.cloud.annotation.AuthCheck;
import com.yudi.cloud.common.BaseResponse;
import com.yudi.cloud.common.DeleteRequest;
import com.yudi.cloud.common.Result;
import com.yudi.cloud.contstant.UserConstant;
import com.yudi.cloud.exception.BusinessException;
import com.yudi.cloud.exception.ErrorCode;
import com.yudi.cloud.exception.ThrowUtils;
import com.yudi.cloud.manager.auth.SpaceUserAuthManager;
import com.yudi.cloud.model.dto.space.*;
import com.yudi.cloud.model.entity.Picture;
import com.yudi.cloud.model.entity.Space;
import com.yudi.cloud.model.entity.User;
import com.yudi.cloud.model.enums.SpaceLevelEnum;
import com.yudi.cloud.model.vo.space.SpaceVO;
import com.yudi.cloud.service.PictureService;
import com.yudi.cloud.service.SpaceService;
import com.yudi.cloud.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/space")
public class SpaceController {

    private static final List<SpaceLevel> SPACE_LEVEL_LIST = Collections.unmodifiableList(
            Arrays.stream(SpaceLevelEnum.values())
                    .map(spaceLevelEnum -> new SpaceLevel(
                            spaceLevelEnum.getValue(),
                            spaceLevelEnum.getText(),
                            spaceLevelEnum.getMaxCount(),
                            spaceLevelEnum.getMaxSize()
                    ))
                    .collect(Collectors.toList())
    );

    @Resource
    private SpaceService spaceService;

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;
    @Autowired
    private SpaceUserAuthManager spaceUserAuthManager;

    @PostMapping("/add")
    public BaseResponse<Long> addSpace(@RequestBody SpaceAddDTO spaceAddDTO, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceAddDTO == null, ErrorCode.PARAMETER_ERROR);
        User loginUser = userService.getLoginUser(request);
        long newId = spaceService.addSpace(spaceAddDTO, loginUser);
        return Result.success(newId);
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteSpace(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        Space space = spaceService.validateSpaceAccess(deleteRequest.getId(), request);
        // 1. 查询空间下的所有图片
        List<Picture> pictures = pictureService.lambdaQuery().eq(Picture::getSpaceId, deleteRequest.getId()).list();
        if (CollUtil.isNotEmpty(pictures)) {
            // 2. 删除图片文件
            for (Picture picture : pictures) {
                pictureService.clearPictureFiles(picture);
            }
            // 3. 删除图片记录
            List<Long> pictureIds = pictures.stream().map(Picture::getId).collect(Collectors.toList());
            pictureService.removeByIds(pictureIds);
        }
        boolean result = spaceService.removeById(space.getId());
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return Result.success(true);
    }

    @PostMapping("/edit")
    public BaseResponse<Boolean> editSpace(@RequestBody SpaceEditDTO spaceEditDTO, HttpServletRequest request) {
        if (spaceEditDTO == null || spaceEditDTO.getId() == null || spaceEditDTO.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR);
        }
        Space space = spaceService.validateSpaceAccess(spaceEditDTO.getId(), request);
        space.setSpaceName(spaceEditDTO.getSpaceName());
        space.setEditTime(new Date());
        spaceService.validSpace(space, false);
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return Result.success(true);
    }

    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateSpace(@RequestBody SpaceUpdateDTO spaceUpdateDTO,
                                             HttpServletRequest request) {
        if (spaceUpdateDTO == null || spaceUpdateDTO.getId() == null || spaceUpdateDTO.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR);
        }
        Long spaceId = spaceUpdateDTO.getId();
        Space spaceToUpdate = spaceService.getById(spaceId);
        ThrowUtils.throwIf(spaceToUpdate == null, ErrorCode.CANNOT_FOUND_DATA_ERROR);
        BeanUtils.copyProperties(spaceUpdateDTO, spaceToUpdate);
        spaceToUpdate.setEditTime(new Date());
        spaceService.fillSpaceBySpaceLevel(spaceToUpdate);
        spaceService.validSpace(spaceToUpdate, false);
        boolean result = spaceService.updateById(spaceToUpdate);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return Result.success(true);
    }

    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Space> getSpaceById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMETER_ERROR);
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.CANNOT_FOUND_DATA_ERROR);
        return Result.success(space);
    }

    @GetMapping("/get/vo")
    public BaseResponse<SpaceVO> getSpaceVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMETER_ERROR);
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.CANNOT_FOUND_DATA_ERROR);
        SpaceVO spaceVO = spaceService.getSpaceVO(space, request);
        User loginUser = userService.getLoginUser(request);
        List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
        spaceVO.setPermissionList(permissionList);
        return Result.success(spaceVO);
    }

    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Space>> listSpaceByPage(@RequestBody SpaceQueryDTO spaceQueryDTO, HttpServletRequest request) {
        long current = spaceQueryDTO.getCurrent();
        long pageSize = spaceQueryDTO.getPageSize();
        Page<Space> page = spaceService.page(new Page<>(current, pageSize),
                spaceService.getQueryWrapper(spaceQueryDTO));
        return Result.success(page);
    }


    @PostMapping("/list/page/vo")
    public BaseResponse<Page<SpaceVO>> listSpaceVOByPage(@RequestBody SpaceQueryDTO spaceQueryDTO, HttpServletRequest request) {
        long current = spaceQueryDTO.getCurrent();
        long pageSize = spaceQueryDTO.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMETER_ERROR);
        Page<Space> spacePage = spaceService.page(new Page<>(current, pageSize),
                spaceService.getQueryWrapper(spaceQueryDTO));
        return Result.success(spaceService.getSpaceVOPage(spacePage, request));
    }

    @GetMapping("/list/level")
    public BaseResponse<List<SpaceLevel>> listSpaceLevel() {
        return Result.success(SPACE_LEVEL_LIST);
    }

}
