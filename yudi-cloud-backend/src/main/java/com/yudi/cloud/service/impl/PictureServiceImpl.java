package com.yudi.cloud.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.hash.Hash;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;

import java.util.ArrayList;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yudi.cloud.api.aliyunai.AliYunAiApi;
import com.yudi.cloud.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.yudi.cloud.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.yudi.cloud.config.CosClientConfig;
import com.yudi.cloud.exception.BusinessException;
import com.yudi.cloud.exception.ErrorCode;
import com.yudi.cloud.exception.ThrowUtils;
import com.yudi.cloud.manager.CosManager;
import com.yudi.cloud.manager.cache.PictureCache;
import com.yudi.cloud.manager.recommend.RealtimeRecommendationServiceImpl;
import com.yudi.cloud.manager.recommend.RedisRankServiceImpl;
import com.yudi.cloud.manager.upload.FilePictureUpload;
import com.yudi.cloud.manager.upload.PictureUploadTemplate;
import com.yudi.cloud.manager.upload.UrlPictureUpload;
import com.yudi.cloud.mapper.PictureMapper;
import com.yudi.cloud.mapper.UserPictureActionMapper;
import com.yudi.cloud.model.dto.picture.*;
import com.yudi.cloud.model.dto.picture.PictureStatsDTO;
import com.yudi.cloud.model.entity.Picture;
import com.yudi.cloud.model.entity.Space;
import com.yudi.cloud.model.entity.User;
import com.yudi.cloud.model.entity.UserPictureAction;

import java.util.Collections;
import java.util.Date;
import java.util.Map;

import com.yudi.cloud.model.enums.PictureReviewStatusEnum;
import com.yudi.cloud.model.enums.SpaceTypeEnum;
import com.yudi.cloud.model.enums.UserRoleEnum;
import com.yudi.cloud.model.vo.picture.PictureVO;
import com.yudi.cloud.model.vo.user.UserVO;
import com.yudi.cloud.service.PictureService;
import com.yudi.cloud.service.SpaceService;
import com.yudi.cloud.service.UserService;
import com.yudi.cloud.utils.ColorSimilarUtils;
import com.yudi.cloud.utils.TimeUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author yudi
 * date 2025-07-20
 */
