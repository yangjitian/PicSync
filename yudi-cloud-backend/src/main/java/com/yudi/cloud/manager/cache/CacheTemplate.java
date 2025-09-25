package com.yudi.cloud.manager.cache;


import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;

/**
 *
 * 作用：
 * - “两级缓存”（本地 Caffeine + Redis），避免重复编写缓存读取/回填逻辑
 * - 通过 Type（而非 Class<T>）保留泛型信息，解决 JSON 反序列化时的类型擦除问题
 * - JSONUtil.toBean 依赖 getTargetType() 返回的 Type 来正确反序列化带泛型的类型。
 */
@Data
@Slf4j
public abstract class CacheTemplate<T> {

    protected final StringRedisTemplate stringRedisTemplate;

    public CacheTemplate(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private final Cache<String, String> localCache =
            Caffeine.newBuilder().initialCapacity(1024)
                    .maximumSize(10000L)
                    .expireAfterWrite(5L, TimeUnit.MINUTES)
                    .build();

    // 用 Type 来承载目标类型（可保留泛型信息）
    protected abstract Type getTargetType();

    // 统一的反序列化方法
    private T deserialize(String json) {
        JSONObject obj = JSONUtil.parseObj(json);
        return JSONUtil.toBean(obj, getTargetType(), false);
    }

    /**
     * 获取缓存数据
     */
    public T getWithCache(String cacheKey, Data<T> dataSupplier) {
        // 1. 本地缓存
        String localData = localCache.getIfPresent(cacheKey);
        if (localData != null) {
            return deserialize(localData);
        }

        // 2. Redis 缓存
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
        String redisData = valueOps.get(cacheKey);
        if (redisData != null) {
            localCache.put(cacheKey, redisData);
            return deserialize(redisData);
        }

        // 3. 未命中 -> 查询
        T data = dataSupplier.get();

        // 4. 回填缓存
        String cacheValue = JSONUtil.toJsonStr(data);
        localCache.put(cacheKey, cacheValue);
        valueOps.set(cacheKey, cacheValue, 300, TimeUnit.SECONDS);

        return data;
    }

    /**
     * 获取缓存数据（仅从缓存中获取，不执行数据查询）
     */
    public T get(String cacheKey) {
        String localData = localCache.getIfPresent(cacheKey);
        if (localData != null) {
            return deserialize(localData);
        }

        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
        String redisData = valueOps.get(cacheKey);
        if (redisData != null) {
            localCache.put(cacheKey, redisData);
            return deserialize(redisData);
        }

        return null;
    }

    /**
     * 设置缓存数据
     */
    public void set(String cacheKey, T data, long expireTimeSeconds) {
        String cacheValue = JSONUtil.toJsonStr(data);
        localCache.put(cacheKey, cacheValue);
        stringRedisTemplate.opsForValue().set(cacheKey, cacheValue, expireTimeSeconds, TimeUnit.SECONDS);
    }

    /**
     * 设置缓存数据（使用默认过期时间5分钟）
     */
    public void set(String cacheKey, T data) {
        set(cacheKey, data, 300);
    }

    /**
     * 删除缓存
     */
    public void delete(String cacheKey) {
        localCache.invalidate(cacheKey);
        stringRedisTemplate.delete(cacheKey);
    }

    /**
     * 数据提供者接口
     */
    @FunctionalInterface
    public interface Data<T> {
        T get();
    }
}
