package com.sapbasu.kafkademo.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties("common.thread-pool")
@Slf4j
@Data
public class CommonThreadPoolConfig {

    private int corePoolSize;
    private int maximumPoolSize;
    private int keepAliveTimeMinutes;
    private int boundedQueueCapacity;

    @Bean
    public Executor getCommonThreadPool() {
        return new ThreadPoolExecutor(
                corePoolSize,
                maximumPoolSize,
                keepAliveTimeMinutes,
                TimeUnit.MINUTES,
                new LinkedBlockingQueue<Runnable>(boundedQueueCapacity),
                new ThreadFactoryBuilder()
                        .setNameFormat("app-common-pool")
                        .setUncaughtExceptionHandler(
                                (thread, throwable) -> log.error("Uncaught Exception:", throwable)
                        )
                        .build(),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
