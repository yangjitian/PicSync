package com.yudi.cloud.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yudi.cloud.model.entity.UserPictureAction;
import com.yudi.cloud.model.vo.picture.PictureVO;
import com.yudi.cloud.model.vo.picture.UserPictureActionStatus;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 图片行为服务接口
 * 处理用户对图片的浏览、点赞、收藏、分享等行为
 */
public interface PictureActionService {

    /**
     * 增加浏览量（今天内防重复，从凌晨0点到23:59:59）
     * @return 浏览量操作结果（包含是否成功和最新计数）
     */
    Map<String, Object> addViewCount(Long pictureId, Long userId);

    /**
     * 点赞/取消点赞（优化版本，直接返回状态和计数）
     */
    Map<String, Object> toggleLikeWithCount(Long pictureId, Long userId);

    /**
     * 收藏/取消收藏（优化版本，直接返回状态和计数）
     */
    Map<String, Object> toggleCollectWithCount(Long pictureId, Long userId);

    /**
     * 增加分享量（1小时内防重复）
     * @return 分享操作结果（包含是否成功和最新计数）
     */
    Map<String, Object> addShareCount(Long pictureId, Long userId);

    /**
     * 增加下载量（支持多次下载累加）
     * @return 下载操作结果（包含是否成功和最新计数）
     */
    Map<String, Object> addDownloadCount(Long pictureId, Long userId);

    /**
     * 获取用户对图片的行为状态
     */
    UserPictureActionStatus getUserActionStatus(Long pictureId, Long userId);

    /**
     * 批量获取用户对多张图片的行为状态
     */
    Map<Long, UserPictureActionStatus> batchGetUserActionStatus(List<Long> pictureIds, Long userId);

    /**
     * 生成图片分享链接（MVP简化版本）
     * @param pictureId 图片ID
     * @param request HTTP请求
     * @return 分享链接
     */
    String generateShareLink(Long pictureId, HttpServletRequest request);

    /**
     * 获取用户统计数据
     * @param userId 用户ID
     * @return 用户统计数据
     */
    Map<String, Object> getUserStats(Long userId);
}