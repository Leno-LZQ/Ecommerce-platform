package com.ecommerce.adminservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 看板聚合专用线程池。
 */
@Configuration
public class DashboardConfig {

    @Value("${admin.dashboard.thread-pool-size:10}")
    private int threadPoolSize;

    @Bean
    public ExecutorService dashboardExecutor() {
        return new ThreadPoolExecutor(
            threadPoolSize,
            threadPoolSize,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(50),
            r -> {
                Thread t = new Thread(r, "dashboard-");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
