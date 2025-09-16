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
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

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
     * 点赞/取消点赞（公共入口，调用 toggleAction）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleLike(Long pictureId, Long userId) {
        // 传入 actionType="LIKE"、对应的 Redis 前缀、以及计数更新函数
        return toggleAction(pictureId, userId, "LIKE", LIKE_REDIS_PREFIX,
                (pid, delta) -> updatePictureCount("likeCount", pid, delta));
    }

    /**
     * 收藏/取消收藏（公共入口，调用 toggleAction）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleCollect(Long pictureId, Long userId) {
        return toggleAction(pictureId, userId, "COLLECT", COLLECT_REDIS_PREFIX,
                (pid, delta) -> updatePictureCount("collectCount", pid, delta));
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

    // ==================== 核心工具方法（保留复用） ====================

    /**
     * 通用行为切换方法（用于点赞/收藏）
     *
     * @param pictureId   图片ID
     * @param userId      用户ID
     * @param actionType  行为类型（"LIKE" / "COLLECT"）
     * @param redisPrefix Redis 前缀（例如 "like:"）
     * @param countUpdater 计数更新器：接收(pictureId, delta) 并更新数据库计数
     * @return 是否处于激活状态（true 表示已点赞或已收藏）
     */
    private boolean toggleAction(Long pictureId, Long userId, String actionType,
                                 String redisPrefix, BiConsumer<Long, Integer> countUpdater) {
        // 构造分布式锁 key（区分不同类型的操作）
        String lockKey = redisPrefix + "_lock:" + pictureId + ":" + userId;
        String lockValue = UUID.randomUUID().toString(); // 随机值，确保锁唯一性

        try {
            // 尝试获取锁，超时为 5 秒；setIfAbsent 返回 Boolean（可能为 null）
            Boolean lockAcquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, Duration.ofSeconds(5));
            // 获取锁失败抛异常
            if (!Boolean.TRUE.equals(lockAcquired)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "操作过于频繁，请稍后再试");
            }

            // 校验图片是否存在
            Picture picture = pictureService.getById(pictureId);
            ThrowUtils.throwIf(picture == null, ErrorCode.CANNOT_FOUND_DATA_ERROR, "图片不存在");

            // 查询是否已有该用户对该图片的对应行为记录（LIKE 或 COLLECT）
            UserPictureAction existingAction = userPictureActionMapper.selectByUserIdAndPictureIdAndActionType(userId, pictureId, actionType);

            boolean isActive;
            if (existingAction != null) {
                // 已存在记录：切换状态（1 -> 0 或 0 -> 1）
                boolean wasActive = existingAction.getStatus() == 1;
                int newStatus = wasActive ? 0 : 1;
                existingAction.setStatus(newStatus);
                existingAction.setUpdateTime(new Date());
                userPictureActionMapper.updateById(existingAction);

                // 使用传入的 countUpdater 做原子计数更新（+1 或 -1）
                countUpdater.accept(pictureId, newStatus == 1 ? 1 : -1);
                isActive = newStatus == 1;

                // 管理 Redis 缓存：激活则设置 key（短期 ttl），否则删除 key
                String key = redisPrefix + userId + ":" + pictureId;
                if (isActive) {
                    stringRedisTemplate.opsForValue().set(key, "1", ACTION_CACHE_HOURS, TimeUnit.HOURS);
                } else {
                    stringRedisTemplate.delete(key);
                }
                log.info("用户{}切换图片{} 的 {} 状态为 {}", userId, pictureId, actionType, isActive ? "激活" : "取消");
            } else {
                // 不存在记录：首次操作 -> 插入记录并计数 +1
                UserPictureAction newAction = new UserPictureAction();
                newAction.setUserId(userId);
                newAction.setPictureId(pictureId);
                newAction.setActionType(actionType);
                newAction.setStatus(1);
                newAction.setCreateTime(new Date());
                newAction.setUpdateTime(new Date());
                userPictureActionMapper.insert(newAction);

                // 计数 +1
                countUpdater.accept(pictureId, 1);
                isActive = true;

                // 设置 Redis 缓存，避免短时间内重复查询 DB
                String key = redisPrefix + userId + ":" + pictureId;
                stringRedisTemplate.opsForValue().set(key, "1", ACTION_CACHE_HOURS, TimeUnit.HOURS);

                log.info("用户{}首次对图片{} 执行了 {}", userId, pictureId, actionType);
            }
            return isActive;
        } finally {
            // 最终块：释放锁（使用 Lua 脚本保证只有持有相同 lockValue 的客户端能释放）
            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) else return 0 end";
            try {
                stringRedisTemplate.execute(new DefaultRedisScript<>(script, Long.class),
                        Collections.singletonList(lockKey), lockValue);
            } catch (Exception e) {
                // 释放锁失败记录日志但不影响主流程
                log.warn("释放锁失败 key: {}", lockKey, e);
            }
        }
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
