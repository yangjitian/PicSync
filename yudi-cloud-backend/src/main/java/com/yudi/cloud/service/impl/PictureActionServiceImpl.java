package com.yudi.cloud.service.impl;

import com.yudi.cloud.exception.BusinessException;
import com.yudi.cloud.exception.ErrorCode;
import com.yudi.cloud.exception.ThrowUtils;
import com.yudi.cloud.mapper.UserPictureActionMapper;
import com.yudi.cloud.model.entity.Picture;
import com.yudi.cloud.model.entity.UserPictureAction;
import com.yudi.cloud.model.vo.picture.UserPictureActionStatus;
import com.yudi.cloud.service.PictureActionService;
import com.yudi.cloud.service.PictureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.time.Duration.between;

/**
 * 图片行为服务实现类（浏览、点赞、收藏、分享）
 *
 * @author yudi
 * 2025-09-16
 */
@Service
@Slf4j
public class PictureActionServiceImpl implements PictureActionService {

    @Resource
    private UserPictureActionMapper userPictureActionMapper;

    @Resource
    private PictureService pictureService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private JdbcTemplate jdbcTemplate;

    // Redis Key 前缀，用于构造防重/标记键
    private static final String VIEW_REDIS_PREFIX = "view:";
    private static final String LIKE_REDIS_PREFIX = "like:";
    private static final String COLLECT_REDIS_PREFIX = "collect:";

    // Redis Hash 作为短期缓存的 key（用于缓存图片的 view/share 计数）
    private static final String PICTURE_VIEW_COUNT_CACHE_KEY = "picture:viewCount";
    private static final String PICTURE_SHARE_COUNT_CACHE_KEY = "picture:shareCount";
    private static final String PICTURE_LIKE_COUNT_CACHE_KEY = "picture:likeCount";
    private static final String PICTURE_COLLECT_COUNT_CACHE_KEY = "picture:collectCount";

    // 点赞/收藏等短期缓存时长（小时）
    private static final int ACTION_CACHE_HOURS = 1;

