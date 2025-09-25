package com.yudi.cloud.manager.cache;

import cn.hutool.core.lang.TypeReference;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yudi.cloud.model.vo.picture.PictureVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;

@Component
public class PictureCache extends CacheTemplate<Page<PictureVO>>{

    public PictureCache(StringRedisTemplate stringRedisTemplate) {
        super(stringRedisTemplate);
    }


    @Override
    protected Type getTargetType() {
        return new TypeReference<Page<PictureVO>>() {}.getType();
    }
}
