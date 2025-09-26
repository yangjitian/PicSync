package com.yudi.cloud.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yudi.cloud.annotation.AuthCheck;
import com.yudi.cloud.api.aliyunai.AliYunAiApi;
import com.yudi.cloud.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.yudi.cloud.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.yudi.cloud.api.imagesearch.ImageSearchApiFacade;
import com.yudi.cloud.api.imagesearch.model.ImageSearchResult;
import com.yudi.cloud.common.BaseResponse;
import com.yudi.cloud.common.DeleteRequest;
import com.yudi.cloud.common.Result;
import com.yudi.cloud.contstant.UserConstant;
import com.yudi.cloud.exception.BusinessException;
import com.yudi.cloud.exception.ErrorCode;
import com.yudi.cloud.exception.ThrowUtils;
import com.yudi.cloud.manager.auth.SpaceUserAuthManager;
import com.yudi.cloud.manager.auth.annotation.SaSpaceCheckPermission;
import com.yudi.cloud.manager.auth.model.SpaceUserPermissionConstant;
import com.yudi.cloud.manager.recommend.RedisRankServiceImpl;
import com.yudi.cloud.model.dto.picture.*;
import com.yudi.cloud.model.dto.picture.PictureStatsDTO;
import com.yudi.cloud.model.entity.Picture;
import com.yudi.cloud.model.entity.Space;
import com.yudi.cloud.model.entity.User;
import com.yudi.cloud.model.enums.PictureReviewStatusEnum;
import com.yudi.cloud.model.vo.picture.PictureTagCategoryVO;
import com.yudi.cloud.model.vo.picture.PictureVO;
import com.yudi.cloud.service.PictureService;
import com.yudi.cloud.service.SpaceService;
import com.yudi.cloud.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/picture")
public class PictureController {

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private AliYunAiApi aliYunAiApi;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    @Resource
    private RedisRankServiceImpl redisRankServiceImpl;

    @PostMapping("/upload")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
    public BaseResponse<PictureVO> uploadPicture(
            @RequestParam("file") MultipartFile multipartFile,
            PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        return Result.success(pictureVO);
    }

