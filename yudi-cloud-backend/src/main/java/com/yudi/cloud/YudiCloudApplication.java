package com.yudi.cloud;

import org.apache.shardingsphere.spring.boot.ShardingSphereAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;


/**
 * @author yudi
 * date 2025-07-20
 * desName PicSync云见图
 */
@SpringBootApplication(exclude = {ShardingSphereAutoConfiguration.class})
@MapperScan("com.yudi.cloud.mapper")
@EnableAspectJAutoProxy(proxyTargetClass=true)
public class YudiCloudApplication {

    public static void main(String[] args) {
        SpringApplication.run(YudiCloudApplication.class, args);
    }

}
