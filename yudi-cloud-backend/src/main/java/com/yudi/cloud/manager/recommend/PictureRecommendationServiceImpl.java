package com.yudi.cloud.manager.recommend;

import com.yudi.cloud.model.entity.Picture;
import com.yudi.cloud.utils.TimeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 图片推荐分数计算服务
 * - 核心算法：新图衰减+冷启动 / 老图无衰减+行为质量主导 / 小用户敏感变换 / 平滑防抖
 *
 * @author yudi
 * @since 1.0.0
 */
@Service
@Slf4j
public class PictureRecommendationServiceImpl {

    private static final double LAMBDA = Math.log(2) / 24.0;

    // ========== 行为权重 ==========
    private static final double VIEW_WEIGHT = 0.10;
    private static final double LIKE_WEIGHT = 0.20;
    private static final double COLLECT_WEIGHT = 0.25;
    private static final double SHARE_WEIGHT = 0.20;
    private static final double DOWNLOAD_WEIGHT = 0.25;
    private static final double COMMENT_WEIGHT = 0.00; // 评论权重，预留

    // ========== 核心参数 ==========
    private static final double BEHAVIOR_EXPONENT = 0.7;        // 行为变换指数

    // 新，中生，老图平滑系数
    private static final double SMOOTH_ALPHA_NEW = 0.8;
    private static final double SMOOTH_ALPHA_MODERATE = 0.5;
    private static final double SMOOTH_ALPHA_OLD = 0.2;

    // 最大变化设限
    private static final double MAX_CHANGE_NEW = 15.0;
    private static final double MAX_CHANGE_MODERATE = 8.0;
    private static final double MAX_CHANGE_OLD = 5.0;

    /**
     * 计算单张图片的推荐分数
     */
    public double calculateRecommendScore(Picture picture, double oldScore) {
        if (picture == null) {
            return 0.0;
        }
        
        // 检查图片是否已被删除，已删除的图片不再计算推荐分数
        if (picture.getIsDelete() != null && picture.getIsDelete() == 1) {
            log.debug("图片{}已被删除，跳过推荐分数计算", picture.getId());
            return 0.0;
        }

        try {
            Date now = TimeUtils.getCurrentBeijingTime();
            long hoursSincePublish = picture.getCreateTime() == null ? 0 :
                    (now.getTime() - picture.getCreateTime().getTime()) / (60 * 60 * 1000);

            // 1. 行为加权分
            double behaviorScore = 0.0;

            behaviorScore += VIEW_WEIGHT * transform(picture.getViewCount());
            behaviorScore += LIKE_WEIGHT * transform(picture.getLikeCount());
            behaviorScore += COLLECT_WEIGHT * transform(picture.getCollectCount());
            behaviorScore += SHARE_WEIGHT * transform(picture.getShareCount());
            behaviorScore += DOWNLOAD_WEIGHT * transform(picture.getDownloadCount());
            behaviorScore += COMMENT_WEIGHT;

            // 2. 时间衰减因子（>72小时=1.0）
            double decay;
            if (hoursSincePublish <= 72) {
                decay = Math.exp(-LAMBDA * Math.max(0, hoursSincePublish));
                boolean hasRecentEngagement = picture.getEditTime() != null &&
                        (now.getTime() - picture.getEditTime().getTime()) / (60 * 60 * 1000) <= 24;
                if (!hasRecentEngagement && hoursSincePublish > 24) {
                    decay *= 0.5;
                }
            } else {
                decay = 1.0; // 老图彻底摆脱时间惩罚
            }

            // 3. 质量调节因子
            double qualityFactor = 1.0;

            if (picture.getReviewStatus() != null && picture.getReviewStatus() == 1) {
                qualityFactor *= 1.2;
            }

            if (picture.getPicWidth() != null && picture.getPicHeight() != null) {
                long pixels = (long) picture.getPicWidth() * picture.getPicHeight();
                if (pixels >= 1920 * 1080) {
                    qualityFactor *= 1.1;
                } else if (pixels >= 1280 * 720) {
                    qualityFactor *= 1.05;
                }
            }

            if (picture.getPicSize() != null) {
                long sizeMB = picture.getPicSize() / (1024 * 1024);
                if (sizeMB >= 1 && sizeMB <= 10) {
                    qualityFactor *= 1.05;
                }
            }

            qualityFactor = Math.min(qualityFactor, 2.0);

            // 4. 冷启动加成
            double coldStartBoost = 0.0;
            if (hoursSincePublish < 72) {
                if (hoursSincePublish < 24) {
                    coldStartBoost = ((72.0 - hoursSincePublish) / 72.0) * 50.0;
                } else {
                    boolean hasEarlyEngagement = (picture.getLikeCount() > 0 ||
                            picture.getCollectCount() > 0 ||
                            picture.getDownloadCount() > 0 ||
                            picture.getShareCount() > 0);
                    if (hasEarlyEngagement) {
                        coldStartBoost = ((72.0 - hoursSincePublish) / 72.0) * 30.0;
                    }
                }
            }

            // 5. 原始得分
            double rawScore = behaviorScore * decay * qualityFactor + coldStartBoost;

            // 6. 平滑处理
            double alpha = hoursSincePublish < 24 ? SMOOTH_ALPHA_NEW :
                    hoursSincePublish < 72 ? SMOOTH_ALPHA_MODERATE : SMOOTH_ALPHA_OLD;

            double smoothedScore = alpha * rawScore + (1 - alpha) * oldScore;

            // 7. 涨跌幅限制
            double maxChange = hoursSincePublish < 24 ? MAX_CHANGE_NEW :
                    hoursSincePublish < 72 ? MAX_CHANGE_MODERATE : MAX_CHANGE_OLD;

            double finalScore = Math.max(0.0,
                    Math.min(oldScore + maxChange,
                            Math.max(oldScore - maxChange, smoothedScore)));

            // ========== 8. 日志记录 ==========
            log.debug("图片{}推荐分数计算完成 | 行为分={:.2f} | 衰减={:.3f} | 质量因子={:.2f} | 冷启动={:.2f} | 原始分={:.2f} | 平滑分={:.2f} | 最终分={:.2f}",
                    picture.getId(), behaviorScore, decay, qualityFactor, coldStartBoost, rawScore, smoothedScore, finalScore);

            return finalScore;

        } catch (Exception e) {
            log.error("计算图片{}推荐分数失败，返回旧分数。错误: {}", picture.getId(), e.getMessage(), e);
            return oldScore;
        }
    }

    private double transform(Long value) {
        return (value == null || value <= 0) ? 0.0 : Math.pow(value, BEHAVIOR_EXPONENT);
    }
}