@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    @Resource
    private PictureCache pictureCache;

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private UserPictureActionMapper userPictureActionMapper;

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Resource
    private CosManager cosManager;

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private AliYunAiApi aliYunAiApi;

    @Resource
    private RealtimeRecommendationServiceImpl realtimeRecommendationServiceImpl;

    @Resource
    private RedisRankServiceImpl redisRankServiceImpl;

    // 用户信息缓存（并发安全的内存缓存，避免重复查询）
    private final Map<Long, User> userCache = new ConcurrentHashMap<>();
    private long lastCacheClearTime = System.currentTimeMillis();
    private final int MAX_CACHE_SIZE = 1000;

    // 缓存统计
    private final AtomicInteger cacheHitCount = new AtomicInteger(0);
    private final AtomicInteger cacheMissCount = new AtomicInteger(0);

    // 用户操作状态缓存（避免重复查询用户对图片的操作）
    private final Map<String, Set<Long>> userActionCache = new ConcurrentHashMap<>();
    private long lastActionCacheClearTime = System.currentTimeMillis();

    // Space信息缓存（避免重复查询Space信息）
    private final Map<Long, Space> spaceCache = new ConcurrentHashMap<>();
    private long lastSpaceCacheClearTime = System.currentTimeMillis();

    // 查询去重机制（避免短时间内重复查询相同数据）
    private final Map<String, Long> queryTimestampCache = new ConcurrentHashMap<>();
    
    // 批量查询缓存（避免重复的批量查询）
    private final Map<String, Object> batchQueryCache = new ConcurrentHashMap<>();
    private long lastBatchCacheClearTime = System.currentTimeMillis();

    /**
     * 校验图片
     *
     * @param picture
     */
    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMETER_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMETER_ERROR, "id 不能为空");
        // 如果传递了 url，才校验
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMETER_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMETER_ERROR, "简介过长");
        }
    }

    /**
     * 上传图片
     *
     * @param inputSource          原图文件
     * @param pictureUploadRequest 请求参数
     * @param loginUser            当前用户
     * @return
     */
    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {

        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "no permission");
        ThrowUtils.throwIf(pictureUploadRequest == null, ErrorCode.PARAMETER_ERROR, "请求参数不能为空");

        Long spaceId = pictureUploadRequest.getSpaceId();
        // 1. 空间存在性和权限校验 (对于新建和更新都需要)
        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.CANNOT_FOUND_DATA_ERROR, "空间不存在");
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无空间权限");
            }
            // 校验额度
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
            }
            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间大小不足");
            }
        }

        Long pictureId = pictureUploadRequest.getId();
        boolean isUpdate = (pictureId != null);
        // 2. 更新操作的校验
        if (isUpdate) {
            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.CANNOT_FOUND_DATA_ERROR, "picture not exists");
            // 权限校验
            if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
            // 空间校验：如果更新时没传 spaceId，则复用原有 spaceId
            if (spaceId == null) {
                spaceId = oldPicture.getSpaceId();
            } else {
                // 如果传了 spaceId，必须和原图片空间一致
                if (ObjUtil.notEqual(spaceId, oldPicture.getSpaceId())) {
                    throw new BusinessException(ErrorCode.PARAMETER_ERROR, "与原空间不一致");
                }
            }
        }

        // 3. 上传文件
        String uploadPathPrefix = spaceId == null ?
                String.format("public/%s", loginUser.getId()) : String.format("space/%s", spaceId);
        // 根据 inputSource 的类型区分上传方式
        PictureUploadTemplate pictureUploadTemplate = (inputSource instanceof String) ? urlPictureUpload : filePictureUpload;
        PictureUploadDTO pictureUploadResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);

        // 4. 构建 Picture 对象
        Picture picture = new Picture();
        picture.setSpaceId(spaceId);
        picture.setUrl(pictureUploadResult.getUrl());
        picture.setThumbnailUrl(pictureUploadResult.getThumbnailUrl());
        picture.setWebpUrl(pictureUploadResult.getWebpUrl());
        String picName = pictureUploadResult.getPicName();
        if (StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        picture.setName(picName);
        picture.setPicSize(pictureUploadResult.getPicSize());
        picture.setPicWidth(pictureUploadResult.getPicWidth());
        picture.setPicHeight(pictureUploadResult.getPicHeight());
        picture.setPicScale(pictureUploadResult.getPicScale());
        picture.setPicFormat(pictureUploadResult.getPicFormat());
        picture.setPicColor(pictureUploadResult.getPicColor());
        picture.setUserId(loginUser.getId());
        picture.setCategory("其他");
        this.fillReviewParams(picture, loginUser);
        if (isUpdate) {
            picture.setId(pictureId);
            picture.setEditTime(TimeUtils.getCurrentBeijingTime());
        }

        // 5. 数据库事务操作
        Long picSize = picture.getPicSize();
        Long finalSpaceId = spaceId;

        transactionTemplate.execute(status -> {
            // 5.1 保存或更新图片信息
            boolean result = this.saveOrUpdate(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片信息保存失败");

            // 5.2 如果关联了空间，则原子地更新空间使用额度
            if (finalSpaceId != null) {
                // 使用 WHERE 条件在更新时检查容量，确保操作的原子性，防止并发问题
                UpdateWrapper<Space> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("id", finalSpaceId)
                        .apply("totalCount < maxCount")
                        .apply("totalSize + {0} <= maxSize", picSize)
                        .setSql("totalSize = totalSize + {0}", picSize)
                        .setSql("totalCount = totalCount + 1");

                boolean updateSuccess = spaceService.update(updateWrapper);
                // 如果更新失败，说明空间容量不足，抛出异常以回滚事务
                ThrowUtils.throwIf(!updateSuccess, ErrorCode.OPERATION_ERROR, "空间容量不足");
            }
            return true;
        });
        // 6. 异步计算推荐分数（仅对新上传且审核通过的图片）
        if (!isUpdate && picture.getId() != null && PictureReviewStatusEnum.PASS.getValue() == picture.getReviewStatus()) {
            try {
                realtimeRecommendationServiceImpl.calculateAndUpdateRecommendScore(picture.getId());
            } catch (Exception e) {
                // 推荐分数计算失败不应该影响上传流程
                log.error("图片{}实时推荐分数计算失败: {}", picture.getId(), e.getMessage(), e);
            }
        }

        return PictureVO.convertToPictureVO(picture);
    }

    /**
     * 删除图片
     *
     * @param pictureId
     * @param loginUser
     */
    @Override
    public PictureDeleteResponse deletePicture(Long pictureId, User loginUser) {
        // 真实性校验
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMETER_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.CANNOT_FOUND_DATA_ERROR);
        // 校验权限
        checkPictureAuth(loginUser, oldPicture);
        // 开启事务，包含数据库操作和文件删除
        transactionTemplate.execute(status -> {
            // 先清空url和webpUrl字段，然后进行逻辑删除
            boolean updateResult = this.lambdaUpdate()
                    .eq(Picture::getId, pictureId)
                    .set(Picture::getUrl, null)
                    .set(Picture::getWebpUrl, null)
                    .set(Picture::getRecommendScore, null)
                    .set(Picture::getScoreUpdatedAt, TimeUtils.getCurrentBeijingTime())
                    .update();
            ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "清空URL字段失败");
            
            // 执行逻辑删除
            boolean result = this.removeById(pictureId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除失败");

            // 只有属于特定空间的图片才需要更新空间额度
            // 公共图库的图片（spaceId为null）不需要更新空间额度
            if (oldPicture.getSpaceId() != null) {
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, oldPicture.getSpaceId())
                        .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                        .setSql("totalCount = totalCount - 1")
                        .update();
                // 如果更新失败，说明空间容量不足，抛出异常以回滚事务
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }

            // 在事务中同步清理文件，确保数据一致性
            try {
                this.clearPictureFiles(oldPicture);
            } catch (Exception e) {
                log.error("文件删除失败，回滚事务: {}", e.getMessage(), e);
                throw new RuntimeException("文件删除失败", e);
            }

            return true;
        });

        // 清理相关缓存
        this.clearPictureCaches(pictureId);

        // 构建删除响应
        PictureDeleteResponse response = new PictureDeleteResponse();
        response.setSuccess(true);
        response.setSpaceId(oldPicture.getSpaceId());
        response.setIsPublicPicture(oldPicture.getSpaceId() == null);

        return response;
    }

    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryDTO pictureQueryDTO) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryDTO == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = pictureQueryDTO.getId();
        String name = pictureQueryDTO.getName();
        String introduction = pictureQueryDTO.getIntroduction();
        String category = pictureQueryDTO.getCategory();
        List<String> tags = pictureQueryDTO.getTags();
        Long picSize = pictureQueryDTO.getPicSize();
        Integer picWidth = pictureQueryDTO.getPicWidth();
        Integer picHeight = pictureQueryDTO.getPicHeight();
        Double picScale = pictureQueryDTO.getPicScale();
        String picFormat = pictureQueryDTO.getPicFormat();
        String searchText = pictureQueryDTO.getSearchText();
        Long userId = pictureQueryDTO.getUserId();
        Long spaceId = pictureQueryDTO.getSpaceId();
        boolean nullSpaceId = pictureQueryDTO.isNullSpaceId();
        Integer reviewStatus = pictureQueryDTO.getReviewStatus();
        String reviewMessage = pictureQueryDTO.getReviewMessage();
        Long reviewerId = pictureQueryDTO.getReviewerId();
        String sortField = pictureQueryDTO.getSortField();
        String sortOrder = pictureQueryDTO.getSortOrder();
        Date startEditTime = pictureQueryDTO.getStartEditTime();
        Date endEditTime = pictureQueryDTO.getEndEditTime();
        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId");
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        // >= startEditTime
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        // < endEditTime
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);
        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    /**
     * 获取图片包装类单条
     *
     * @param picture
     * @param request
     * @return
     */
    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.convertToPictureVO(picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUserVO(userVO);
        }

        // 设置分享量显示控制
        setShareCountDisplayControl(pictureVO, request);

        // 查询当前用户对该图片的操作状态（点赞、收藏）
        User loginUser = userService.getLoginUserSafely(request);
        if (loginUser != null && picture.getId() != null) {
            List<Long> pictureIdList = Collections.singletonList(picture.getId());
            Map<String, Set<Long>> actionMap = getUserActionStatusBatch(loginUser.getId(), pictureIdList);
            Set<Long> likedPictureIdSet = actionMap.get("LIKE");
            Set<Long> collectedPictureIdSet = actionMap.get("COLLECT");
            
            // 设置当前用户的操作状态
            pictureVO.setLiked(likedPictureIdSet.contains(picture.getId()));
            pictureVO.setCollected(collectedPictureIdSet.contains(picture.getId()));
        } else {
            // 未登录用户不显示操作状态
            pictureVO.setLiked(false);
            pictureVO.setCollected(false);
        }

        return pictureVO;
    }

    /**
     * 将 Picture 分页转换为 PictureVO 分页，并填充关联的用户信息
     * 使用高效的一次性数据加载和单次遍历转换策略
     *
     * @param picturePage 原始 Picture 分页数据
     * @param request     HTTP 请求对象，用于可能的用户信息获取
     * @return 转换后的 PictureVO 分页数据
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        // 获取原始记录列表
        List<Picture> pictureList = picturePage.getRecords();
        // 创建空的VO分页对象，保留原始分页信息（当前页、每页大小、总记录数）
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        // 如果源数据为空，直接返回空分页对象
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // 1.提取所有需要查询的用户ID（使用Set去重）
        Set<Long> userIdSet = pictureList.stream()
                .map(Picture::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // 提取所有需查询的图片ID
        List<Long> pictureIdList = pictureList.stream()
                .map(Picture::getId)
                .collect(Collectors.toList());

        // 2.批量查询用户信息。使用toMap而不是groupingBy，因为每个ID对应唯一用户，避免不必要的List结构
        Map<Long, User> userMap = userIdSet.isEmpty() ? Collections.emptyMap() : userService.listByIds(userIdSet).stream()
                .collect(Collectors.toMap(User::getId,
                        Function.identity(),
                        (existing,replacement) -> existing)
                );

        // 3. 查询当前用户的操作状态（优化：一次查询获取所有操作）
        User loginUser = userService.getLoginUser(request);
        Map<Long, Set<String>> userActionMap = new HashMap<>();
        if (loginUser != null && CollUtil.isNotEmpty(pictureIdList)) {
            // 查询点赞记录
            List<UserPictureAction> userPictureActions = userPictureActionMapper.selectList(
                    new QueryWrapper<UserPictureAction>()
                            .eq("user_id", loginUser.getId())
                            .in("picture_id", pictureIdList)
                            .eq("action_type", Arrays.asList("LIKE", "COLLECT"))
                            .eq("status", 1)
            );
            userActionMap = userPictureActions.stream().collect(Collectors.groupingBy(UserPictureAction::getPictureId,
                    Collectors.mapping(UserPictureAction::getActionType, Collectors.toSet())));
        }

        // 4. 转换为VO
        final Map<Long, Set<String>> finalUserActionMap = userActionMap;
        List<PictureVO> pictureVOList = pictureList.stream().map(picture -> {
            // entity -> vo
            PictureVO pictureVO = PictureVO.convertToPictureVO(picture);
            // 设置用户信息
            User user = userMap.get(picture.getUserId());
            if (user != null) {
                pictureVO.setUserVO(userService.getUserVO(user));
            }

            // 设置分享量显示控制
            setShareCountDisplayControl(pictureVO, request);

            // 设置当前用户的操作状态
            Set<String> action = finalUserActionMap.getOrDefault(picture.getId(), Collections.emptySet());
            pictureVO.setLiked(action.contains("LIKE"));
            pictureVO.setCollected(action.contains("COLLECT"));

            return pictureVO;
        }).collect(Collectors.toList());
        // 将转换后的VO列表设置到分页对象中
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    /**
     * 编辑图片
     *
     * @param pictureEditDTO
     * @param loginUser
     */
    @Override
    public void editPicture(PictureEditDTO pictureEditDTO, User loginUser) {
        // 判断是否存在
        long id = pictureEditDTO.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.CANNOT_FOUND_DATA_ERROR);
        
        // 校验权限
