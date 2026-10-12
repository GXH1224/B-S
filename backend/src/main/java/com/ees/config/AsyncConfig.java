package com.ees.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池：评估任务异步执行，不阻塞页面请求（对应方案 3.4 并发目标）。
 *
 * <p><b>两个池都必须显式设置拒绝策略</b>：默认的 {@code AbortPolicy} 在队列满时抛
 * {@code TaskRejectedException}。由于 {@code AuditFilter} 在 {@code finally} 中提交审计任务，
 * 该异常会冒泡覆盖已经成功的业务响应，表现为「接口 500 但数据其实已写入」。</p>
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
        // 队列满时由提交线程（Tomcat 工作线程）直接执行，而不是抛 TaskRejectedException。
        // 评估任务耗时长，这里宁可让该请求变慢，也不要抛异常把业务接口变成 500。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
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
        // 关键修复：审计等旁路 IO 任务在队列满时必须退化为「调用线程自己执行」，
        // 不能抛 TaskRejectedException 冒泡覆盖主业务响应。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setThreadNamePrefix("ees-io-");
        executor.initialize();
        return executor;
    }
}
