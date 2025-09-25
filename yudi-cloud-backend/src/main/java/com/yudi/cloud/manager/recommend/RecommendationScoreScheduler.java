package com.yudi.cloud.manager.recommend;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yudi.cloud.mapper.PictureMapper;
import com.yudi.cloud.model.entity.Picture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.yudi.cloud.utils.TimeUtils;

/**
 * 推荐分数调度器（定期更新图片分数）
 */
@Component
@Slf4j
public class RecommendationScoreScheduler {

    @Resource
    private PictureMapper pictureMapper;
    @Resource
    private PictureRecommendationServiceImpl recommendationServiceImpl;
    @Resource
    private RedisRankServiceImpl redisRankServiceImpl;

    private static final double SCORE_CHANGE_THRESHOLD = 0.01; // 分数变化阈值
    private static final int BATCH_SIZE = 500; // 批量处理大小

    /**
     * 每 1小时更新一次推荐分数（与Redis缓存过期时间保持一致）
     */
    @Scheduled(fixedRate = 60 * 60 * 1000)
    @Transactional(rollbackFor = Exception.class)
    public void updateRecommendScores() {
        log.info("开始执行推荐分数更新任务");
        
        try {
            // 1. 获取需要更新的图片（最近一小时有变化的图片）
            List<Picture> picturesToUpdate = getPicturesToUpdate();
            log.info("找到{}张图片需要更新推荐分数", picturesToUpdate.size());

            if (picturesToUpdate.isEmpty()) {
                log.info("没有图片需要更新，任务结束");
                return;
            }

            // 2. 批量处理
            List<Long> updatedPictureIds = new ArrayList<>();
            List<Double> updatedScores = new ArrayList<>();
            int updateCount = 0;

            for (Picture picture : picturesToUpdate) {
                try {
                    double oldScore = picture.getRecommendScore() != null ? picture.getRecommendScore() : 0.0;
                    double newScore = recommendationServiceImpl.calculateRecommendScore(picture, oldScore);

                    // 只有分数变化超过阈值才更新
                    if (Math.abs(newScore - oldScore) > SCORE_CHANGE_THRESHOLD) {
                        picture.setRecommendScore(newScore);
                        picture.setScoreUpdatedAt(TimeUtils.getCurrentBeijingTime());
                        pictureMapper.updateById(picture);

                        // 收集批量更新数据
                        updatedPictureIds.add(picture.getId());
                        updatedScores.add(newScore);
                        updateCount++;

                        log.debug("更新图片{}推荐分数: {} -> {}", picture.getId(), oldScore, newScore);
                    }
                } catch (Exception e) {
                    log.error("更新图片{}推荐分数失败: {}", picture.getId(), e.getMessage(), e);
                }
            }

            // 3. 批量更新Redis缓存
            if (!updatedPictureIds.isEmpty()) {
                redisRankServiceImpl.batchUpdatePictureScores(updatedPictureIds, updatedScores);
                log.info("推荐分数更新完成: 更新{}张图片，批量同步Redis", updateCount);
            } else {
                log.info("推荐分数更新完成: 没有图片分数发生变化");
            }

        } catch (Exception e) {
            log.error("推荐分数更新任务执行失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取需要更新推荐分数的图片
     */
    private List<Picture> getPicturesToUpdate() {
        try {
            // 计算一小时前的时间点，使用北京时间
            LocalDateTime oneHourAgo = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusHours(1);
            Date cutoffTime = Date.from(oneHourAgo.atZone(ZoneId.of("Asia/Shanghai")).toInstant());

            QueryWrapper<Picture> wrapper = new QueryWrapper<>();
            wrapper.eq("isDelete", 0)
                   .eq("reviewStatus", 1) // 只处理审核通过的图片
                   .and(w -> w.ge("editTime", cutoffTime)
                           .or()
                           .lt("scoreUpdatedAt", cutoffTime))
                   .orderByDesc("editTime")
                   .last("LIMIT " + BATCH_SIZE * 2); // 限制查询数量，避免内存溢出

            List<Picture> pictures = pictureMapper.selectList(wrapper);
            log.debug("查询到{}张图片需要检查更新，截止时间: {}", pictures.size(), cutoffTime);
            return pictures;

        } catch (Exception e) {
            log.error("查询需要更新的图片失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 手动触发全量更新（用于系统初始化或紧急修复）
     */
    @Transactional(rollbackFor = Exception.class)
    public void fullUpdateRecommendScores() {
        log.info("开始执行全量推荐分数更新任务");
        
        try {
            QueryWrapper<Picture> wrapper = new QueryWrapper<>();
            wrapper.eq("isDelete", 0)
                   .eq("reviewStatus", 1)
                   .orderByDesc("createTime");

            List<Picture> allPictures = pictureMapper.selectList(wrapper);
            log.info("找到{}张图片需要全量更新推荐分数", allPictures.size());

            List<Long> updatedPictureIds = new ArrayList<>();
            List<Double> updatedScores = new ArrayList<>();
            int updateCount = 0;

            for (Picture picture : allPictures) {
                try {
                    double oldScore = picture.getRecommendScore() != null ? picture.getRecommendScore() : 0.0;
                    double newScore = recommendationServiceImpl.calculateRecommendScore(picture, oldScore);

                    picture.setRecommendScore(newScore);
                    picture.setScoreUpdatedAt(TimeUtils.getCurrentBeijingTime());
                    pictureMapper.updateById(picture);

                    updatedPictureIds.add(picture.getId());
                    updatedScores.add(newScore);
                    updateCount++;

                    // 每处理100张图片就批量更新一次Redis
                    if (updatedPictureIds.size() >= BATCH_SIZE) {
                        redisRankServiceImpl.batchUpdatePictureScores(updatedPictureIds, updatedScores);
                        updatedPictureIds.clear();
                        updatedScores.clear();
                        log.info("已处理{}张图片", updateCount);
                    }
                } catch (Exception e) {
                    log.error("更新图片{}推荐分数失败: {}", picture.getId(), e.getMessage(), e);
                }
            }

            // 处理剩余的图片
            if (!updatedPictureIds.isEmpty()) {
                redisRankServiceImpl.batchUpdatePictureScores(updatedPictureIds, updatedScores);
            }

            log.info("全量推荐分数更新完成: 共更新{}张图片", updateCount);

        } catch (Exception e) {
            log.error("全量推荐分数更新任务执行失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 每天凌晨2点执行全量更新（可选）
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyFullUpdate() {
        log.info("开始执行每日全量推荐分数更新");
        fullUpdateRecommendScores();
    }
    // 初始化计算图片推荐分
//    @PostConstruct
//    public void initRecommendScores() {
//        log.info("项目启动，开始初始化推荐分数...");
//        fullUpdateRecommendScores();
//    }
}