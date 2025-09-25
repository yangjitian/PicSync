package com.yudi.cloud.manager.recommend;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yudi.cloud.exception.ErrorCode;
import com.yudi.cloud.exception.ThrowUtils;
import com.yudi.cloud.mapper.PictureMapper;
import com.yudi.cloud.model.entity.Picture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis排名用于快速查询
 */
@Service
@Slf4j
public class RedisRankServiceImpl {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private PictureMapper pictureMapper;

    private static final String RANK_KEY = "picture:recommend:rank";
    private static final long CACHE_EXPIRE_SECONDS = 3600; // 1小时
    private static final int MAX_CACHE_SIZE = 100; // 最大缓存数量

    /**
     * 增量更新单张图片分数
     */
    public void updatePictureScore(Long pictureId, double score) {
        ThrowUtils.throwIf(pictureId == null, ErrorCode.PARAMETER_ERROR, "图片无效");

        try {
            ZSetOperations<String, Object> zSet = redisTemplate.opsForZSet();
            // 添加新分数（ZADD会自动覆盖旧分数）
            zSet.add(RANK_KEY, pictureId, score);
            
            // 修剪到 Top 100
            Long size = zSet.size(RANK_KEY);
            if (size != null && size > MAX_CACHE_SIZE) {
                zSet.removeRange(RANK_KEY, 0, size - MAX_CACHE_SIZE - 1);
            }
            
            // 设置过期时间（修复：使用秒而不是天）
            redisTemplate.expire(RANK_KEY, Duration.ofSeconds(CACHE_EXPIRE_SECONDS));
            
            // 清除排序结果缓存，因为推荐分数发生了变化
            redisTemplate.delete(ALL_PICTURE_IDS_CACHE_KEY);
            log.debug("更新图片{}推荐分数: {}，已清除排序缓存", pictureId, score);
        } catch (Exception e) {
            log.error("更新图片{}推荐分数失败: {}", pictureId, e.getMessage(), e);
        }
    }

    // 添加缓存键和过期时间
    private static final String ALL_PICTURE_IDS_CACHE_KEY = "picture:all:ids:cache";
    private static final long ALL_PICTURE_IDS_CACHE_EXPIRE_SECONDS = 300; // 5分钟缓存

    /**
     * 获取所有图片ID列表（用于瀑布流）
     * 混合排序：推荐图片在前，其他图片在后
     */
    public List<Long> getAllRecommendPictureIds() {
        try {
            // 1. 先尝试从缓存获取完整排序结果
            String cacheKey = ALL_PICTURE_IDS_CACHE_KEY;
            @SuppressWarnings("unchecked")
            List<Long> cachedResult = (List<Long>) redisTemplate.opsForValue().get(cacheKey);
            if (cachedResult != null && !cachedResult.isEmpty()) {
                log.info("从缓存获取所有图片ID列表: 共{}张", cachedResult.size());
                return cachedResult;
            }

            // 2. 获取所有推荐图片ID（有分数的）
            ZSetOperations<String, Object> zSet = redisTemplate.opsForZSet();
            Set<Object> recommendRange = zSet.reverseRange(RANK_KEY, 0, -1);
            Set<Long> recommendIds;
            if (recommendRange != null && !recommendRange.isEmpty()) {
                recommendIds = recommendRange.stream()
                        .map(id -> (Long) id)
                        .collect(Collectors.toSet());
                log.info("从Redis获取推荐图片ID: 共{}张", recommendIds.size());
            } else {
                recommendIds = new HashSet<>();
            }

            // 3. 获取所有图片数据（包含完整信息用于多级排序）
            QueryWrapper<Picture> wrapper = new QueryWrapper<>();
            wrapper.eq("isDelete", 0)
                   .eq("reviewStatus", 1);

            List<Picture> allPictures = pictureMapper.selectList(wrapper);
            log.info("从MySQL获取所有图片: 共{}张", allPictures.size());

            // 3. 实现多级排序：推荐分 → 收藏数 → 点赞数 → 浏览量 → 发布时间
            // 使用LinkedHashSet确保去重且保持顺序
            List<Long> result = allPictures.stream()
                    .sorted((pic1, pic2) -> {
                        // 1. 推荐分数比较（有推荐分的在前，按分数降序）
                        boolean pic1HasScore = recommendIds.contains(pic1.getId());
                        boolean pic2HasScore = recommendIds.contains(pic2.getId());

                        // 只有一方有推荐分时的处理
                        if (pic1HasScore ^ pic2HasScore) { // 使用异或判断是否只有一个为true
                            return pic1HasScore ? -1 : 1;
                        }

                        // 双方都有推荐分的情况
                        if (pic1HasScore) {
                            double score1 = Optional.ofNullable(pic1.getRecommendScore()).orElse(0.0);
                            double score2 = Optional.ofNullable(pic2.getRecommendScore()).orElse(0.0);
                            // 降序排列
                            int scoreCompare = Double.compare(score2, score1);
                            if (scoreCompare != 0) {
                                return scoreCompare;
                            }
                        }

                        // 2. 收藏数比数
                        long collect1 = Optional.ofNullable(pic1.getCollectCount()).orElse(0L);
                        long collect2 = Optional.ofNullable(pic2.getCollectCount()).orElse(0L);
                        int collectCompare = Long.compare(collect2, collect1);
                        if (collectCompare != 0) {
                            return collectCompare;
                        }

                        // 3. 点赞数比较
                        long like1 = Optional.ofNullable(pic1.getLikeCount()).orElse(0L);
                        long like2 = Optional.ofNullable(pic2.getLikeCount()).orElse(0L);
                        int likeCompare = Long.compare(like2, like1);
                        if (likeCompare != 0) {
                            return likeCompare;
                        }

                        // 4. 浏览量比较
                        long view1 = Optional.ofNullable(pic1.getViewCount()).orElse(0L);
                        long view2 = Optional.ofNullable(pic2.getViewCount()).orElse(0L);
                        int viewCompare = Long.compare(view2, view1);
                        if (viewCompare != 0) {
                            return viewCompare;
                        }

                        // 5. 发布时间比较（降序，新的在前）
                        Date time1 = pic1.getCreateTime();
                        Date time2 = pic2.getCreateTime();
                        if (time1 != null && time2 != null) {
                            return time2.compareTo(time1);
                        } else if (time1 != null) {
                            return -1;
                        } else if (time2 != null) {
                            return 1;
                        }

                        return 0;
                    })
                    .map(Picture::getId)
                    .distinct() // 确保去重
                    .collect(Collectors.toList());


            long recommendCount = result.stream().filter(recommendIds::contains).count();
            log.info("多级排序完成: 推荐图片{}张 + 其他图片{}张 = 总计{}张", 
                    recommendCount, result.size() - recommendCount, result.size());
            
            // 4. 缓存排序结果
            redisTemplate.opsForValue().set(cacheKey, result, Duration.ofSeconds(ALL_PICTURE_IDS_CACHE_EXPIRE_SECONDS));
            log.info("已缓存所有图片ID排序结果，过期时间: {}秒", ALL_PICTURE_IDS_CACHE_EXPIRE_SECONDS);
            
            return result;
            
        } catch (Exception e) {
            log.error("获取所有图片ID失败: {}", e.getMessage(), e);
            // 降级：直接返回所有图片
            return getAllPictureIdsFromMySQL();
        }
    }



