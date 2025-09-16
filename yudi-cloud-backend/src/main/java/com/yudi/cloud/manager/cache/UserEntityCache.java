package com.yudi.cloud.manager.cache;

import com.yudi.cloud.model.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserEntityCache extends CacheTemplate<User>{

    public UserEntityCache(StringRedisTemplate stringRedisTemplate) {
        super(stringRedisTemplate);
    }

    @Override
    protected Class<User> getTargetType() {
        return User.class;
    }
}