package com.yudi.cloud.manager.cache;

import cn.hutool.core.lang.TypeReference;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yudi.cloud.model.entity.User;
import com.yudi.cloud.model.vo.user.UserVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;

@Component
public class UserCache extends CacheTemplate<Page<UserVO>>{

    public UserCache(StringRedisTemplate stringRedisTemplate) {
        super(stringRedisTemplate);
    }

    @Override
    protected Type getTargetType() {
        return new TypeReference<Page<UserVO>>() {}.getType();
    }


}