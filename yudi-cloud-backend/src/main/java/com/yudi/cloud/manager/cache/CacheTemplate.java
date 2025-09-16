package com.yudi.cloud.manager.cache;


import cn.hutool.json.JSONUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

@Data
@Slf4j
public abstract class CacheTemplate<T> {

    protected final StringRedisTemplate stringRedisTemplate;

    /**
     * 构造函数
     */
    public CacheTemplate(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private final Cache<String, String> localCache =
            Caffeine.newBuilder().initialCapacity(1024)
                    .maximumSize(10000L)
                    // 缓存 5 分钟移除
                    .expireAfterWrite(5L, TimeUnit.MINUTES)
                    .build();

    /**
     * 获取缓存数据
     */
    public T getWithCache(String cacheKey, Data<T> Data) {
        // 1. 查询本地缓存
        String localData = localCache.getIfPresent(cacheKey);
        if (localData != null) {
            return JSONUtil.toBean(localData, getTargetType());
        }

        // 2. 查询Redis缓存
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
        String redisData = valueOps.get(cacheKey);
        if (redisData != null) {
            localCache.put(cacheKey, redisData);
            return JSONUtil.toBean(redisData, getTargetType());
        }

        // 3. 缓存未命中，执行数据查询
        T data = Data.get();

        // 4. 更新缓存
        String cacheValue = JSONUtil.toJsonStr(data);
        localCache.put(cacheKey, cacheValue);
        valueOps.set(cacheKey, cacheValue, 300, TimeUnit.SECONDS);

        return data;
    }

    /**
     * 获取缓存数据（仅从缓存中获取，不执行数据查询）
     */
    public T get(String cacheKey) {
        // 1. 查询本地缓存
        String localData = localCache.getIfPresent(cacheKey);
        if (localData != null) {
            return JSONUtil.toBean(localData, getTargetType());
        }

        // 2. 查询Redis缓存
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
        String redisData = valueOps.get(cacheKey);
        if (redisData != null) {
            localCache.put(cacheKey, redisData);
            return JSONUtil.toBean(redisData, getTargetType());
        }

        return null;
    }

    /**
     * 设置缓存数据
     */
    public void set(String cacheKey, T data, long expireTimeSeconds) {
        String cacheValue = JSONUtil.toJsonStr(data);
        
        // 更新本地缓存
        localCache.put(cacheKey, cacheValue);
        
        // 更新Redis缓存
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
        valueOps.set(cacheKey, cacheValue, expireTimeSeconds, TimeUnit.SECONDS);
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
        // 删除本地缓存
        localCache.invalidate(cacheKey);
        
        // 删除Redis缓存
        stringRedisTemplate.delete(cacheKey);
    }

    /**
     * 获取目标类型Class
     */
    protected abstract Class<T> getTargetType();

    /**
     * 数据提供者接口
     */
    @FunctionalInterface
    public interface Data<T> {
        T get();
    }
}
