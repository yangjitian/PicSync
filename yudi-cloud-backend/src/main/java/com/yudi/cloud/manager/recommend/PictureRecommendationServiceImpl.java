package com.yudi.cloud.manager.recommend;

import com.yudi.cloud.model.entity.Picture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import com.yudi.cloud.utils.TimeUtils;

/**
 * 图片推荐分数计算服务
 * 核心算法公式：
 * 最终分数 = (行为加权分 × 时间衰减因子 × 质量调节因子) + 冷启动加成
 *
 * @author yudi
 * @since 1.0.0
 */
@Service
@Slf4j
public class PictureRecommendationServiceImpl {

    /**
     * 时间衰减系数 λ = ln(2) / 48 ≈ 0.014426
     * 表示每48小时分数衰减一半，用于实现内容的自然更替
     * 衰减公式：decay = e^(-λ × hours)
     */
    private static final double LAMBDA = Math.log(2) / 48.0;

    /**
     * 用户行为权重配置
     * 权重越高表示该行为对推荐分数的影响越大
     * 总权重 = 1.0，确保各维度贡献度的相对平衡
     */
    private static final double VIEW_WEIGHT = 0.08;
    private static final double LIKE_WEIGHT = 0.12;
    private static final double COLLECT_WEIGHT = 0.15;
    private static final double SHARE_WEIGHT = 0.15;
    private static final double DOWNLOAD_WEIGHT = 0.25;
    private static final double COMMENT_WEIGHT = 0.25;   // 评论权重：预留功能

