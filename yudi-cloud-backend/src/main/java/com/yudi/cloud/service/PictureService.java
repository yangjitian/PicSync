package com.yudi.cloud.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yudi.cloud.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.yudi.cloud.model.dto.picture.*;
import com.yudi.cloud.model.entity.Picture;
import com.yudi.cloud.model.entity.User;
import com.yudi.cloud.model.vo.picture.PictureVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author yudi
 * date 2025-07-20
 */
public interface PictureService extends IService<Picture> {

    /**
     * 校验图片
     *
     * @param picture
     */
    void validPicture(Picture picture);

    /**
     * 上传图片
     */
    PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser);

    /**
     * 删除图片
     * @param pictureId
     * @param loginUser
     * @return 删除响应信息
     */
    PictureDeleteResponse deletePicture(Long pictureId, User loginUser);

    /**
     * 获取查询对象
     *
     * @param pictureUploadDTO
     * @return
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryDTO pictureUploadDTO);

    /**
     * 获取图片包装类（单条）
     *
     * @param picture
     * @param request
     * @return
     */
    PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    /**
     * 获取图片包装类（分页）
     *
     * @param picturePage
     * @param request
     * @return
     */
    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    /**
     *编辑图片
     * @param pictureEditDTO
     * @param loginUser
     */
    void editPicture(PictureEditDTO pictureEditDTO, User loginUser);


    /**
     * 图片审核
     *
     * @param pictureReviewDTO
     * @param loginUser
     * @return
     */
    void pictureReview(PictureReviewDTO pictureReviewDTO, User loginUser);

    /**
     * 补充审核参数
     *
     * @param picture
     * @param loginUser
     */
    void fillReviewParams(Picture picture, User loginUser);

    /**
     * 批量抓取上传图片
     *
     * @param pictureUploadByBatchDTO
     * @param loginUser
     * @return 上传成功数量
     */
    Integer uploadPictureByBatch(PictureUploadByBatchDTO pictureUploadByBatchDTO, User loginUser);

    /**
     * 分页查询图片（带缓存）
     */
    Page<PictureVO> getPictureVOPageWithCache(PictureQueryDTO pictureQueryDTO, HttpServletRequest request);

    /**
     * 权限校验
     * @param loginUser
     * @param picture
     */
    void checkPictureAuth(User loginUser, Picture picture);

    /**
     * 清理图片
     * @param picture
     */
    void clearPictureFiles(Picture picture);

    /**
     * 根据颜色搜索图片
     *
     * @param spaceId
     * @param picColor
     * @param loginUser
     * @return
     */
    List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser);

    /**
     * 按颜色搜索用户点赞的图片
     * @param picColor
     * @param loginUser
     * @return
     */
    List<PictureVO> searchLikedPicturesByColor(String picColor, User loginUser);

    /**
     * 按颜色搜索用户收藏的图片
     * @param picColor
     * @param loginUser
     * @return
     */
    List<PictureVO> searchCollectedPicturesByColor(String picColor, User loginUser);

    /**
     * AI扩图
     * @param createPictureOutPaintingTaskRequest
     * @param loginUser
     * @return
     */
    CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser);

    /**
     * 批量编辑图片（仅限空间内）
     *
     * @param request
     * @param loginUser
     */
    void editPictureByBatch(PictureEditByBatchRequest request, User loginUser);

    /**
     * 分页获取用户点赞的图片
     *
     * @param queryDTO 查询参数
     * @param request  http请求
     * @return 包装后的分页对象
     */
    Page<PictureVO> listLikedPicturesByPage(PictureQueryDTO queryDTO, HttpServletRequest request);

    /**
     * 分页获取用户收藏的图片
     *
     * @param queryDTO 查询参数
     * @param request  http请求
     * @return 包装后的分页对象
     */
    Page<PictureVO> listCollectedPicturesByPage(PictureQueryDTO queryDTO, HttpServletRequest request);
}