    /**
     * 增加浏览量（一天内防重复，从当天 00:00:00 到 23:59:59）
     * 优化方案：首次浏览INSERT，跨天浏览UPDATE update_time
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> addViewCount(Long pictureId, Long userId) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMETER_ERROR, "图片ID不能为空");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMETER_ERROR, "用户ID不能为空");

        // 以日期分区的 Redis key，保证每天的防重复是隔天重置的
        String today = LocalDateTime.now().toLocalDate().toString();
        String redisKey = VIEW_REDIS_PREFIX + today + ":" + userId + ":" + pictureId;

        // 尝试从短期缓存（Hash）读取 viewCount，若没有则返回 null
        Long cachedViewCount = getCountFromCache(PICTURE_VIEW_COUNT_CACHE_KEY, pictureId);
        boolean success = false; // 标记此次请求是否真正计数成功（有可能只是跳过）

        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(redisKey))) {
            log.info("用户{}今天已浏览过图片{}，跳过计数", userId, pictureId);
        } else if (userPictureActionMapper.checkUserViewedIn24Hours(userId, pictureId)) {
            // 如果 DB 中已有今天的浏览记录（Redis 可能淘汰或丢失），回填 Redis 防重 key 并跳过计数
            long secondsUntilMidnight = getSecondsUntilMidnight();
            stringRedisTemplate.opsForValue().set(redisKey, "1", secondsUntilMidnight, TimeUnit.SECONDS);
            log.info("用户{}今天已浏览过图片{}（DB 记录），已回填 Redis 防重，跳过计数", userId, pictureId);
        } else {
            // 检查用户是否曾经浏览过该图片
            boolean hasViewedBefore = userPictureActionMapper.checkUserViewedBefore(userId, pictureId);

            if (!hasViewedBefore) {
                // 首次浏览：INSERT 新记录 + 浏览量 +1
                UserPictureAction viewAction = new UserPictureAction();
                viewAction.setUserId(userId);
                viewAction.setPictureId(pictureId);
                viewAction.setActionType("VIEW");
                viewAction.setStatus(1);
                viewAction.setCreateTime(new Date());
                viewAction.setUpdateTime(new Date());
                userPictureActionMapper.insert(viewAction);

                updatePictureCount("viewCount", pictureId, 1);
                log.info("用户{}首次浏览图片{}，新增记录并计数+1", userId, pictureId);
            } else {
                // 跨天浏览：UPDATE 现有记录的 update_time + 浏览量 +1
                int updateResult = userPictureActionMapper.updateViewTime(userId, pictureId);
                if (updateResult > 0) {
                    updatePictureCount("viewCount", pictureId, 1);
                    log.info("用户{}跨天浏览图片{}，更新记录时间并计数+1", userId, pictureId);
                } else {
                    log.warn("用户{}跨天浏览图片{}，更新记录失败", userId, pictureId);
                }
            }

            // 计算到第二天 00:00:00 的剩余秒数（用于设定 Redis 键过期）
            long secondsUntilMidnight = getSecondsUntilMidnight();
            // 设置 Redis 防重复键（到午夜过期）
            stringRedisTemplate.opsForValue().set(redisKey, "1", secondsUntilMidnight, TimeUnit.SECONDS);

            success = true; // 此次已经计数成功

            // 若短期缓存存在，则直接在缓存上自增，避免立刻回查 DB
            if (cachedViewCount != null) {
                cachedViewCount = cachedViewCount + 1;
                stringRedisTemplate.opsForHash().put(PICTURE_VIEW_COUNT_CACHE_KEY, pictureId.toString(), String.valueOf(cachedViewCount));
            }
        }

        // 如果缓存中没有计数值，读取 DB 回填缓存（短期缓存，5 分钟）
        if (cachedViewCount == null) {
            Picture picture = pictureService.getById(pictureId);
            cachedViewCount = picture != null ? picture.getViewCount() : 0L;
            stringRedisTemplate.opsForHash().put(PICTURE_VIEW_COUNT_CACHE_KEY, pictureId.toString(), String.valueOf(cachedViewCount));
            stringRedisTemplate.expire(PICTURE_VIEW_COUNT_CACHE_KEY, 5, TimeUnit.MINUTES);
        }

        // 构造返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("viewCount", cachedViewCount);
        log.info("用户{}浏览图片{}结果：{}，当前浏览量：{}", userId, pictureId, success ? "成功" : "跳过", cachedViewCount);
        return result;
    }

    /**
     * 点赞/取消点赞（简化版本，确保数据一致性）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> toggleLikeWithCount(Long pictureId, Long userId) {
        // 参数校验
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMETER_ERROR, "图片ID不能为空");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMETER_ERROR, "用户ID不能为空");

        // 查询图片是否存在
        Picture picture = pictureService.getById(pictureId);
        if (picture == null) {
            throw new BusinessException(ErrorCode.CANNOT_FOUND_DATA_ERROR, "图片不存在");
        }

        // 查询用户当前的点赞状态
        UserPictureAction existingAction = userPictureActionMapper.selectByUserIdAndPictureIdAndActionType(userId, pictureId, "LIKE");
        boolean isLiked = existingAction != null && existingAction.getStatus() == 1;

        // 计算新的点赞状态
        boolean newLikedState = !isLiked;
        long newLikeCount;

        if (existingAction != null) {
            // 已存在记录：切换状态
            existingAction.setStatus(newLikedState ? 1 : 0);
            existingAction.setUpdateTime(new Date());
            userPictureActionMapper.updateById(existingAction);

            // 更新点赞数
            if (newLikedState) {
                picture.setLikeCount(picture.getLikeCount() + 1);
            } else if (picture.getLikeCount() > 0) {
                picture.setLikeCount(picture.getLikeCount() - 1);
            }
        } else {
            // 不存在记录：新增点赞
            UserPictureAction newAction = new UserPictureAction();
            newAction.setUserId(userId);
            newAction.setPictureId(pictureId);
            newAction.setActionType("LIKE");
            newAction.setStatus(1);
            newAction.setCreateTime(new Date());
            newAction.setUpdateTime(new Date());
            userPictureActionMapper.insert(newAction);

            // 增加点赞数
            picture.setLikeCount(picture.getLikeCount() + 1);
        }

        // 保存更新后的图片信息
        pictureService.updateById(picture);
        newLikeCount = picture.getLikeCount();

        // 清理缓存
        stringRedisTemplate.delete(LIKE_REDIS_PREFIX + userId + ":" + pictureId);
        stringRedisTemplate.delete(PICTURE_LIKE_COUNT_CACHE_KEY);

        // 返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("liked", newLikedState);
        result.put("likeCount", newLikeCount);

        log.info("用户{}点赞图片{}结果：{}，当前点赞数：{}",
                userId, pictureId, newLikedState ? "成功" : "取消", newLikeCount);

        return result;
    }

    /**
     * 收藏/取消收藏（简化版本，确保数据一致性）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> toggleCollectWithCount(Long pictureId, Long userId) {
        // 参数校验
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMETER_ERROR, "图片ID不能为空");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMETER_ERROR, "用户ID不能为空");

        // 查询图片是否存在
        Picture picture = pictureService.getById(pictureId);
        if (picture == null) {
            throw new BusinessException(ErrorCode.CANNOT_FOUND_DATA_ERROR, "图片不存在");
        }

        // 查询用户当前的收藏状态
        UserPictureAction existingAction = userPictureActionMapper.selectByUserIdAndPictureIdAndActionType(userId, pictureId, "COLLECT");
        boolean isCollected = existingAction != null && existingAction.getStatus() == 1;

        // 计算新的收藏状态
        boolean newCollectedState = !isCollected;
        long newCollectCount;

        if (existingAction != null) {
            // 已存在记录：切换状态
            existingAction.setStatus(newCollectedState ? 1 : 0);
            existingAction.setUpdateTime(new Date());
            userPictureActionMapper.updateById(existingAction);

            // 更新收藏数
            if (newCollectedState) {
                picture.setCollectCount(picture.getCollectCount() + 1);
            } else if (picture.getCollectCount() > 0) {
                picture.setCollectCount(picture.getCollectCount() - 1);
            }
        } else {
            // 不存在记录：新增收藏
            UserPictureAction newAction = new UserPictureAction();
            newAction.setUserId(userId);
            newAction.setPictureId(pictureId);
            newAction.setActionType("COLLECT");
            newAction.setStatus(1);
            newAction.setCreateTime(new Date());
            newAction.setUpdateTime(new Date());
            userPictureActionMapper.insert(newAction);

            // 增加收藏数
            picture.setCollectCount(picture.getCollectCount() + 1);
        }

        // 保存更新后的图片信息
        pictureService.updateById(picture);
        newCollectCount = picture.getCollectCount();

        // 清理缓存
        stringRedisTemplate.delete(COLLECT_REDIS_PREFIX + userId + ":" + pictureId);
        stringRedisTemplate.delete(PICTURE_COLLECT_COUNT_CACHE_KEY);

        // 返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("collected", newCollectedState);
        result.put("collectCount", newCollectCount);

        log.info("用户{}收藏图片{}结果：{}，当前收藏数：{}",
                userId, pictureId, newCollectedState ? "成功" : "取消", newCollectCount);

        return result;
    }

    // 辅助方法：首字母大写（用于反射调用getter）
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     *  优化：直接获取当前计数，避免反射调用
     */
    private long getCurrentCount(Picture picture, String countField) {
        if (picture == null) return 0L;

        switch (countField) {
            case "likeCount":
                return picture.getLikeCount() != null ? picture.getLikeCount() : 0L;
            case "collectCount":
                return picture.getCollectCount() != null ? picture.getCollectCount() : 0L;
            case "viewCount":
                return picture.getViewCount() != null ? picture.getViewCount() : 0L;
            case "shareCount":
                return picture.getShareCount() != null ? picture.getShareCount() : 0L;
            default:
                return 0L;
        }
    }