    /**
     * 计算单张图片的推荐分数（含平滑机制）
     *
     * @param picture 待计算的图片对象，包含各种统计数据
     * @param oldScore 图片的旧分数，用于平滑处理和涨幅限制
     * @return 计算后的推荐分数，范围[0, +∞)，实际上限受涨幅限制约束
     */
    public double calculateRecommendScore(Picture picture, double oldScore) {
        if (picture == null) {
            log.warn("图片对象为空，返回默认分数");
            return 0.0;
        }
        try {
            // 获取当前时间
            Date now = TimeUtils.getCurrentBeijingTime();
            // 计算图片发布至今的小时数
            long hoursSincePublish = picture.getCreateTime() == null ? 0 :
                    (now.getTime() - picture.getCreateTime().getTime()) / (60 * 60 * 1000);

            // ========== 1. 行为加权分计算 ==========
            // 使用对数函数log1p来平滑数据，避免某个指标过大导致分数失衡
            // log1p(x) = log(1+x)，当x=0时返回0，避免log(0)的数学错误
            double behaviorScore =
                    VIEW_WEIGHT * log1p(picture.getViewCount())
                            + LIKE_WEIGHT * log1p(picture.getLikeCount())
                            + COLLECT_WEIGHT * log1p(picture.getCollectCount())
                            + SHARE_WEIGHT * log1p(picture.getShareCount())
                            + DOWNLOAD_WEIGHT * log1p(picture.getDownloadCount())
                            + COMMENT_WEIGHT * log1p(0L); // 评论贡献分（功能预留，当前为0）

            // ========== 2. 时间衰减因子计算 ==========
            // 使用指数衰减函数，确保新内容获得更多曝光机会
            // Math.max(0, hoursSincePublish) 确保时间值非负
            double decay = Math.exp(-LAMBDA * Math.max(0, hoursSincePublish));

            // ========== 3. 质量调节因子计算 ==========
            // 基础质量因子为1.0，根据图片的各种属性进行调节
            double qualityFactor = 1.0;

            // 3.1 审核状态加成
            if (picture.getReviewStatus() != null && picture.getReviewStatus() == 1) {
                qualityFactor *= 1.2;
            }

            // 3.2 图片分辨率加成
            if (picture.getPicWidth() != null && picture.getPicHeight() != null) {
                long pixels = (long) picture.getPicWidth() * picture.getPicHeight();
                if (pixels >= 1920 * 1080) {      // 1080p及以上，10%加成
                    qualityFactor *= 1.1;
                } else if (pixels >= 1280 * 720) { // 720p及以上，5%加成
                    qualityFactor *= 1.05;
                }
            }

            // 3.3 文件大小适中加成
            if (picture.getPicSize() != null) {
                long sizeMB = picture.getPicSize() / (1024 * 1024);
                if (sizeMB >= 1 && sizeMB <= 10) {
                    qualityFactor *= 1.05; // 5%加成
                }
            }

            // 限制质量因子的最大值，防止过度加成
            qualityFactor = Math.min(qualityFactor, 2.0);

            // ========== 4. 老图加速衰减机制 ==========
            // 检查最近24小时内是否有用户互动（通过editTime判断）
            boolean hasRecentEngagement = picture.getEditTime() != null &&
                    (now.getTime() - picture.getEditTime().getTime()) / (60 * 60 * 1000) <= 24;

            // 如果图片发布超过24小时且最近没有互动，加速其衰减
            // 这样可以让不受欢迎的老内容更快地退出推荐池
            if (!hasRecentEngagement && hoursSincePublish > 24) {
                decay *= 0.5; // 衰减速度加倍
            }

            // ========== 5. 智能冷启动机制 ==========
            // 为新发布的图片提供初始曝光机会，避免"马太效应"
            double coldStartBoost = 0.0;

            if (hoursSincePublish < 168) { // 只对7天内的图片提供冷启动支持
                if (hoursSincePublish < 48) {
                    // 前48小时：无条件扶持期
                    // 扶持力度随时间线性递减，从50分递减到约35分
                    coldStartBoost = ((168.0 - hoursSincePublish) / 168.0) * 50.0;
                } else {
                    // 48-168小时：条件扶持期
                    // 只有获得早期用户认可（有互动）的图片才继续获得扶持
                    boolean hasEarlyEngagement = (picture.getLikeCount() > 0 ||
                            picture.getCollectCount() > 0 ||
                            picture.getDownloadCount() > 0 ||
                            picture.getShareCount() > 0);

                    if (hasEarlyEngagement) {
                        // 扶持力度降低到30分起，继续线性递减
                        coldStartBoost = ((168.0 - hoursSincePublish) / 168.0) * 30.0;
                    }
                }
            }

            // ========== 6. 计算原始得分 ==========
            // 综合所有因素计算原始推荐分数
            double rawScore = behaviorScore * decay * qualityFactor + coldStartBoost;

            // ========== 7. 动态涨幅限制机制 ==========
            // 防止分数突然暴涨导致排序剧烈变化，保证用户体验的稳定性
            double maxIncrease;
            if (hoursSincePublish < 24) {
                maxIncrease = 10.0;  // 新图片（24小时内）：允许快速上升，最多涨10分
            } else if (hoursSincePublish < 168) {
                maxIncrease = 5.0;   // 较新图片（7天内）：允许适度上升，最多涨5分
            } else {
                maxIncrease = 2.0;   // 老图片（7天后）：限制上升速度，最多涨2分
            }

            // 应用涨幅限制：如果新分数高于旧分数，则限制涨幅；否则允许自由下降
            double finalScore = rawScore > oldScore ?
                    Math.min(oldScore + maxIncrease, rawScore) :  // 上涨时限制涨幅
                    rawScore;                                      // 下降时不限制

            // 确保最终分数非负
            finalScore = Math.max(0.0, finalScore);

            // 记录详细的计算日志，便于调试和优化
            log.debug("图片{}推荐分数计算: 行为分={}, 衰减={}, 质量因子={}, 冷启动={}, 最终分数={}",
                    picture.getId(), behaviorScore, decay, qualityFactor, coldStartBoost, finalScore);

            return finalScore;

        } catch (Exception e) {
            // 异常处理：记录错误日志并返回旧分数，保证系统稳定性
            log.error("计算图片{}推荐分数失败: {}", picture.getId(), e.getMessage(), e);
            return oldScore;
        }
    }

    /**
     * 安全的对数计算方法
     *
     * 使用log1p函数计算log(1+x)，相比直接使用log(x+1)有以下优势：
     * 1. 当x接近0时，计算精度更高
     * 2. 当x=0时，直接返回0，避免log(1)的计算
     * 3. 处理null和负数情况，确保计算安全
     *
     * @param value 待计算的数值，通常是各种计数（浏览数、点赞数等）
     * @return log(1+value)的结果，如果value无效则返回0
     */
    private double log1p(Long value) {
        // 处理无效输入：null或非正数都返回0
        if (value == null || value <= 0) return 0.0;
        // 使用Math.log1p计算，比log(1+x)更精确
        return Math.log1p(value);
    }
}