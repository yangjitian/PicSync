package com.yudi.cloud.service;

import com.yudi.cloud.model.entity.Picture;
import com.yudi.cloud.model.entity.UserPictureAction;
import com.yudi.cloud.mapper.UserPictureActionMapper;
import com.yudi.cloud.service.PictureService;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 数据同步服务
 * 用于确保Redis缓存与数据库数据的一致性
 * 
 * @author yudi
 * @date 2025-01-27
 */
@Service
@Slf4j
public class DataSyncService {

    @Resource
    private UserPictureActionMapper userPictureActionMapper;

    @Resource
    private PictureService pictureService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private JdbcTemplate jdbcTemplate;

    // Redis Key 前缀
    private static final String LIKE_REDIS_PREFIX = "like:";
    private static final String COLLECT_REDIS_PREFIX = "collect:";

    /**
     * 异步同步所有图片的计数数据
     * 在项目启动时调用，确保数据一致性
     */
    @Async
    public void syncAllPictureCounts() {
        try {
            log.info("开始同步所有图片计数数据...");
            
            // 获取所有图片
            List<Picture> allPictures = pictureService.list();
            log.info("📊 找到 {} 张图片，开始同步计数", allPictures.size());
            
            int syncedCount = 0;
            int errorCount = 0;
            
            for (Picture picture : allPictures) {
                try {
                    syncPictureCount(picture.getId());
                    syncedCount++;
                    
                    if (syncedCount % 100 == 0) {
                        log.info("🔄 已同步 {} 张图片的计数数据", syncedCount);
                    }
                } catch (Exception e) {
                    errorCount++;
                    log.error("同步图片 {} 计数失败", picture.getId(), e);
                }
            }
            
            log.info("图片计数同步完成: 成功={}, 失败={}", syncedCount, errorCount);
            
        } catch (Exception e) {
            log.error("同步所有图片计数数据失败", e);
        }
    }

    /**
     * 同步单张图片的计数数据
     */
    public void syncPictureCount(Long pictureId) {
        try {
            // 查询该图片的所有行为记录
            List<UserPictureAction> actions = userPictureActionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserPictureAction>()
                    .eq("picture_id", pictureId)
            );
            
            // 统计各种行为的数量
            long likeCount = actions.stream()
                .filter(action -> "LIKE".equals(action.getActionType()) && action.getStatus() == 1)
                .count();
            
            long collectCount = actions.stream()
                .filter(action -> "COLLECT".equals(action.getActionType()) && action.getStatus() == 1)
                .count();
            
            long shareCount = actions.stream()
                .filter(action -> "SHARE".equals(action.getActionType()) && action.getStatus() == 1)
                .count();
            
            // 更新数据库中的计数
            String updateSql = "UPDATE picture SET likeCount = ?, collectCount = ?, shareCount = ? WHERE id = ?";
            int updatedRows = jdbcTemplate.update(updateSql, likeCount, collectCount, shareCount, pictureId);
            
            if (updatedRows > 0) {
                log.debug("同步图片 {} 计数成功: 点赞={}, 收藏={}, 分享={}", 
                    pictureId, likeCount, collectCount, shareCount);
            } else {
                log.warn("图片 {} 不存在，跳过计数同步", pictureId);
            }
            
        } catch (Exception e) {
            log.error("同步图片 {} 计数失败", pictureId, e);
            throw e;
        }
    }

    /**
     * 清理过期的Redis缓存
     */
    @Async
    public void cleanupExpiredCache() {
        try {
            log.info("🧹 开始清理过期的Redis缓存...");
            
            // 清理过期的点赞缓存
            cleanupExpiredActionCache(LIKE_REDIS_PREFIX, "点赞");
            
            // 清理过期的收藏缓存
            cleanupExpiredActionCache(COLLECT_REDIS_PREFIX, "收藏");
            
            log.info("Redis缓存清理完成");
            
        } catch (Exception e) {
            log.error("清理Redis缓存失败", e);
        }
    }

    /**
     * 清理指定类型的过期行为缓存
     */
    private void cleanupExpiredActionCache(String prefix, String actionName) {
        try {
            // 获取所有匹配的key
            var keys = stringRedisTemplate.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                int cleanedCount = 0;
                for (String key : keys) {
                    try {
                        // 检查key是否过期
                        Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
                        if (ttl != null && ttl <= 0) {
                            stringRedisTemplate.delete(key);
                            cleanedCount++;
                        }
                    } catch (Exception e) {
                        log.warn("清理缓存key失败: {}", key, e);
                    }
                }
                log.info("🧹 清理了 {} 个过期的{}缓存", cleanedCount, actionName);
            }
        } catch (Exception e) {
            log.error("清理{}缓存失败", actionName, e);
        }
    }

    /**
     * 验证数据一致性
     * 检查Redis缓存与数据库数据是否一致
     */
    public void validateDataConsistency() {
        try {
            log.info("🔍 开始验证数据一致性...");
            
            // 获取所有图片
            List<Picture> allPictures = pictureService.list();
            int inconsistentCount = 0;
            
            for (Picture picture : allPictures) {
                try {
                    if (!validatePictureDataConsistency(picture.getId())) {
                        inconsistentCount++;
                        log.warn("图片 {} 数据不一致，需要同步", picture.getId());
                        syncPictureCount(picture.getId());
                    }
                } catch (Exception e) {
                    log.error("验证图片 {} 数据一致性失败", picture.getId(), e);
                }
            }
            
            log.info("数据一致性验证完成: 发现 {} 个不一致项", inconsistentCount);
            
        } catch (Exception e) {
            log.error("验证数据一致性失败", e);
        }
    }

    /**
     * 验证单张图片的数据一致性
     */
    private boolean validatePictureDataConsistency(Long pictureId) {
        try {
            // 获取数据库中的计数
            Picture picture = pictureService.getById(pictureId);
            if (picture == null) {
                return true; // 图片不存在，跳过验证
            }
            
            // 查询实际的行为记录计数
            List<UserPictureAction> actions = userPictureActionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserPictureAction>()
                    .eq("picture_id", pictureId)
            );
            
            long actualLikeCount = actions.stream()
                .filter(action -> "LIKE".equals(action.getActionType()) && action.getStatus() == 1)
                .count();
            
            long actualCollectCount = actions.stream()
                .filter(action -> "COLLECT".equals(action.getActionType()) && action.getStatus() == 1)
                .count();
            
            long actualShareCount = actions.stream()
                .filter(action -> "SHARE".equals(action.getActionType()) && action.getStatus() == 1)
                .count();
            
            // 比较计数是否一致
            boolean likeConsistent = (picture.getLikeCount() == null ? 0L : picture.getLikeCount()) == actualLikeCount;
            boolean collectConsistent = (picture.getCollectCount() == null ? 0L : picture.getCollectCount()) == actualCollectCount;
            boolean shareConsistent = (picture.getShareCount() == null ? 0L : picture.getShareCount()) == actualShareCount;
            
            if (!likeConsistent || !collectConsistent || !shareConsistent) {
                log.warn("图片 {} 计数不一致: 数据库(点赞={}, 收藏={}, 分享={}) vs 实际(点赞={}, 收藏={}, 分享={})",
                    pictureId,
                    picture.getLikeCount(), picture.getCollectCount(), picture.getShareCount(),
                    actualLikeCount, actualCollectCount, actualShareCount);
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("验证图片 {} 数据一致性失败", pictureId, e);
            return false;
        }
    }
}