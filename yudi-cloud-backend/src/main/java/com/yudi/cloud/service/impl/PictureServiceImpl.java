package com.yudi.cloud.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
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
import com.yudi.cloud.manager.upload.FilePictureUpload;
import com.yudi.cloud.manager.upload.PictureUploadTemplate;
import com.yudi.cloud.manager.upload.UrlPictureUpload;
import com.yudi.cloud.mapper.PictureMapper;
import com.yudi.cloud.model.dto.picture.*;
import com.yudi.cloud.model.entity.Picture;
import com.yudi.cloud.model.entity.Space;
import com.yudi.cloud.model.entity.User;
import com.yudi.cloud.model.enums.PictureReviewStatusEnum;
import com.yudi.cloud.model.enums.SpaceTypeEnum;
import com.yudi.cloud.model.vo.picture.PictureVO;
import com.yudi.cloud.model.vo.user.UserVO;
import com.yudi.cloud.service.PictureService;
import com.yudi.cloud.service.SpaceService;
import com.yudi.cloud.service.UserService;
import com.yudi.cloud.utils.ColorSimilarUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
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
        this.fillReviewParams(picture, loginUser);
        if (isUpdate) {
            picture.setId(pictureId);
            picture.setEditTime(new Date());
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
        return PictureVO.convertToPictureVO(picture);
    }

    /**
     * 删除图片
     * @param pictureId
     * @param loginUser
     */
    @Override
    public PictureDeleteResponse deletePicture(Long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMETER_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 判断是否存在
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.CANNOT_FOUND_DATA_ERROR);
        // 校验权限
        checkPictureAuth(loginUser, oldPicture);
        // 开启事务
        transactionTemplate.execute(status -> {
            boolean result = this.removeById(pictureId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            
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
            return true;
        });
        // 异步清理文件
        this.clearPictureFiles(oldPicture);
        
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
        
        return pictureVO;
    }

    /**
     * 将 Picture 分页转换为 PictureVO 分页，并填充关联的用户信息
     * 使用高效的一次性数据加载和单次遍历转换策略
     *
     * @param picturePage 原始 Picture 分页数据
     * @param request HTTP 请求对象，用于可能的用户信息获取
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
                .collect(Collectors.toSet());
        // 2.批量查询用户信息，并构建ID到用户的映射Map
        // 使用toMap而不是groupingBy，因为每个ID对应唯一用户，避免不必要的List结构
        Map<Long, User> userMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.toMap(
                        User::getId,                  // 键提取器：用户ID
                        Function.identity(),          // 值提取器：用户对象本身
                        (existing, replacement) -> existing // 合并函数：处理键冲突（保留现有值）
                ));
        // 3.单次遍历完成实体转换和数据填充
        List<PictureVO> pictureVOList = pictureList.stream().map(picture -> {
            // entity -> vo
            PictureVO pictureVO = PictureVO.convertToPictureVO(picture);
            // 从Map中获取关联的用户信息（高效O(1)查询）
            User user = userMap.get(picture.getUserId());

            // 将User转换为UserVO并设置到PictureVO中
            // 注意：userService.getUserVO应能处理user为null的情况
            pictureVO.setUserVO(userService.getUserVO(user));
            
            // 设置分享量显示控制
            setShareCountDisplayControl(pictureVO, request);
            
            return pictureVO;
        }).collect(Collectors.toList());
        // 将转换后的VO列表设置到分页对象中
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    /**
     *编辑图片
     * @param pictureEditDTO
     * @param loginUser
     */
    @Override
    public void editPicture(PictureEditDTO pictureEditDTO, User loginUser) {
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditDTO, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditDTO.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        this.validPicture(picture);
        // 判断是否存在
        long id = pictureEditDTO.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.CANNOT_FOUND_DATA_ERROR);
        // 校验权限
//        checkPictureAuth(loginUser, oldPicture);
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
        // 4.数据库操作
        Picture updatePicture = new Picture();
        BeanUtil.copyProperties(pictureReviewDTO, updatePicture);
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        boolean result = this.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
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
            picture.setReviewMessage("管理员上传自动过审");
            picture.setReviewerId(loginUser.getId());
            picture.setReviewTime(new Date());
        } else {
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }

    /**
     * 批量抓取上传图片
     *
     * @param pictureUploadByBatchDTO
     * @param loginUser
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
            
            // 输出页面的一部分HTML用于调试
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
     * 【通用】清理图片文件（COS：原图 + WebP + 缩略图）
     * 适用于：更新图片前清理旧图、删除图片时清理文件
     *
     * @param picture 图片实体（必须包含 url）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void clearPictureFiles(Picture picture) {
        if (picture == null || StrUtil.isBlank(picture.getUrl())) {
            log.warn("Skip clearing picture files: picture is null or URL is empty");
            return;
        }

        String cosKey = extractCosKeyFromUrl(picture.getUrl());
        cosManager.deletePictureObject(cosKey);
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
     * @param pictureEditByBatchRequest   批量编辑请求参数
     * @param loginUser 当前登录用户
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
            updatePicture.setEditTime(new Date());

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
     * @param nameRule 命名规则
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
            return url.substring(host.length() + 1); // 去掉 "https://host/"
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
     * @param request HTTP请求对象
     */
    private void setShareCountDisplayControl(PictureVO pictureVO, HttpServletRequest request) {
        if (pictureVO == null || request == null) {
            pictureVO.setShowShareCount(false);
            return;
        }

        try {
            // 获取当前登录用户
            User loginUser = userService.getLoginUser(request);
            if (loginUser == null) {
                // 未登录用户，不显示分享量
                pictureVO.setShowShareCount(false);
                return;
            }

            // 判断是否显示具体分享量
            boolean showShareCount = false;

            // 1. 图片上传者：显示具体分享量
            if (pictureVO.getUserId() != null && pictureVO.getUserId().equals(loginUser.getId())) {
                showShareCount = true;
            }
            // 2. 管理员：显示所有图片的具体分享量
            else if (userService.isAdmin(loginUser)) {
                showShareCount = true;
            }
            // 3. 其他用户：不显示具体分享量
            else {
                showShareCount = false;
            }

            pictureVO.setShowShareCount(showShareCount);

        } catch (Exception e) {
            // 异常情况下，不显示分享量
            log.warn("设置分享量显示控制时发生异常: {}", e.getMessage());
            pictureVO.setShowShareCount(false);
        }
    }

}




