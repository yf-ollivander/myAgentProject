package org.jeecg.modules.airag.execution.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration @EnableScheduling @EnableConfigurationProperties(AiExecutorProperties.class)
public class AiExecutorConfiguration {
    @Bean("aiExecutorTaskExecutor")
    public ThreadPoolTaskExecutor aiExecutorTaskExecutor(AiExecutorProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize()); executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity()); executor.setThreadNamePrefix("ai-executor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy()); executor.initialize();
        return executor;
    }
}