    @PostMapping("/upload/url")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
    public BaseResponse<PictureVO> uploadPictureByUrl(
            @RequestBody PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        String fileUrl = pictureUploadRequest.getFileUrl();
        PictureVO pictureVO = pictureService.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
        return Result.success(pictureVO);
    }

    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadPictureByBatch(@RequestBody PictureUploadByBatchDTO pictureUploadByBatchDTO,
                                                      HttpServletRequest request) {
        ThrowUtils.throwIf(pictureUploadByBatchDTO == null,ErrorCode.PARAMETER_ERROR);
        User loginUser = userService.getLoginUser(request);
        int uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchDTO, loginUser);
        log.info("批量上传服务调用完成，返回数量: {}", uploadCount);
        return Result.success(uploadCount);
    }

    @PostMapping("/delete")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_DELETE)
    public BaseResponse<PictureDeleteResponse> deletePicture(@RequestBody DeleteRequest deleteRequest
            , HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        PictureDeleteResponse response = pictureService.deletePicture(deleteRequest.getId(), loginUser);
        return Result.success(response);
    }

    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateDTO pictureUpdateDTO,
                                               HttpServletRequest request) {
        if (pictureUpdateDTO == null || pictureUpdateDTO.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR);
        }
        
        // 判断是否存在
        long id = pictureUpdateDTO.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.CANNOT_FOUND_DATA_ERROR);
        
        // 创建更新对象，只更新允许编辑的字段，保护作者信息
        Picture picture = new Picture();
        picture.setId(id);
        
        // 只复制允许编辑的字段，避免覆盖作者信息
        if (StrUtil.isNotBlank(pictureUpdateDTO.getName())) {
            picture.setName(pictureUpdateDTO.getName());
        }
        if (StrUtil.isNotBlank(pictureUpdateDTO.getIntroduction())) {
            picture.setIntroduction(pictureUpdateDTO.getIntroduction());
        }
        if (StrUtil.isNotBlank(pictureUpdateDTO.getCategory())) {
            picture.setCategory(pictureUpdateDTO.getCategory());
        } else {
            // 设置默认分类：如果分类为空，默认为"其他"
            picture.setCategory("其他");
        }
        if (CollUtil.isNotEmpty(pictureUpdateDTO.getTags())) {
            picture.setTags(JSONUtil.toJsonStr(pictureUpdateDTO.getTags()));
        }
        
        // 数据校验
        pictureService.validPicture(picture);
        
        // 补充审核参数
        pictureService.fillReviewParams(picture, userService.getLoginUser(request));
        
        // 操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        pictureService.clearPictureFiles(oldPicture);
        return Result.success(true);
    }

    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMETER_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.CANNOT_FOUND_DATA_ERROR);
        // 获取封装类
        return Result.success(picture);
    }

    @GetMapping("/get/vo")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_VIEW)
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMETER_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.CANNOT_FOUND_DATA_ERROR);
        // 获取空间信息用于权限列表
        Long spaceId = picture.getSpaceId();
        Space space = null;
        if (spaceId != null) {
            space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.CANNOT_FOUND_DATA_ERROR,"空间不存在");
        }
        List<String> permissionList = spaceUserAuthManager.getPermissionList(space, userService.getLoginUser(request), picture.getUserId());
        PictureVO pictureVO = pictureService.getPictureVO(picture, request);
        pictureVO.setPermissionList(permissionList);
        // 获取封装类
        return Result.success(pictureVO);
    }

    /**
     * 获取图片详情（支持未登录用户访问，仅显示图片和作者信息）
     */
    @GetMapping("/get/vo/public")
    public BaseResponse<PictureVO> getPictureVOByIdPublic(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMETER_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.CANNOT_FOUND_DATA_ERROR);
        
        // 只允许查看公共图库的图片（spaceId为null）
        if (picture.getSpaceId() != null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "只能查看公共图库的图片");
        }
        
        // 获取封装类（未登录用户版本）
        PictureVO pictureVO = pictureService.getPictureVOForPublic(picture, request);
        return Result.success(pictureVO);
    }

    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryDTO pictureQueryDTO) {
        long current = pictureQueryDTO.getCurrent();
        long size = pictureQueryDTO.getPageSize();
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryDTO));
        return Result.success(picturePage);
    }

    @PostMapping("/list/page/vo")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_VIEW)
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryDTO pictureQueryDTO,
                                                             HttpServletRequest request) {
        long current = pictureQueryDTO.getCurrent();
        long size = pictureQueryDTO.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMETER_ERROR);
        // 处理查询条件
        Long spaceId = pictureQueryDTO.getSpaceId();
        if (spaceId == null) {
            pictureQueryDTO.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            pictureQueryDTO.setNullSpaceId(true);
        }
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryDTO));
        // 获取封装类
        return Result.success(pictureService.getPictureVOPage(picturePage, request));
    }

    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCache(@RequestBody PictureQueryDTO pictureQueryDTO,
                                                             HttpServletRequest request) {
        long size = pictureQueryDTO.getPageSize();
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMETER_ERROR);
        Page<PictureVO> result = pictureService.getPictureVOPageWithCache(pictureQueryDTO, request);

        return Result.success(result);
    }

    /**
     * 获取图片分页列表（公开访问，支持未登录用户）
     * 仅显示公共图库的图片，不显示用户操作状态
     */
    @PostMapping("/list/page/vo/public")
    public BaseResponse<Page<PictureVO>> listPictureVOByPagePublic(@RequestBody PictureQueryDTO pictureQueryDTO,
                                                                   HttpServletRequest request) {
        long current = pictureQueryDTO.getCurrent();
        long size = pictureQueryDTO.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMETER_ERROR);
        
        // 强制设置为公共图库查询（spaceId为null）
        pictureQueryDTO.setNullSpaceId(true);
        pictureQueryDTO.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryDTO));
        
        // 获取封装类（公开版本）
        return Result.success(pictureService.getPictureVOPageForPublic(picturePage, request));
    }

    /**
     * 获取主页推荐图片列表（基于推荐算法）
     */
    @PostMapping("/recommend/homepage")
    public BaseResponse<Page<PictureVO>> getHomepageRecommendPictures(@RequestBody PictureQueryDTO pictureQueryDTO,
                                                                      HttpServletRequest request) {
        long current = pictureQueryDTO.getCurrent();
        long size = pictureQueryDTO.getPageSize();
        
        // 参数校验
        ThrowUtils.throwIf(current <= 0, ErrorCode.PARAMETER_ERROR);
        ThrowUtils.throwIf(size <= 0 || size > 20, ErrorCode.PARAMETER_ERROR);

        try {
            // 使用推荐算法获取所有图片ID列表（用于瀑布流）
            List<Long> allRecommendPictureIds = redisRankServiceImpl.getAllRecommendPictureIds();
            
            if (allRecommendPictureIds.isEmpty()) {
                log.warn("推荐图片列表为空，返回空结果");
                Page<PictureVO> emptyPage = new Page<>(current, size);
                return Result.success(emptyPage);
            }
            
            // 根据推荐ID列表获取图片详情，支持分类筛选
            Page<PictureVO> result = pictureService.getPictureVOPageByIdsWithFilter(allRecommendPictureIds, pictureQueryDTO, current, size, request);
            
            log.info("主页推荐图片获取成功: 请求page={}, 返回{}张图片", current, result.getRecords().size());
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("获取主页推荐图片失败: {}", e.getMessage(), e);
            // 降级处理：返回普通分页查询
            log.info("推荐算法降级，使用普通分页查询");
            return listPictureVOByPage(pictureQueryDTO, request);
        }
    }

    @PostMapping("/edit")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditDTO pictureEditDTO, HttpServletRequest request) {
        if (pictureEditDTO == null || pictureEditDTO.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        pictureService.editPicture(pictureEditDTO, loginUser);
        return Result.success(true);
    }

    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategoryVO> listPictureTagCategory() {
        PictureTagCategoryVO pictureTagCategoryVO = new PictureTagCategoryVO();
        List<String> category = Arrays.asList( "人物","动漫","风景","动物","二次元","建筑","美食","艺术","其他");
        pictureTagCategoryVO.setCategoryList(category);
        return Result.success(pictureTagCategoryVO);
    }

    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> pictureReview(@RequestBody PictureReviewDTO pictureReviewDTO, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureReviewDTO == null, ErrorCode.PARAMETER_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.pictureReview(pictureReviewDTO, loginUser);
        return Result.success(true);
    }


    @PostMapping("/search/picture")
    public BaseResponse<List<ImageSearchResult>> searchPictureByPicture(@RequestBody SearchPictureByPictureRequest searchPictureByPictureRequest) {
        ThrowUtils.throwIf(searchPictureByPictureRequest == null, ErrorCode.PARAMETER_ERROR);
        Long pictureId = searchPictureByPictureRequest.getPictureId();
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMETER_ERROR);
        Picture picture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.CANNOT_FOUND_DATA_ERROR);
        List<ImageSearchResult> resultList = ImageSearchApiFacade.searchImage(picture.getUrl());
        return Result.success(resultList);
    }

    @PostMapping("/search/color")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_VIEW)
    public BaseResponse<List<PictureVO>> searchPictureByColor(@RequestBody SearchPictureByColorRequest searchPictureByColorRequest,
                                                              HttpServletRequest request) {
        ThrowUtils.throwIf(searchPictureByColorRequest == null, ErrorCode.PARAMETER_ERROR);
        Long spaceId = searchPictureByColorRequest.getSpaceId();
        String picColor = searchPictureByColorRequest.getPicColor();
        User loginUser = userService.getLoginUser(request);
        List<PictureVO> pictureVOList = pictureService.searchPictureByColor(spaceId, picColor, loginUser);
        return Result.success(pictureVOList);
    }

    @PostMapping("/search/liked/color")
    public BaseResponse<List<PictureVO>> searchLikedPicturesByColor(@RequestBody SearchPictureByColorRequest searchPictureByColorRequest,
                                                                    HttpServletRequest request) {
        ThrowUtils.throwIf(searchPictureByColorRequest == null, ErrorCode.PARAMETER_ERROR);
        String picColor = searchPictureByColorRequest.getPicColor();
        User loginUser = userService.getLoginUser(request);
        List<PictureVO> pictureVOList = pictureService.searchLikedPicturesByColor(picColor, loginUser);
        return Result.success(pictureVOList);
    }

    @PostMapping("/search/collected/color")
    public BaseResponse<List<PictureVO>> searchCollectedPicturesByColor(@RequestBody SearchPictureByColorRequest searchPictureByColorRequest,
                                                                        HttpServletRequest request) {
        ThrowUtils.throwIf(searchPictureByColorRequest == null, ErrorCode.PARAMETER_ERROR);
        String picColor = searchPictureByColorRequest.getPicColor();
        User loginUser = userService.getLoginUser(request);
        List<PictureVO> pictureVOList = pictureService.searchCollectedPicturesByColor(picColor, loginUser);
        return Result.success(pictureVOList);
    }

    @PostMapping("/edit/batch")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> editPictureByBatch(@RequestBody PictureEditByBatchRequest pictureEditByBatchRequest,
                                                      HttpServletRequest request) {
        ThrowUtils.throwIf(pictureEditByBatchRequest == null, ErrorCode.PARAMETER_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.editPictureByBatch(pictureEditByBatchRequest, loginUser);
        return Result.success(true);
    }

    @PostMapping("/out_painting/create_task")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<CreateOutPaintingTaskResponse> createPictureOutPaintingTask(
            @RequestBody CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest,
            HttpServletRequest request) {
        if (createPictureOutPaintingTaskRequest == null || createPictureOutPaintingTaskRequest.getPictureId() == null) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        CreateOutPaintingTaskResponse response = pictureService.createPictureOutPaintingTask(createPictureOutPaintingTaskRequest, loginUser);
        return Result.success(response);
    }

    @GetMapping("/out_painting/get_task")
    public BaseResponse<GetOutPaintingTaskResponse> getPictureOutPaintingTask(String taskId) {
        ThrowUtils.throwIf(StrUtil.isBlank(taskId), ErrorCode.PARAMETER_ERROR);
        GetOutPaintingTaskResponse task = aliYunAiApi.getOutPaintingTask(taskId);
        return Result.success(task);
    }

    /**
     * 获取用户发布列表（仅在公共图库发布的图片）
     */
    @PostMapping("/list/page/vo/published")
    public BaseResponse<Page<PictureVO>> listPublishedPictureVOByPage(@RequestBody PictureQueryDTO pictureQueryDTO,
                                                                      HttpServletRequest request) {
        long current = pictureQueryDTO.getCurrent();
        long size = pictureQueryDTO.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMETER_ERROR);
        
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        
        // 设置查询条件：只查询当前用户发布的图片，且只在公共图库（spaceId为null）
        pictureQueryDTO.setUserId(loginUser.getId());
        pictureQueryDTO.setNullSpaceId(true);
        
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryDTO));
        
        // 获取封装类
        return Result.success(pictureService.getPictureVOPage(picturePage, request));
    }

    /**
     * 获取用户点赞的图片列表（分页）
     */
    @PostMapping("/action/liked-pictures")
    public BaseResponse<Page<PictureVO>> listLikedPicturesByPage(@RequestBody PictureQueryDTO pictureQueryDTO,
                                                                   HttpServletRequest request) {
        Page<PictureVO> pictureVOPage = pictureService.listLikedPicturesByPage(pictureQueryDTO, request);
        return Result.success(pictureVOPage);
    }

    /**
     * 获取用户发布图片的统计数据
     */
    @GetMapping("/stats/published")
    public BaseResponse<PictureStatsDTO> getPublishedPictureStats(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        
        PictureStatsDTO stats = pictureService.getPublishedPictureStats(loginUser.getId());
        return Result.success(stats);
    }

    /**
     * 获取用户收藏的图片列表（分页）
     */
    @PostMapping("/action/collected-pictures")
    public BaseResponse<Page<PictureVO>> listCollectedPicturesByPage(@RequestBody PictureQueryDTO pictureQueryDTO,
                                                                     HttpServletRequest request) {
        Page<PictureVO> pictureVOPage = pictureService.listCollectedPicturesByPage(pictureQueryDTO, request);
        return Result.success(pictureVOPage);
    }
}
