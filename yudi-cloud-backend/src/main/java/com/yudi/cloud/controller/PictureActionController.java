package com.yudi.cloud.controller;

import cn.hutool.core.util.StrUtil;
import com.yudi.cloud.common.BaseResponse;
import com.yudi.cloud.common.Result;
import com.yudi.cloud.exception.BusinessException;
import com.yudi.cloud.exception.ErrorCode;
import com.yudi.cloud.exception.ThrowUtils;
import com.yudi.cloud.mapper.UserPictureActionMapper;
import com.yudi.cloud.model.entity.User;
import com.yudi.cloud.model.vo.picture.UserPictureActionStatus;
import com.yudi.cloud.service.PictureActionService;
import com.yudi.cloud.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Map;

/**
 * 图片行为控制器
 * 
 * @author yudi
 * date 2025-01-27
 */
@RestController
@RequestMapping("/picture/action")
@Slf4j
public class PictureActionController {

    @Resource
    private PictureActionService pictureActionService;

    @Resource
    private UserService userService;

    @Resource
    private UserPictureActionMapper userPictureActionMapper;

    /**
     * 增加浏览量（今天内防重复，从凌晨0点到23:59:59）
     *
     * @param pictureId 图片ID
     * @param request   HTTP请求
     * @return 浏览量操作结果（包含是否成功和最新计数）
     */
    @PostMapping("/view")
    public BaseResponse<Map<String, Object>> addViewCount(@RequestParam Long pictureId, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMETER_ERROR, "图片ID不能为空");
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        Map<String, Object> result = pictureActionService.addViewCount(pictureId, loginUser.getId());
        return Result.success(result);
    }

    /**
     * 点赞/取消点赞
     *
     * @param pictureId 图片ID
     * @param request   HTTP请求
     * @return 点赞结果（包含状态和最新计数）
     */
    @PostMapping("/like")
    public BaseResponse<Map<String, Object>> toggleLike(@RequestParam Long pictureId, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMETER_ERROR, "图片ID不能为空");
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        
        boolean liked = pictureActionService.toggleLike(pictureId, loginUser.getId());
        
        // 返回最新计数和状态
        Map<String, Object> result = new HashMap<>();
        result.put("liked", liked);
        result.put("likeCount", userPictureActionMapper.countLikesByPictureId(pictureId));
        
        return Result.success(result);
    }

    /**
     * 收藏/取消收藏
     *
     * @param pictureId 图片ID
     * @param request   HTTP请求
     * @return 收藏结果（包含状态和最新计数）
     */
    @PostMapping("/collect")
    public BaseResponse<Map<String, Object>> toggleCollect(@RequestParam Long pictureId, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMETER_ERROR, "图片ID不能为空");
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        
        boolean collected = pictureActionService.toggleCollect(pictureId, loginUser.getId());
        
        // 返回最新计数和状态
        Map<String, Object> result = new HashMap<>();
        result.put("collected", collected);
        result.put("collectCount", userPictureActionMapper.countCollectsByPictureId(pictureId));
        
        return Result.success(result);
    }

    /**
     * 增加分享数
     *
     * @param pictureId 图片ID
     * @param request   HTTP请求
     * @return 分享操作结果（包含是否成功和最新计数）
     */
    @PostMapping("/share")
    public BaseResponse<Map<String, Object>> addShareCount(@RequestParam Long pictureId, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMETER_ERROR, "图片ID不能为空");
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        Map<String, Object> result = pictureActionService.addShareCount(pictureId, loginUser.getId());
        return Result.success(result);
    }

    /**
     * 获取用户对图片的行为状态
     *
     * @param pictureId 图片ID
     * @param request   HTTP请求
     * @return 用户行为状态
     */
    @GetMapping("/user-action")
    public BaseResponse<UserPictureActionStatus> getUserActionStatus(
            @RequestParam Long pictureId, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMETER_ERROR, "图片ID不能为空");
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        UserPictureActionStatus status = pictureActionService.getUserActionStatus(pictureId, loginUser.getId());
        return Result.success(status);
    }

    /**
     * 批量获取用户对多张图片的行为状态
     *
     * @param pictureIds 图片ID列表（逗号分隔）
     * @param request    HTTP请求
     * @return 用户行为状态映射
     */
    @GetMapping("/user-actions")
    public BaseResponse<Map<Long, UserPictureActionStatus>> batchGetUserActionStatus(
            @RequestParam String pictureIds, HttpServletRequest request) {
        // 1. 参数校验
        ThrowUtils.throwIf(StrUtil.isBlank(pictureIds), ErrorCode.PARAMETER_ERROR, "图片ID列表不能为空");
        
        // 2. 解析图片ID列表
        List<Long> pictureIdList;
        try {
            pictureIdList = StrUtil.split(pictureIds, ',').stream()
                    .map(String::trim)
                    .map(Long::valueOf)
                    .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "图片ID格式错误");
        }
        
        ThrowUtils.throwIf(pictureIdList.isEmpty(), ErrorCode.PARAMETER_ERROR, "图片ID列表不能为空");
        
        // 3. 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        
        // 4. 执行业务逻辑
        Map<Long, UserPictureActionStatus> result = pictureActionService.batchGetUserActionStatus(pictureIdList, loginUser.getId());
        
        return Result.success(result);
    }
}