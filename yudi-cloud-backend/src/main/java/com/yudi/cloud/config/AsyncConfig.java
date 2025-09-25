package com.yudi.cloud.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置
 * 用于推荐分数计算等异步任务
 * 
 * @author yudi
 * @since 1.0.0
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    /**
     * 推荐分数计算专用线程池
     * 
     * @return Executor
     */
    @Bean("recommendationTaskExecutor")
    public Executor recommendationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数：推荐分数计算是CPU密集型任务，设置为CPU核心数
        executor.setCorePoolSize(Runtime.getRuntime().availableProcessors());
        
        // 最大线程数：考虑到推荐计算可能并发较多，设置为核心数的2倍
        executor.setMaxPoolSize(Runtime.getRuntime().availableProcessors() * 2);
        
        // 队列容量：设置合理的队列大小，避免内存溢出
        executor.setQueueCapacity(100);
        
        // 线程名前缀：便于日志追踪
        executor.setThreadNamePrefix("recommendation-");
        
        // 拒绝策略：推荐分数计算失败不应该影响主流程，使用CallerRunsPolicy
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 线程空闲时间：60秒
        executor.setKeepAliveSeconds(60);
        
        // 允许核心线程超时：节省资源
        executor.setAllowCoreThreadTimeOut(true);
        
        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        
        // 等待时间：30秒
        executor.setAwaitTerminationSeconds(30);
        
        executor.initialize();
        
        log.info("推荐分数计算线程池初始化完成: 核心线程数={}, 最大线程数={}, 队列容量={}", 
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        
        return executor;
    }
}