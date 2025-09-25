package com.yudi.cloud.manager.recommend;

import com.yudi.cloud.mapper.PictureMapper;
import com.yudi.cloud.model.entity.Picture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 实时推荐分数计算服务
 * 用于在图片上传后立即计算推荐分数，避免等待定时任务
 * 
 * @author yudi
 * @since 1.0.0
 */
@Service
@Slf4j
public class RealtimeRecommendationServiceImpl {

    @Resource
    private PictureMapper pictureMapper;
    
    @Resource
    private PictureRecommendationServiceImpl pictureRecommendationServiceImpl;
    
    @Resource
    private RedisRankServiceImpl redisRankServiceImpl;

    /**
     * 异步计算并更新单张图片的推荐分数
     * 
     * @param pictureId 图片ID
     * @return CompletableFuture<Boolean> 计算是否成功
     */
    @Async("recommendationTaskExecutor")
    @Transactional(rollbackFor = Exception.class)
    public CompletableFuture<Boolean>  calculateAndUpdateRecommendScore(Long pictureId) {
        log.info("开始实时计算图片{}的推荐分数", pictureId);
        
        try {
            // 1. 从数据库获取图片信息
            Picture picture = pictureMapper.selectById(pictureId);
            if (picture == null) {
                log.warn("图片{}不存在，跳过推荐分数计算", pictureId);
                return CompletableFuture.completedFuture(false);
            }
            
            // 2. 检查图片状态
            if (picture.getIsDelete() == 1) {
                log.warn("图片{}已删除，跳过推荐分数计算", pictureId);
                return CompletableFuture.completedFuture(false);
            }
            
            // 3. 计算推荐分数
            double oldScore = picture.getRecommendScore() != null ? picture.getRecommendScore() : 0.0;
            double newScore = pictureRecommendationServiceImpl.calculateRecommendScore(picture, oldScore);
            
            // 4. 更新数据库
            picture.setRecommendScore(newScore);
            picture.setScoreUpdatedAt(com.yudi.cloud.utils.TimeUtils.getCurrentBeijingTime());
            int updateResult = pictureMapper.updateById(picture);
            
            if (updateResult > 0) {
                // 5. 更新Redis缓存
                redisRankServiceImpl.updatePictureScore(pictureId, newScore);
                
                log.info("图片{}推荐分数计算完成: {} -> {}, Redis同步成功", 
                        pictureId, oldScore, newScore);
                return CompletableFuture.completedFuture(true);
            } else {
                log.error("图片{}推荐分数更新失败", pictureId);
                return CompletableFuture.completedFuture(false);
            }
            
        } catch (Exception e) {
            log.error("图片{}实时推荐分数计算失败: {}", pictureId, e.getMessage(), e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * 批量计算并更新多张图片的推荐分数
     *
     * @param pictureIds 图片ID列表
     */
    @Async("recommendationTaskExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void batchCalculateAndUpdateRecommendScores(List<Long> pictureIds) {
        log.info("开始批量实时计算{}张图片的推荐分数", pictureIds.size());
        
        int successCount = 0;
        
        try {
            for (Long pictureId : pictureIds) {
                try {
                    CompletableFuture<Boolean> result = calculateAndUpdateRecommendScore(pictureId);
                    if (result.get()) { // 同步等待结果
                        successCount++;
                    }
                } catch (Exception e) {
                    log.error("批量计算图片{}推荐分数失败: {}", pictureId, e.getMessage());
                }
            }
            
            log.info("批量实时推荐分数计算完成: 成功{}张，失败{}张", 
                    successCount, pictureIds.size() - successCount);
            
        } catch (Exception e) {
            log.error("批量实时推荐分数计算失败: {}", e.getMessage(), e);
        }

    }

}