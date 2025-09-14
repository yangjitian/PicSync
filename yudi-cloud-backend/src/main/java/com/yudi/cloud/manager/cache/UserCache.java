package com.yudi.cloud.manager.cache;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yudi.cloud.model.vo.user.UserVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserCache extends CacheTemplate<Page<UserVO>>{

    public UserCache(StringRedisTemplate stringRedisTemplate) {
        super(stringRedisTemplate);
    }

    @Override
    protected Class<Page<UserVO>> getTargetType() {
        return (Class<Page<UserVO>>) (Class<?>) Page.class;
    }
}