package com.yudi.cloud.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Date;
import com.yudi.cloud.utils.TimeUtils;

@Configuration
public class MybatisPlusConfig {

    /**
     * 拦截器配置
     *
     * @return {@link MybatisPlusInterceptor}
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 自动填充处理器 - 处理时区问题
     *
     * @return {@link MetaObjectHandler}
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                Date now = TimeUtils.getCurrentBeijingTime();
                this.strictInsertFill(metaObject, "createTime", Date.class, now);
                this.strictInsertFill(metaObject, "updateTime", Date.class, now);
                this.strictInsertFill(metaObject, "editTime", Date.class, now);
                this.strictInsertFill(metaObject, "scoreUpdatedAt", Date.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                Date now = TimeUtils.getCurrentBeijingTime();
                this.strictUpdateFill(metaObject, "updateTime", Date.class, now);
                this.strictUpdateFill(metaObject, "editTime", Date.class, now);
            }
        };
    }
}