    /**
     *  优化：原子更新图片计数，确保数据一致性（高性能版本）
     */
    private void updatePictureCountAtomic(String column, Long pictureId, int delta) {
        try {
            //  优化：使用更高效的SQL语句，减少数据库负载
            String sql = delta > 0
                    ? "UPDATE picture SET " + column + " = " + column + " + 1 WHERE id = ?"
                    : "UPDATE picture SET " + column + " = GREATEST(" + column + " - 1, 0) WHERE id = ?";

            //  优化：使用批量更新，减少数据库往返
            int updatedRows = jdbcTemplate.update(sql, pictureId);
            if (updatedRows == 0) {
                log.warn("原子更新图片{}的{}失败，可能图片不存在", pictureId, column);
                throw new BusinessException(ErrorCode.CANNOT_FOUND_DATA_ERROR, "图片不存在");
            }

            //  优化：减少日志输出，提高性能
            if (log.isDebugEnabled()) {
                log.debug("原子更新图片{}的{}成功，delta={}", pictureId, column, delta);
            }
        } catch (Exception e) {
            log.error("原子更新图片计数失败: pictureId={}, column={}, delta={}", pictureId, column, delta, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新计数失败");
        }
    }

    /**
     * 增加分享数（用户可无限分享，但图片分享数只增加一次）
     * 优化：首次分享INSERT，重复分享UPDATE update_time
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> addShareCount(Long pictureId, Long userId) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMETER_ERROR, "图片ID不能为空");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMETER_ERROR, "用户ID不能为空");

        // 检查该图片是否已经被该用户分享过（用于判断是否需要增加分享数）
        boolean hasSharedBefore = userPictureActionMapper.checkUserSharedBefore(userId, pictureId);

        // 从短期缓存读取分享数（若有）
        Long cachedShareCount = getCountFromCache(PICTURE_SHARE_COUNT_CACHE_KEY, pictureId);
        boolean success = true; // 分享操作总是成功
        boolean countIncreased = false; // 标记分享数是否增加

        if (!hasSharedBefore) {
            // 首次分享：INSERT 新记录
            UserPictureAction newAction = new UserPictureAction();
            newAction.setUserId(userId);
            newAction.setPictureId(pictureId);
            newAction.setActionType("SHARE");
            newAction.setStatus(1);
            newAction.setCreateTime(new Date());
            newAction.setUpdateTime(new Date());
            userPictureActionMapper.insert(newAction);

            // 增加图片的分享数
            updatePictureCount("shareCount", pictureId, 1); // 原子更新 shareCount
            countIncreased = true;
            log.info("用户{}首次分享图片{}，分享数+1", userId, pictureId);

            // 如果短期缓存存在则 +1
            if (cachedShareCount != null) {
                cachedShareCount = cachedShareCount + 1;
                stringRedisTemplate.opsForHash().put(PICTURE_SHARE_COUNT_CACHE_KEY, pictureId.toString(), String.valueOf(cachedShareCount));
            }
        } else {
            // 重复分享：UPDATE 现有记录的 update_time
            int updateResult = userPictureActionMapper.updateShareTime(userId, pictureId);
            if (updateResult > 0) {
                log.info("用户{}再次分享图片{}，更新分享时间", userId, pictureId);
            } else {
                log.warn("用户{}分享图片{}失败，更新记录时未找到对应记录", userId, pictureId);
            }
        }

        // 如果短期缓存不存在，则从 DB 读取并回填短期缓存（5 分钟）
        if (cachedShareCount == null) {
            Picture picture = pictureService.getById(pictureId);
            cachedShareCount = picture != null ? picture.getShareCount() : 0L;
            stringRedisTemplate.opsForHash().put(PICTURE_SHARE_COUNT_CACHE_KEY, pictureId.toString(), String.valueOf(cachedShareCount));
            stringRedisTemplate.expire(PICTURE_SHARE_COUNT_CACHE_KEY, 5, TimeUnit.MINUTES);
        }

        // 返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("shareCount", cachedShareCount);
        result.put("countIncreased", countIncreased); // 新增：是否增加了分享数
        log.info("用户{}分享图片{}结果：成功，当前分享数：{}，分享数是否增加：{}", userId, pictureId, cachedShareCount, countIncreased);
        return result;
    }

    /**
     * 获取用户对图片的行为状态（是否点赞/是否收藏）
     */
    @Override
    public UserPictureActionStatus getUserActionStatus(Long pictureId, Long userId) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMETER_ERROR, "图片ID不能为空");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMETER_ERROR, "用户ID不能为空");

        // 初始化默认状态（全部 false）
        UserPictureActionStatus status = new UserPictureActionStatus(pictureId, userId, false, false, false, null, null, null);

        // 查询点赞记录并设定状态（存在且 status==1 表示已点赞）
        UserPictureAction likeAction = userPictureActionMapper.selectByUserIdAndPictureIdAndActionType(userId, pictureId, "LIKE");
        status.setLiked(likeAction != null && likeAction.getStatus() == 1);

        // 查询收藏记录并设定状态（存在且 status==1 表示已收藏）
        UserPictureAction collectAction = userPictureActionMapper.selectByUserIdAndPictureIdAndActionType(userId, pictureId, "COLLECT");
        status.setCollected(collectAction != null && collectAction.getStatus() == 1);

        return status; // 返回状态给调用方
    }

    /**
     * 批量获取用户对多张图片的行为状态（避免多次单独查询）
     */
    @Override
    public Map<Long, UserPictureActionStatus> batchGetUserActionStatus(List<Long> pictureIds, Long userId) {
        // 参数校验：图片列表不能为空
        ThrowUtils.throwIf(pictureIds == null || pictureIds.isEmpty(), ErrorCode.PARAMETER_ERROR, "图片ID列表不能为空");
        // 参数校验：用户 ID 合法性
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMETER_ERROR, "用户ID不能为空");

        // 结果映射：先初始化每张图片的默认状态
        Map<Long, UserPictureActionStatus> result = new HashMap<>();
        for (Long pid : pictureIds) {
            result.put(pid, new UserPictureActionStatus(pid, userId, false, false, false, null, null, null));
        }

        // 一次性从 DB 查询用户在这些图片上的所有行为（提高性能）
        List<UserPictureAction> actions = userPictureActionMapper.selectUserActionsByPictureIds(userId, pictureIds);
        for (UserPictureAction action : actions) {
            Long pid = action.getPictureId(); // 获取图片 ID
            UserPictureActionStatus stat = result.get(pid); // 获取结果对象引用
            if (stat != null) {
                // 根据 actionType 设置状态（仅当 status == 1 时才算激活）
                if ("LIKE".equals(action.getActionType()) && action.getStatus() == 1) {
                    stat.setLiked(true);
                } else if ("COLLECT".equals(action.getActionType()) && action.getStatus() == 1) {
                    stat.setCollected(true);
                }
            }
        }

        return result; // 返回全部图片的状态映射
    }

    /**
     * 原子更新图片计数字段（支持 view/like/collect/share）
     * - delta > 0 时使用 +1；delta <= 0 时使用 GREATEST(..., 0) 避免计数变负。
     */
    private void updatePictureCount(String column, Long pictureId, int delta) {
        String sql = delta > 0
                ? "UPDATE picture SET " + column + " = " + column + " + 1 WHERE id = ?"
                : "UPDATE picture SET " + column + " = GREATEST(" + column + " - 1, 0) WHERE id = ?";
        int updatedRows = jdbcTemplate.update(sql, pictureId);
        if (updatedRows == 0) {
            // 如果受影响行数为 0，可能是图片不存在或 ID 错误
            log.warn("更新图片{}的{}失败，可能图片不存在", pictureId, column);
        }
    }

    /**
     * 计算到明天 00:00:00 的剩余秒数（用于浏览防重键过期）
     */
    private long getSecondsUntilMidnight() {
        LocalDateTime now = LocalDateTime.now(); // 当前时间
        LocalDateTime nextDayStart = now.toLocalDate().plusDays(1).atStartOfDay(); // 明天 00:00:00
        return between(now, nextDayStart).getSeconds(); // 计算秒数差并返回
    }

    /**
     * 从 Redis Hash 中获取计数（若无返回 null）
     *
     * @param cacheKey   Redis Hash 的 key
     * @param pictureId  图片 ID（作为 hash 的 field）
     * @return 如果缓存存在返回 Long 值，否则返回 null
     */
    private Long getCountFromCache(String cacheKey, Long pictureId) {
        String val = (String) stringRedisTemplate.opsForHash().get(cacheKey, pictureId.toString()); // 读取 hash field
        return val != null ? Long.parseLong(val) : null; // 若存在则解析为 Long，否则返回 null
    }
}