//        checkPictureAuth(loginUser, oldPicture);
        
        // 创建更新对象，只更新允许编辑的字段
        Picture picture = new Picture();
        picture.setId(id);
        
        // 只复制允许编辑的字段，避免覆盖作者信息
        if (StrUtil.isNotBlank(pictureEditDTO.getName())) {
            picture.setName(pictureEditDTO.getName());
        }
        if (StrUtil.isNotBlank(pictureEditDTO.getIntroduction())) {
            picture.setIntroduction(pictureEditDTO.getIntroduction());
        }
        if (StrUtil.isNotBlank(pictureEditDTO.getCategory())) {
            picture.setCategory(pictureEditDTO.getCategory());
        } else {
            // 设置默认分类：如果分类为空，默认为"其他"
            picture.setCategory("其他");
        }
        if (CollUtil.isNotEmpty(pictureEditDTO.getTags())) {
            picture.setTags(JSONUtil.toJsonStr(pictureEditDTO.getTags()));
        }
        
        // 设置编辑时间
        picture.setEditTime(TimeUtils.getCurrentBeijingTime());
        
        // 数据校验
        this.validPicture(picture);
        
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        
        // 操作数据库
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    @Override
    public void pictureReview(PictureReviewDTO pictureReviewDTO, User loginUser) {
        // 1.校验参数
        ThrowUtils.throwIf(pictureReviewDTO == null, ErrorCode.PARAMETER_ERROR);
        Long id = pictureReviewDTO.getId();
        Integer reviewStatus = pictureReviewDTO.getReviewStatus();
        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        String reviewMessage = pictureReviewDTO.getReviewMessage();
        if (id == null || reviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR);
        }
        // 2.判断图片是否存在
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.CANNOT_FOUND_DATA_ERROR);
        // 3.校验审核状态是否重复
        if (oldPicture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "已是该状态");
        }
        // 4.数据库操作 - 只更新审核相关字段，保护作者信息
        Picture updatePicture = new Picture();
        updatePicture.setId(id);
        updatePicture.setReviewStatus(reviewStatus);
        if (StrUtil.isNotBlank(reviewMessage)) {
            updatePicture.setReviewMessage(reviewMessage);
        }
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        boolean result = this.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        // 5. 如果审核通过，异步计算推荐分数
        if (PictureReviewStatusEnum.PASS.equals(reviewStatusEnum)) {
            try {
                realtimeRecommendationServiceImpl.calculateAndUpdateRecommendScore(id);
            } catch (Exception e) {
                // 推荐分数计算失败不应该影响审核流程
                log.error("图片{}审核通过后推荐分数计算失败: {}", id, e.getMessage(), e);
            }
        }
    }

    /**
     * 填充审核参数
     *
     * @param picture
     * @param loginUser
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewMessage("管理员编辑自动过审");
            picture.setReviewerId(loginUser.getId());
            picture.setReviewTime(new Date());
            // 注意：这里不设置 userId，保持原有作者信息
        } else {
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }

    /**
     * 批量抓取上传图片
     *
     * @return 上传成功数量
     */
    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchDTO pictureUploadByBatchDTO, User loginUser) {
        // 1.校验参数
        String searchText = pictureUploadByBatchDTO.getSearchText();
        Integer count = pictureUploadByBatchDTO.getCount();
        log.info("searchText: '{}', count: {}", searchText, count);
        ThrowUtils.throwIf(StrUtil.isBlank(searchText), ErrorCode.PARAMETER_ERROR, "searchText cannot be empty");
        ThrowUtils.throwIf(count > 20, ErrorCode.PARAMETER_ERROR, "max cannot larger than 20");
        // 默认前缀名为搜索关键词
        String namePrefix = pictureUploadByBatchDTO.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }
        log.info("namePrefix: '{}'", namePrefix);
        // 抓取
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        log.info("开始抓取图片，URL: {}", fetchUrl);
        Document document;
        try {
            log.info("正在连接Bing图片搜索...");
            document = Jsoup.connect(fetchUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .timeout(30000)
                    .get();
            log.info("抓取成功，文档大小: {} 字符", document.html().length());
            log.info("页面标题: {}", document.title());
        } catch (IOException e) {
            log.error("抓取失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "网络请求失败: " + e.getMessage());
        }

        // 解析内容
        log.info("开始解析页面内容...");
        Element div = document.getElementsByClass("dgControl").first();
        log.info("查找dgControl元素: {}", div != null ? "找到" : "未找到");

        if (ObjUtil.isEmpty(div)) {
            log.error("页面结构可能已改变，尝试查找其他元素...");
            // 尝试查找其他可能的容器元素
            Elements possibleContainers = document.select("div, ul, ol");
            log.info("找到可能的容器元素数量: {}", possibleContainers.size());

            String pageHtml = document.html();
            log.info("页面HTML片段 (前1000字符): {}",
                    pageHtml.length() > 1000 ? pageHtml.substring(0, 1000) + "..." : pageHtml);

            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未找到目标元素dgControl，页面结构可能已改变");
        }

        Elements imgElementList = div.select("img.ming");
        log.info("使用img.ming选择器找到图片元素数量: {}", imgElementList.size());

        if (imgElementList.isEmpty()) {
            log.warn("未找到img.ming元素，尝试其他选择器...");
            // 尝试其他可能的选择器
            imgElementList = div.select("img");
            log.info("使用通用img选择器找到元素数量: {}", imgElementList.size());

            if (imgElementList.isEmpty()) {
                log.error("div内未找到任何img元素");
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "页面中未找到任何图片元素");
            }
        }
        // 遍历 上传
        int uploadCount = 0;
        // 收集成功上传的图片ID,用于后面计算推荐分数
        List<Long> uploadedPictureIds = new ArrayList<>();
        log.info("开始遍历图片元素，总数: {}", imgElementList.size());

        for (int i = 0; i < imgElementList.size(); i++) {
            Element imgElement = imgElementList.get(i);
            log.info("=== 处理第{}个图片元素 ===", i + 1);

            String fileUrl = imgElement.attr("src");
            log.info("图片URL: '{}'", fileUrl);

            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过");
                continue;
            }

            // 处理图片的地址，防止转义或者和对象存储冲突的问题
            // codefather.cn?yupi=dog，应该只保留 codefather.cn
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
                log.info("处理后的URL: '{}'", fileUrl);
            }

            // 上传图片
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
            pictureUploadRequest.setFileUrl(fileUrl);
            log.info("准备上传图片: {}", pictureUploadRequest.getPicName());

            try {
                log.info("开始调用uploadPicture方法...");
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功, id = {}", pictureVO.getId());
                uploadCount++;

                // 收集成功上传的图片ID，用于批量计算推荐分数
                if (pictureVO.getId() != null) {
                    uploadedPictureIds.add(pictureVO.getId());
                }

                log.info("当前成功数量: {}/{}", uploadCount, count);
            } catch (BusinessException e) {
                log.warn("图片上传失败: {}", e.getMessage());
                continue;
            } catch (Exception e) {
                log.error("图片上传出现未知错误", e);
                continue;
            }

            if (uploadCount >= count) {
                log.info("已达到目标数量: {}, 停止处理", count);
                break;
            }
        }

        // 批量计算推荐分数（仅对成功上传的图片）
        if (!uploadedPictureIds.isEmpty()) {
            try {
                log.info("开始批量计算{}张图片的推荐分数", uploadedPictureIds.size());
                realtimeRecommendationServiceImpl.batchCalculateAndUpdateRecommendScores(uploadedPictureIds);
                log.info("批量推荐分数计算任务已提交");
            } catch (Exception e) {
                // 推荐分数计算失败不应该影响批量上传流程
                log.error("批量推荐分数计算失败: {}", e.getMessage(), e);
            }
        }

        log.info("=== 批量上传完成，成功数量: {} ===", uploadCount);
        return uploadCount;
    }

    /**
     * 分页查询图片（带缓存）
     */
    @Override
    public Page<PictureVO> getPictureVOPageWithCache(PictureQueryDTO pictureQueryDTO, HttpServletRequest request) {
        // 设置审核状态
        pictureQueryDTO.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        PictureQueryDTO cacheKeyDTO = new PictureQueryDTO();
        BeanUtils.copyProperties(pictureQueryDTO, cacheKeyDTO);
        cacheKeyDTO.setCurrent(null);
        cacheKeyDTO.setPageSize(null);
        String queryCondition = JSONUtil.toJsonStr(cacheKeyDTO);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        String cacheKey = String.format("yudiPicSync:%s", hashKey);

        return pictureCache.getWithCache(cacheKey, () -> {
            // 数据查询逻辑
            long current = pictureQueryDTO.getCurrent();
            long size = pictureQueryDTO.getPageSize();

            Page<Picture> picturePage = this.page(
                    new Page<>(current, size),
                    this.getQueryWrapper(pictureQueryDTO)
            );
            return this.getPictureVOPage(picturePage, request);
        });
    }

    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        Long loginUserId = loginUser.getId();

        if (spaceId == null) {
            // 公共图库：普通用户只能删除自己上传的图片，管理员可以删除所有图片
            if (!picture.getUserId().equals(loginUserId) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "您只能删除自己上传的图片");
            }
        } else {
            // 有空间ID，需要查询空间信息
            Space space = spaceService.getById(spaceId);
            if (space == null) {
                throw new BusinessException(ErrorCode.CANNOT_FOUND_DATA_ERROR, "空间不存在");
            }

            if (space.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()) {
                // 私有空间：只有空间创建者才能删除图片
                if (!space.getUserId().equals(loginUserId)) {
                    throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "只有空间创建者才能删除此空间的图片");
                }
            } else if (space.getSpaceType() == SpaceTypeEnum.TEAM.getValue()) {
                // 团队空间：通过 @SaSpaceCheckPermission 注解控制权限，这里不需要额外检查
                // 因为团队空间的权限已经在 StpInterfaceImpl 中通过角色权限控制
                log.debug("团队空间删除权限由角色权限控制，当前用户: {}, 空间ID: {}", loginUserId, spaceId);
            }
        }
    }

    /**
     * 【通用】清理图片文件（COS：原图 + WebP，保留缩略图）
     * 适用于：更新图片前清理旧图、删除图片时清理文件
     *
     * @param picture 图片实体（必须包含 url）
     */
    @Override
    public void clearPictureFiles(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMETER_ERROR, "图片不存在");
        // 删除原图及其WebP版本（保留缩略图）
        if (StrUtil.isNotBlank(picture.getUrl())) {
            String cosKey = extractCosKeyFromUrl(picture.getUrl());
            // 先尝试普通删除，如果WebP文件删除失败，使用强制删除
            try {
                cosManager.deletePictureObject(cosKey);
            } catch (Exception e) {
                log.warn("正常删除失败，尝试强制删除: {}", e.getMessage());
                cosManager.forceDeletePictureObject(cosKey);
            }
        }
    }

    /**
     * 根据颜色搜索图片
     *
     * @param spaceId
     * @param picColor
     * @param loginUser
     * @return
     */
    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        ThrowUtils.throwIf(spaceId == null || StrUtil.isBlank(picColor) || loginUser == null, ErrorCode.PARAMETER_ERROR);
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.CANNOT_FOUND_DATA_ERROR, "空间不存在");
        if (!loginUser.getId().equals(space.getUserId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无空间权限");
        }
        // 3. 查询空间下所有图片（带主色调）
        List<Picture> pictureList = this.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .list();
        if (CollUtil.isEmpty(pictureList)) {
            return Collections.emptyList();
        }

        // 将用户输入的颜色字符串（如 "#FF0000"）解析为 Color 对象
        Color targetColor = Color.decode(picColor);

        return pictureList.stream()
                // 过滤掉没有颜色信息的图片，减少后续计算量
                .filter(picture -> StrUtil.isNotBlank(picture.getPicColor()))
                // 根据颜色相似度进行排序
                .sorted(Comparator.comparingDouble(picture -> {
                    try {
                        Color pictureColor = Color.decode(picture.getPicColor());
                        // 计算当前图片颜色与目标颜色的相似度
                        return -ColorSimilarUtils.calculateColorSimilarity(targetColor, pictureColor);
                    } catch (NumberFormatException e) {
                        // 如果颜色格式不正确导致解析失败，返回最大值,这样解析失败的图片会被排到列表最后
                        return Double.MAX_VALUE;
                    }
                }))
                .limit(12)
                .map(PictureVO::convertToPictureVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<PictureVO> searchLikedPicturesByColor(String picColor, User loginUser) {
        ThrowUtils.throwIf(StrUtil.isBlank(picColor) || loginUser == null, ErrorCode.PARAMETER_ERROR);

        // 1. 查询用户点赞的图片ID列表
        List<UserPictureAction> likedActions = userPictureActionMapper.selectList(
                new QueryWrapper<UserPictureAction>()
                        .eq("user_id", loginUser.getId())
                        .eq("action_type", "LIKE")
                        .eq("status", 1)
        );

        if (CollUtil.isEmpty(likedActions)) {
            return Collections.emptyList();
        }

        List<Long> likedPictureIds = likedActions.stream()
                .map(UserPictureAction::getPictureId)
                .collect(Collectors.toList());

        // 2. 查询这些图片的详细信息
        List<Picture> pictureList = this.lambdaQuery()
                .in(Picture::getId, likedPictureIds)
                .isNotNull(Picture::getPicColor)
                .list();

        if (CollUtil.isEmpty(pictureList)) {
            return Collections.emptyList();
        }

        // 3. 将用户输入的颜色字符串（如 "#FF0000"）解析为 Color 对象
        Color targetColor = Color.decode(picColor);

        // 4. 根据颜色相似度进行排序并转换为VO
        List<PictureVO> pictureVOList = pictureList.stream()
                // 过滤掉没有颜色信息的图片，减少后续计算量
                .filter(picture -> StrUtil.isNotBlank(picture.getPicColor()))
                // 根据颜色相似度进行排序
                .sorted(Comparator.comparingDouble(picture -> {
                    try {
                        Color pictureColor = Color.decode(picture.getPicColor());
                        // 计算当前图片颜色与目标颜色的相似度
                        return -ColorSimilarUtils.calculateColorSimilarity(targetColor, pictureColor);
                    } catch (NumberFormatException e) {
                        // 如果颜色格式不正确导致解析失败，返回最大值,这样解析失败的图片会被排到列表最后
                        return Double.MAX_VALUE;
                    }
                }))
                .limit(12)
                .map(picture -> {
                    PictureVO pictureVO = PictureVO.convertToPictureVO(picture);
                    // 设置点赞状态为true（因为这些都是用户点赞的图片）
                    pictureVO.setLiked(true);
                    // 查询收藏状态
                    UserPictureAction collectAction = userPictureActionMapper.selectOne(
                            new QueryWrapper<UserPictureAction>()
                                    .eq("user_id", loginUser.getId())
                                    .eq("picture_id", picture.getId())
                                    .eq("action_type", "COLLECT")
                                    .eq("status", 1)
                    );
                    pictureVO.setCollected(collectAction != null);
                    return pictureVO;
                })
                .collect(Collectors.toList());

        return pictureVOList;
    }

    @Override
    public List<PictureVO> searchCollectedPicturesByColor(String picColor, User loginUser) {
        ThrowUtils.throwIf(StrUtil.isBlank(picColor) || loginUser == null, ErrorCode.PARAMETER_ERROR);

        // 1. 查询用户收藏的图片ID列表
        List<UserPictureAction> collectedActions = userPictureActionMapper.selectList(
                new QueryWrapper<UserPictureAction>()
                        .eq("user_id", loginUser.getId())
                        .eq("action_type", "COLLECT")
                        .eq("status", 1)
        );

        if (CollUtil.isEmpty(collectedActions)) {
            return Collections.emptyList();
        }

        List<Long> collectedPictureIds = collectedActions.stream()
                .map(UserPictureAction::getPictureId)
                .collect(Collectors.toList());

        // 2. 查询这些图片的详细信息
        List<Picture> pictureList = this.lambdaQuery()
                .in(Picture::getId, collectedPictureIds)
                .isNotNull(Picture::getPicColor)
                .list();

        if (CollUtil.isEmpty(pictureList)) {
            return Collections.emptyList();
        }

        // 3. 将用户输入的颜色字符串（如 "#FF0000"）解析为 Color 对象
        Color targetColor = Color.decode(picColor);

        // 4. 根据颜色相似度进行排序并转换为VO
        List<PictureVO> pictureVOList = pictureList.stream()
                // 过滤掉没有颜色信息的图片，减少后续计算量
                .filter(picture -> StrUtil.isNotBlank(picture.getPicColor()))
                // 根据颜色相似度进行排序
                .sorted(Comparator.comparingDouble(picture -> {
                    try {
                        Color pictureColor = Color.decode(picture.getPicColor());
                        // 计算当前图片颜色与目标颜色的相似度
                        return -ColorSimilarUtils.calculateColorSimilarity(targetColor, pictureColor);
                    } catch (NumberFormatException e) {
                        // 如果颜色格式不正确导致解析失败，返回最大值,这样解析失败的图片会被排到列表最后
                        return Double.MAX_VALUE;
                    }
                }))
                .limit(12)
                .map(picture -> {
                    PictureVO pictureVO = PictureVO.convertToPictureVO(picture);
                    // 设置收藏状态为true（因为这些都是用户收藏的图片）
                    pictureVO.setCollected(true);
                    // 查询点赞状态
                    UserPictureAction likeAction = userPictureActionMapper.selectOne(
                            new QueryWrapper<UserPictureAction>()
                                    .eq("user_id", loginUser.getId())
                                    .eq("picture_id", picture.getId())
                                    .eq("action_type", "LIKE")
                                    .eq("status", 1)
                    );
                    pictureVO.setLiked(likeAction != null);
                    return pictureVO;
                })
                .collect(Collectors.toList());

        return pictureVOList;
    }

    /**
     * AI扩图
     *
     * @param createPictureOutPaintingTaskRequest
     * @param loginUser
     * @return
     */
    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        // 获取图片信息
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        Picture picture = Optional.ofNullable(this.getById(pictureId))
                .orElseThrow(() -> new BusinessException(ErrorCode.CANNOT_FOUND_DATA_ERROR));
        
        // VIP权限校验 - 只有VIP用户和管理员可以使用AI扩图功能
        checkVipPermissionForAiOutpainting(loginUser);
        
        // 权限校验
//        checkPictureAuth(loginUser, picture);

        // AI 扩图前置校验
        // 1. 格式校验
        String picFormat = picture.getPicFormat();
        final List<String> allowedFormats = Arrays.asList("JPG", "JPEG", "PNG", "HEIF", "WEBP");
        ThrowUtils.throwIf(StrUtil.isBlank(picFormat) || !allowedFormats.contains(picFormat.toUpperCase()),
                ErrorCode.PARAMETER_ERROR, "图片格式不支持，仅支持 JPG, JPEG, PNG, HEIF, WEBP");

        // 2. 分辨率校验
        Integer width = picture.getPicWidth();
        Integer height = picture.getPicHeight();
        final int MIN_RESOLUTION = 512;
        final int MAX_RESOLUTION = 4096;
        ThrowUtils.throwIf(width == null || height == null, ErrorCode.PARAMETER_ERROR, "图片尺寸信息丢失");
        ThrowUtils.throwIf(width < MIN_RESOLUTION || width > MAX_RESOLUTION || height < MIN_RESOLUTION || height > MAX_RESOLUTION,
                ErrorCode.PARAMETER_ERROR, "图片分辨率不符合要求，单边长度需在 512-4096 像素之间");

        // 构造请求参数
        CreateOutPaintingTaskRequest taskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        taskRequest.setInput(input);
        BeanUtil.copyProperties(createPictureOutPaintingTaskRequest, taskRequest);
        // 创建任务
        return aliYunAiApi.createOutPaintingTask(taskRequest);
    }

    /**
     * 批量编辑图片（仅限空间内）
     *
     * @param pictureEditByBatchRequest 批量编辑请求参数
     * @param loginUser                 当前登录用户
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();
        String nameRule = pictureEditByBatchRequest.getNameRule();
        // 1. 参数校验
        ThrowUtils.throwIf(CollUtil.isEmpty(pictureIdList) || spaceId == null, ErrorCode.PARAMETER_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.CANNOT_FOUND_DATA_ERROR, "空间不存在");
        ThrowUtils.throwIf(!space.getUserId().equals(loginUser.getId()),
                ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");

        // 校验是否至少有一个修改项
        boolean hasUpdateField = StrUtil.isNotBlank(category) ||
                CollUtil.isNotEmpty(tags) ||
                StrUtil.isNotBlank(nameRule);
        ThrowUtils.throwIf(!hasUpdateField, ErrorCode.PARAMETER_ERROR, "至少提供一个修改项");

        // 3. 查询指定图片（优化查询，只查询需要的字段）
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, pictureIdList)
                .list();
        if (pictureList.isEmpty()) {
            return;
        }
        // 安全校验：确保所有请求的图片都存在且属于该空间
        if (pictureList.size() != pictureIdList.size()) {
            throw new BusinessException(ErrorCode.PARAMETER_ERROR, "部分图片不存在或不属于该空间");
        }

        // 4. 构建更新数据
        List<Picture> updateList = new ArrayList<>();
        for (Picture picture : pictureList) {
            Picture updatePicture = new Picture();
            updatePicture.setId(picture.getId());

            // 只设置需要更新的字段
            if (StrUtil.isNotBlank(category)) {
                updatePicture.setCategory(category);
            }
            if (CollUtil.isNotEmpty(tags)) {
                updatePicture.setTags(JSONUtil.toJsonStr(tags));
            }
            updatePicture.setEditTime(TimeUtils.getCurrentBeijingTime());

            updateList.add(updatePicture);
        }

        // 5. 批量重命名（如果有命名规则）
        fillPictureWithNameRule(updateList, nameRule);

        // 6. 执行批量更新
        boolean result = this.updateBatchById(updateList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "批量编辑失败");
    }

    /**
     * 根据命名规则批量设置图片名称
     * nameRule 格式示例：图片{序号}
     *
     * @param pictureList 待更新的图片列表
     * @param nameRule    命名规则
     */
    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        if (StrUtil.isBlank(nameRule) || CollUtil.isEmpty(pictureList)) {
            return;
        }

        try {
            // 使用 AtomicInteger 更安全
            AtomicInteger count = new AtomicInteger(1);
            pictureList.forEach(picture -> {
                String pictureName = nameRule.replaceAll("\\{序号}", String.valueOf(count.getAndIncrement()));
                picture.setName(pictureName);
            });
        } catch (Exception e) {
            log.error("名称解析错误", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }
    }

    /**
     * 从图片 URL 提取 COS 的 key
     */
    private String extractCosKeyFromUrl(String url) {
        if (StrUtil.isBlank(url)) return null;
        String host = cosClientConfig.getHost();
        if (url.startsWith(host)) {
            //  https://my-bucket.cos.ap-beijing.myqcloud.com/yudiPicSync/public/1/avatar.jpg -> yudiPicSync/public/1/avatar.jpg
            return url.substring(host.length() + 1);
        }
        return url;
    }

    /**
     * 设置分享量显示控制
     * 规则：
     * 1. 图片上传者：显示具体分享量
     * 2. 管理员：显示所有图片的具体分享量
     * 3. 其他用户：模糊显示或不显示分享量
     *
     * @param pictureVO 图片VO对象
     * @param request   HTTP请求对象
     */
    private void setShareCountDisplayControl(PictureVO pictureVO, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureVO == null || request == null, ErrorCode.PARAMETER_ERROR);
        pictureVO.setShowShareCount(false);

        try {
            User loginUser = userService.getLoginUser(request);
            if (loginUser != null) {
                // 只有图片上传者或管理员才显示分享量
                boolean isOwner = pictureVO.getUserId() != null && pictureVO.getUserId().equals(loginUser.getId());
                boolean isAdmin = userService.isAdmin(loginUser);
                pictureVO.setShowShareCount(isOwner || isAdmin);
            }
        } catch (Exception e) {
            log.warn("设置分享量显示控制时发生异常: {}", e.getMessage());
            // 异常时保持默认值false
        }
    }

    /**
     * 分页获取用户点赞的图片
     *
     * @param queryDTO 查询参数
     * @param request  http请求
     * @return 包装后的分页对象
     */
    @Override
    public Page<PictureVO> listLikedPicturesByPage(PictureQueryDTO queryDTO, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        long current = queryDTO.getCurrent();
        long pageSize = queryDTO.getPageSize();

        // 构建查询条件，包含搜索条件
        QueryWrapper<Picture> queryWrapper = buildLikedPicturesQueryWrapper(loginUser.getId(), queryDTO);

        // 分页查询
        Page<Picture> picturePage = this.page(new Page<>(current, pageSize), queryWrapper);

        return this.getPictureVOPage(picturePage, request);
    }

    @Override
    public Page<PictureVO> listCollectedPicturesByPage(PictureQueryDTO queryDTO, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        long current = queryDTO.getCurrent();
        long pageSize = queryDTO.getPageSize();

        // 构建查询条件，包含搜索条件
        QueryWrapper<Picture> queryWrapper = buildCollectedPicturesQueryWrapper(loginUser.getId(), queryDTO);

        // 分页查询
        Page<Picture> picturePage = this.page(new Page<>(current, pageSize), queryWrapper);

        return this.getPictureVOPage(picturePage, request);
    }

    /**
     * 构建用户点赞图片的查询条件
     */
    private QueryWrapper<Picture> buildLikedPicturesQueryWrapper(Long userId, PictureQueryDTO queryDTO) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();

        // 基础条件：只查询用户点赞的图片
        queryWrapper.inSql("id",
                "SELECT picture_id FROM user_picture_action WHERE user_id = " + userId +
                        " AND action_type = 'LIKE' AND status = 1");

        // 应用搜索条件
        applySearchConditions(queryWrapper, queryDTO);

        return queryWrapper;
    }

    /**
     * 构建用户收藏图片的查询条件
     */
    private QueryWrapper<Picture> buildCollectedPicturesQueryWrapper(Long userId, PictureQueryDTO queryDTO) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();

        // 基础条件：只查询用户收藏的图片
        queryWrapper.inSql("id",
                "SELECT picture_id FROM user_picture_action WHERE user_id = " + userId +
                        " AND action_type = 'COLLECT' AND status = 1");

        // 应用搜索条件
        applySearchConditions(queryWrapper, queryDTO);

        return queryWrapper;
    }

    /**
     * 应用搜索条件到查询包装器
     */
    private void applySearchConditions(QueryWrapper<Picture> queryWrapper, PictureQueryDTO queryDTO) {
        if (queryDTO == null) {
            return;
        }

        // 从对象中取值
        String name = queryDTO.getName();
        String introduction = queryDTO.getIntroduction();
        String category = queryDTO.getCategory();
        List<String> tags = queryDTO.getTags();
        String searchText = queryDTO.getSearchText();
        String sortField = queryDTO.getSortField();
        String sortOrder = queryDTO.getSortOrder();

        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }

        // 精确匹配条件
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);

        // 标签搜索
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", tag);
            }
        }

        // 排序
        if (StrUtil.isNotBlank(sortField)) {
            boolean isAsc = "ascend".equals(sortOrder);
            queryWrapper.orderBy(true, isAsc, sortField);
        } else {
            // 默认按创建时间倒序
            queryWrapper.orderByDesc("createTime");
        }
    }

    @Override
    public Page<PictureVO> getPictureVOPageByIdsWithFilter(List<Long> pictureIds, PictureQueryDTO pictureQueryDTO,
         long current, long size, HttpServletRequest request) {
        if (CollUtil.isEmpty(pictureIds)) {
            log.warn("图片ID列表为空，返回空分页结果");
            return new Page<>(current, size);
        }

        try {
            // 1. 根据ID列表查询图片
            List<Picture> pictures = this.listByIds(pictureIds);
            if (CollUtil.isEmpty(pictures)) {
                log.warn("根据ID列表未找到图片，返回空分页结果");
                return new Page<>(current, size);
            }

            // 2. 保持推荐算法的排序顺序
            Map<Long, Picture> pictureMap = pictures.stream()
                    .collect(Collectors.toMap(Picture::getId, Function.identity()));
            List<Picture> orderedPictures = pictureIds.stream()
                    .map(pictureMap::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // 3. 应用筛选条件
            List<Picture> filteredPictures = orderedPictures.stream()
                    .filter(picture -> {
                        // 分类筛选
                        if (StrUtil.isNotBlank(pictureQueryDTO.getCategory())) {
                            if (!pictureQueryDTO.getCategory().equals(picture.getCategory())) {
                                return false;
                            }
                        }

                        // 搜索文本筛选（支持图片名称和简介的模糊搜索）
                        if (StrUtil.isNotBlank(pictureQueryDTO.getSearchText())) {
                            String searchText = pictureQueryDTO.getSearchText().toLowerCase();
                            String pictureName = picture.getName() != null ? picture.getName().toLowerCase() : "";
                            String pictureIntro = picture.getIntroduction() != null ? picture.getIntroduction().toLowerCase() : "";

                            if (!pictureName.contains(searchText) && !pictureIntro.contains(searchText)) {
                                return false;
                            }
                        }

                        return true;
                    })
                    .collect(Collectors.toList());

            if (CollUtil.isEmpty(filteredPictures)) {
                log.warn("筛选后无图片，返回空分页结果");
                return new Page<>(current, size);
            }

            // 4. 内存分页
            long total = filteredPictures.size();
            long start = (current - 1) * size;
            long end = Math.min(start + size, total);

            List<Picture> pagePictures;
            if (start >= total) {
                pagePictures = Collections.emptyList();
            } else {
                pagePictures = filteredPictures.subList((int) start, (int) end);
            }

            // 5. 批量转换为VO（仅转换当前页的）
            List<PictureVO> pageRecords = convertPicturesToVOs(pagePictures, request);

            // 6. 构造分页结果
            Page<PictureVO> result = new Page<>(current, size, total);
            result.setRecords(pageRecords);

            log.info("根据ID列表获取图片VO成功（支持搜索和筛选）: 请求{}个ID，排序后{}张，筛选后{}张，分页返回{}张",
                    pictureIds.size(), orderedPictures.size(), total, pageRecords.size());
            return result;

        } catch (Exception e) {
            log.error("根据ID列表获取图片VO失败（支持筛选）: {}", e.getMessage(), e);
            return new Page<>(current, size);
        }
    }

    /**
     * 清理用户缓存（定期清理过期缓存）
     */
    private void clearExpiredUserCache() {
        long currentTime = System.currentTimeMillis();
        // 5分钟过期
        long CACHE_EXPIRE_TIME = 5 * 60 * 1000;
        if (currentTime - lastCacheClearTime > CACHE_EXPIRE_TIME) {
            int hitCount = cacheHitCount.get();
            int missCount = cacheMissCount.get();
            int totalCount = hitCount + missCount;
            double hitRate = totalCount > 0 ? (double) hitCount / totalCount * 100 : 0;

            log.info("用户缓存清理 - 缓存大小: {}, 命中率: {:.2f}% ({}/{})",
                    userCache.size(), hitRate, hitCount, totalCount);

            userCache.clear();
            cacheHitCount.set(0);
            cacheMissCount.set(0);
            lastCacheClearTime = currentTime;
        }
    }

    /**
     * 清理批量查询缓存（定期清理过期缓存）
     */
    private void clearExpiredBatchCache() {
        long currentTime = System.currentTimeMillis();
        // 2分钟过期
        long BATCH_CACHE_EXPIRE_TIME = 2 * 60 * 1000;
        if (currentTime - lastBatchCacheClearTime > BATCH_CACHE_EXPIRE_TIME) {
            log.debug("批量查询缓存清理: 清理{}个缓存项", batchQueryCache.size());
            batchQueryCache.clear();
            lastBatchCacheClearTime = currentTime;
        }
    }

    /**
     * 批量获取用户信息（带缓存）
     *
     * @param userIds 用户ID集合
     * @return 用户ID到用户的映射
     */
    private Map<Long, User> getUsersWithCache(Set<Long> userIds) {
        clearExpiredUserCache();
        clearExpiredBatchCache();

        // 生成批量查询缓存键
        String batchCacheKey = "users_batch_" + userIds.stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        
        // 先检查批量查询缓存
        @SuppressWarnings("unchecked")
        Map<Long, User> batchCachedResult = (Map<Long, User>) batchQueryCache.get(batchCacheKey);
        if (batchCachedResult != null) {
            log.debug("批量用户查询缓存命中: {}个用户", userIds.size());
            return batchCachedResult;
        }

        Map<Long, User> result = new HashMap<>();
        Set<Long> needQueryIds = new HashSet<>();

        // 先从单个用户缓存中获取
        for (Long userId : userIds) {
            User cachedUser = userCache.get(userId);
            if (cachedUser != null) {
                result.put(userId, cachedUser);
                cacheHitCount.incrementAndGet();
            } else {
                needQueryIds.add(userId);
                cacheMissCount.incrementAndGet();
            }
        }

        // 查询缓存中没有的用户
        if (!needQueryIds.isEmpty()) {
            List<User> users = userService.listByIds(needQueryIds);
            for (User user : users) {
                result.put(user.getId(), user);
                // 检查缓存大小，如果超过限制则清理最旧的缓存
                if (userCache.size() >= MAX_CACHE_SIZE) {
                    clearOldestCache();
                }
                userCache.put(user.getId(), user); // 加入缓存
            }

            // 记录查询统计
            log.debug("用户查询统计: 请求{}个用户ID，缓存命中{}个，数据库查询{}个，实际返回{}个",
                    userIds.size(), userIds.size() - needQueryIds.size(), needQueryIds.size(), users.size());
        }

        // 缓存批量查询结果
        batchQueryCache.put(batchCacheKey, new HashMap<>(result));
        
        return result;
    }

    /**
     * 清理最旧的缓存（简单的LRU策略）
     */
    private void clearOldestCache() {
        if (userCache.size() > MAX_CACHE_SIZE * 0.8) { // 清理到80%容量
            int removeCount = userCache.size() - (int) (MAX_CACHE_SIZE * 0.8);
            Iterator<Map.Entry<Long, User>> iterator = userCache.entrySet().iterator();
            int removed = 0;
            while (iterator.hasNext() && removed < removeCount) {
                iterator.next();
                iterator.remove();
                removed++;
            }
            log.debug("清理了 {} 个最旧的用户缓存", removed);
        }
    }

    /**
     * 批量获取用户对图片的操作状态（带缓存优化）
     *
     * @param userId     用户ID
     * @param pictureIds 图片ID列表
     * @return 包含LIKE和COLLECT状态的Map
     */
    private Map<String, Set<Long>> getUserActionStatusBatch(Long userId, List<Long> pictureIds) {
        if (userId == null || CollUtil.isEmpty(pictureIds)) {
            Map<String, Set<Long>> result = new HashMap<>();
            result.put("LIKE", new HashSet<>());
            result.put("COLLECT", new HashSet<>());
            return result;
        }

        // 清理过期缓存
        long currentTime = System.currentTimeMillis();
        // 2分钟过期
        long ACTION_CACHE_EXPIRE_TIME = 2 * 60 * 1000;
        if (currentTime - lastActionCacheClearTime > ACTION_CACHE_EXPIRE_TIME) {
            userActionCache.clear();
            lastActionCacheClearTime = currentTime;
            log.debug("用户操作缓存已清理");
        }

        Map<String, Set<Long>> result = new HashMap<>();
        Set<Long> likedSet = new HashSet<>();
        Set<Long> collectedSet = new HashSet<>();

        // 检查缓存
        String likeCacheKey = userId + ":LIKE";
        String collectCacheKey = userId + ":COLLECT";
        Set<Long> cachedLiked = userActionCache.get(likeCacheKey);
        Set<Long> cachedCollected = userActionCache.get(collectCacheKey);

        if (cachedLiked != null && cachedCollected != null) {
            // 从缓存中获取交集
            likedSet = pictureIds.stream()
                    .filter(cachedLiked::contains)
                    .collect(Collectors.toSet());
            collectedSet = pictureIds.stream()
                    .filter(cachedCollected::contains)
                    .collect(Collectors.toSet());
        } else {
            // 查询数据库（一次性查询所有操作类型）
            String queryKey = "user_action_" + userId + "_" + pictureIds.size();
            if (checkQueryDeduplication(queryKey)) {
                List<UserPictureAction> actions = userPictureActionMapper.selectList(
                        new QueryWrapper<UserPictureAction>()
                                .eq("user_id", userId)
                                .in("picture_id", pictureIds)
                                .in("action_type", "LIKE", "COLLECT")
                                .eq("status", 1)
                );

                // 分别处理LIKE和COLLECT
                for (UserPictureAction action : actions) {
                    if ("LIKE".equals(action.getActionType())) {
                        likedSet.add(action.getPictureId());
                    } else if ("COLLECT".equals(action.getActionType())) {
                        collectedSet.add(action.getPictureId());
                    }
                }

                // 缓存结果
                userActionCache.put(likeCacheKey, likedSet);
                userActionCache.put(collectCacheKey, collectedSet);
            } else {
                // 查询被去重，返回空结果
                log.debug("用户操作状态查询被去重: userId={}, pictureCount={}", userId, pictureIds.size());
            }
        }

        result.put("LIKE", likedSet);
        result.put("COLLECT", collectedSet);
        return result;
    }

    /**
     * 获取用户对图片的操作状态（带缓存）
     *
     * @param userId     用户ID
     * @param pictureIds 图片ID列表
     * @param actionType 操作类型（LIKE/COLLECT）
     * @return 用户已操作的图片ID集合
     */
    private Set<Long> getUserActionStatus(Long userId, List<Long> pictureIds, String actionType) {
        Map<String, Set<Long>> actionMap = getUserActionStatusBatch(userId, pictureIds);
        return actionMap.get(actionType);
    }

    /**
     * 获取Space信息（带缓存）
     *
     * @param spaceIds Space ID集合
     * @return Space ID到Space的映射
     */
    private Map<Long, Space> getSpacesWithCache(Set<Long> spaceIds) {
        if (CollUtil.isEmpty(spaceIds)) {
            return new HashMap<>();
        }

        // 清理过期缓存
        long currentTime = System.currentTimeMillis();
        // 10分钟过期
        long SPACE_CACHE_EXPIRE_TIME = 10 * 60 * 1000;
        if (currentTime - lastSpaceCacheClearTime > SPACE_CACHE_EXPIRE_TIME) {
            spaceCache.clear();
            lastSpaceCacheClearTime = currentTime;
            log.debug("Space缓存已清理");
        }

        Map<Long, Space> result = new HashMap<>();
        Set<Long> needQueryIds = new HashSet<>();

        // 先从缓存中获取
        for (Long spaceId : spaceIds) {
            Space cachedSpace = spaceCache.get(spaceId);
            if (cachedSpace != null) {
                result.put(spaceId, cachedSpace);
            } else {
                needQueryIds.add(spaceId);
            }
        }

        // 查询缓存中没有的Space
        if (!needQueryIds.isEmpty()) {
            List<Space> spaces = spaceService.listByIds(needQueryIds);
            for (Space space : spaces) {
                result.put(space.getId(), space);
                spaceCache.put(space.getId(), space); // 加入缓存
            }
        }

        return result;
    }

    /**
     * 检查查询去重（避免短时间内重复查询）
     *
     * @param queryKey 查询键
     * @return true表示可以查询，false表示需要去重
     */
    private boolean checkQueryDeduplication(String queryKey) {
        long currentTime = System.currentTimeMillis();
        Long lastQueryTime = queryTimestampCache.get(queryKey);
        // 1秒内去重
        long QUERY_REPEAT_TIME = 1000;
        if (lastQueryTime == null || currentTime - lastQueryTime > QUERY_REPEAT_TIME) {
            queryTimestampCache.put(queryKey, currentTime);
            return true;
        }

        log.debug("查询去重: {} 在{}ms内重复查询", queryKey, currentTime - lastQueryTime);
        return false;
    }

    /**
     * 批量转换图片列表为VO列表（优化版本，避免N+1查询）
     *
     * @param pictures 图片列表
     * @param request  HTTP请求
     * @return PictureVO列表
     */
    private List<PictureVO> convertPicturesToVOs(List<Picture> pictures, HttpServletRequest request) {
        if (CollUtil.isEmpty(pictures)) {
            return Collections.emptyList();
        }

        // 1. 提取所有需要查询的用户ID（使用Set去重）
        Set<Long> userIdSet = pictures.stream()
                .map(Picture::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 2. 批量查询用户信息（带缓存优化）
        Map<Long, User> userMap = getUsersWithCache(userIdSet);

        // 3. 批量查询Space信息（带缓存优化）
        Set<Long> spaceIdSet = pictures.stream()
                .map(Picture::getSpaceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Space> spaceMap = getSpacesWithCache(spaceIdSet);

        // 4. 查询当前用户对这些图片的操作状态（点赞、收藏）- 使用批量缓存优化
        List<Long> pictureIdList = pictures.stream().map(Picture::getId).collect(Collectors.toList());
        User loginUser = userService.getLoginUserSafely(request);
        Set<Long> likedPictureIdSet = new HashSet<>();
        Set<Long> collectedPictureIdSet = new HashSet<>();

        if (loginUser != null && CollUtil.isNotEmpty(pictureIdList)) {
            // 使用批量缓存获取用户操作状态（一次性查询LIKE和COLLECT）
            Map<String, Set<Long>> actionMap = getUserActionStatusBatch(loginUser.getId(), pictureIdList);
            likedPictureIdSet = actionMap.get("LIKE");
            collectedPictureIdSet = actionMap.get("COLLECT");
        }

        // 5. 单次遍历完成实体转换和数据填充
        final Set<Long> finalLikedPictureIdSet = likedPictureIdSet;
        final Set<Long> finalCollectedPictureIdSet = collectedPictureIdSet;

        return pictures.stream().map(picture -> {
            // entity -> vo
            PictureVO pictureVO = PictureVO.convertToPictureVO(picture);

            // 从Map中获取关联的用户信息（高效O(1)查询）
            User user = userMap.get(picture.getUserId());
            pictureVO.setUserVO(userService.getUserVO(user));

            // 从Map中获取关联的Space信息（高效O(1)查询）
            Space space = spaceMap.get(picture.getSpaceId());
            if (space != null) {
                // 如果需要Space信息，可以在这里设置
                // pictureVO.setSpaceVO(spaceService.getSpaceVO(space, request));
            }

            // 设置分享量显示控制
            setShareCountDisplayControl(pictureVO, request);

            // 设置当前用户的操作状态
            pictureVO.setLiked(finalLikedPictureIdSet.contains(picture.getId()));
            pictureVO.setCollected(finalCollectedPictureIdSet.contains(picture.getId()));

            return pictureVO;
        }).collect(Collectors.toList());
    }

    /**
     * 获取图片VO（公共访问版本，仅显示图片和作者信息）
     *
     * @param picture 图片实体
     * @param request HTTP请求
     * @return 图片VO
     */
    @Override
    public PictureVO getPictureVOForPublic(Picture picture, HttpServletRequest request) {
        if (picture == null) {
            return null;
        }

        // entity -> vo
        PictureVO pictureVO = PictureVO.convertToPictureVO(picture);

        // 获取作者信息
        User user = userService.getById(picture.getUserId());
        pictureVO.setUserVO(userService.getUserVO(user));

        // 未登录用户不显示操作状态
        pictureVO.setLiked(false);
        pictureVO.setCollected(false);
        
        // 不显示权限列表
        pictureVO.setPermissionList(Collections.emptyList());

        return pictureVO;
    }

    /**
     * 获取图片分页VO（公共访问版本，支持未登录用户）
     * 仅显示公共图库的图片，不显示用户操作状态
     *
     * @param picturePage 图片分页对象
     * @param request     HTTP请求
     * @return 包装后的分页对象
     */
    @Override
    public Page<PictureVO> getPictureVOPageForPublic(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictures = picturePage.getRecords();
        if (CollectionUtils.isEmpty(pictures)) {
            return new Page<>();
        }
        
        // 1. 关联查询用户信息
        Set<Long> userIdSet = pictures.stream().map(Picture::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        
        // 2. 填充信息
        List<PictureVO> pictureVOList = pictures.stream().map(picture -> {
            // entity -> vo
            PictureVO pictureVO = PictureVO.convertToPictureVO(picture);
            
            // 填充用户信息
            Long userId = picture.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUserVO(userService.getUserVO(user));
            
            // 未登录用户不显示操作状态
            pictureVO.setLiked(false);
            pictureVO.setCollected(false);
            
            // 不显示权限列表
            pictureVO.setPermissionList(Collections.emptyList());
            
            return pictureVO;
        }).collect(Collectors.toList());
        
        // 3. 封装返回
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    /**
     * 清理图片相关的所有缓存
     * 包括：Redis推荐缓存、图片计数缓存等
     *
     * @param pictureId 图片ID
     */
    private void clearPictureCaches(Long pictureId) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMETER_ERROR, "无效图片");
        try {
            // 1. 清理Redis推荐缓存中的图片ID
            redisRankServiceImpl.removePictureFromRank(pictureId);
            // 2. 清理图片计数缓存（如果有的话）
            // 这里可以添加其他需要清理的缓存
        } catch (Exception e) {
            log.error("清理图片{}缓存失败: {}", pictureId, e.getMessage(), e);
        }
    }

    /**
     * 检查用户是否有使用AI扩图功能的权限
     * 只有VIP用户和管理员可以使用
     */
    private void checkVipPermissionForAiOutpainting(User loginUser) {
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "用户未登录");
        }
        
        // 管理员可以使用
        if (UserRoleEnum.ADMIN.getValue().equals(loginUser.getUserRole())) {
            return;
        }
        
        // VIP用户可以使用
        if (UserRoleEnum.VIP.getValue().equals(loginUser.getUserRole())) {
            // 检查VIP是否过期
            if (loginUser.getVipExpireTime() != null) {
                try {
                    if (loginUser.getVipExpireTime().after(new Date())) {
                        return; // VIP未过期，可以使用
                    }
                } catch (Exception e) {
                    log.error("检查VIP过期时间失败", e);
                }
            }
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "VIP已过期，无法使用AI扩图功能");
        }
        
        // 普通用户无法使用
        throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "AI扩图功能仅限VIP用户使用，请升级会员");
    }

    /**
     * 获取用户发布图片的统计数据
     *
     * @param userId 用户ID
     * @return 统计数据
     */
    @Override
    public PictureStatsDTO getPublishedPictureStats(Long userId) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMETER_ERROR);
        
        // 查询用户发布的所有图片（包括已删除的，用于统计）
        LambdaQueryWrapper<Picture> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Picture::getUserId, userId)
                   .isNull(Picture::getSpaceId); // 只统计公共图库的图片
        
        List<Picture> pictures = this.list(queryWrapper);
        
        if (CollUtil.isEmpty(pictures)) {
            // 如果没有发布图片，返回空统计数据
            PictureStatsDTO stats = new PictureStatsDTO();
            stats.setTotalPictures(0L);
            stats.setTotalLikes(0L);
            stats.setTotalCollects(0L);
            stats.setTotalViews(0L);
            stats.setTotalDownloads(0L);
            return stats;
        }
        
        // 计算统计数据
        long totalPictures = pictures.size();
        long totalLikes = pictures.stream().mapToLong(p -> p.getLikeCount() != null ? p.getLikeCount() : 0L).sum();
        long totalCollects = pictures.stream().mapToLong(p -> p.getCollectCount() != null ? p.getCollectCount() : 0L).sum();
        long totalViews = pictures.stream().mapToLong(p -> p.getViewCount() != null ? p.getViewCount() : 0L).sum();
        long totalDownloads = pictures.stream().mapToLong(p -> p.getDownloadCount() != null ? p.getDownloadCount() : 0L).sum();
        
        PictureStatsDTO stats = new PictureStatsDTO();
        stats.setTotalPictures(totalPictures);
        stats.setTotalLikes(totalLikes);
        stats.setTotalCollects(totalCollects);
        stats.setTotalViews(totalViews);
        stats.setTotalDownloads(totalDownloads);
        
        return stats;
    }
}






