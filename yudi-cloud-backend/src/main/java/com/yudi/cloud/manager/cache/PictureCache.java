package com.yudi.cloud.manager.cache;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yudi.cloud.model.vo.picture.PictureVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class PictureCache extends CacheTemplate<Page<PictureVO>>{

    public PictureCache(StringRedisTemplate stringRedisTemplate) {
        super(stringRedisTemplate);
    }

    @Override
    protected Class<Page<PictureVO>> getTargetType() {
        return (Class<Page<PictureVO>>) (Class<?>) Page.class;
    }
}
