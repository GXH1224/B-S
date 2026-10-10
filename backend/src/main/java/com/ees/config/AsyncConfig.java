package com.ees.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 异步任务线程池：评估任务异步执行，不阻塞页面请求（对应方案 3.4 并发目标）。
 */
@Configuration
public class AsyncConfig {

    private final EesProperties properties;

    public AsyncConfig(EesProperties properties) {
        this.properties = properties;
    }

    @Bean(name = "evaluationExecutor")
    public TaskExecutor evaluationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int concurrency = Math.max(2, properties.getAlgorithm().getMaxConcurrentTasks());
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency * 2);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("ees-eval-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    @Bean(name = "ioExecutor")
    public TaskExecutor ioExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("ees-io-");
        executor.initialize();
        return executor;
    }
}