    /**
     * MySQL降级：获取所有图片ID列表（按创建时间排序）
     */
    private List<Long> getAllPictureIdsFromMySQL() {
        try {
            QueryWrapper<Picture> wrapper = new QueryWrapper<>();
            wrapper.select("id")
                   .eq("isDelete", 0)
                   .eq("reviewStatus", 1) // 只获取审核通过的图片
                   .orderByDesc("createTime"); // 按创建时间降序

            List<Picture> pictures = pictureMapper.selectList(wrapper);
            List<Long> result = pictures.stream()
                    .map(Picture::getId)
                    .distinct() // 确保去重
                    .collect(Collectors.toList());

            log.info("从MySQL获取所有图片ID列表: 共{}张图片", result.size());
            return result;
        } catch (Exception e) {
            log.error("从MySQL获取所有图片失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 批量更新图片分数
     */
    public void batchUpdatePictureScores(List<Long> pictureIds, List<Double> scores) {
        if (pictureIds == null || scores == null || pictureIds.size() != scores.size()) {
            log.warn("批量更新参数无效");
            return;
        }

        try {
            ZSetOperations<String, Object> zSet = redisTemplate.opsForZSet();
            
            for (int i = 0; i < pictureIds.size(); i++) {
                Long pictureId = pictureIds.get(i);
                Double score = scores.get(i);
                if (pictureId != null && pictureId > 0 && score != null) {
                    zSet.add(RANK_KEY, pictureId, score);
                }
            }
            
            // 修剪到最大缓存数量
            Long size = zSet.size(RANK_KEY);
            if (size != null && size > MAX_CACHE_SIZE) {
                zSet.removeRange(RANK_KEY, 0, size - MAX_CACHE_SIZE - 1);
            }
            
            // 设置过期时间
            redisTemplate.expire(RANK_KEY, Duration.ofSeconds(CACHE_EXPIRE_SECONDS));
            
            // 清除排序结果缓存，因为推荐分数发生了变化
            redisTemplate.delete(ALL_PICTURE_IDS_CACHE_KEY);
            
            log.info("批量更新推荐分数完成: 数量={}，已清除排序缓存", pictureIds.size());
        } catch (Exception e) {
            log.error("批量更新推荐分数失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 从推荐排名中移除指定图片
     * 用于图片删除时清理缓存
     *
     * @param pictureId 图片ID
     */
    public void removePictureFromRank(Long pictureId) {
        if (pictureId == null || pictureId <= 0) {
            log.warn("图片ID无效，跳过移除: {}", pictureId);
            return;
        }

        try {
            ZSetOperations<String, Object> zSet = redisTemplate.opsForZSet();
            
            // 从Redis ZSet中移除指定图片ID
            Long removedCount = zSet.remove(RANK_KEY, pictureId);
            
            // 清除排序结果缓存，因为图片被删除了
            redisTemplate.delete(ALL_PICTURE_IDS_CACHE_KEY);
            
            if (removedCount != null && removedCount > 0) {
                log.info("已从推荐排名中移除图片{}，已清除排序缓存", pictureId);
            } else {
                log.debug("图片{}不在推荐排名中，已清除排序缓存", pictureId);
            }
        } catch (Exception e) {
            log.error("从推荐排名中移除图片{}失败: {}", pictureId, e.getMessage(), e);
        }
    }
}