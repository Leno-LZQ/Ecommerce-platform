package com.ecommerce.promotionservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.ecommerce.client")
@ComponentScan(basePackages = "com.ecommerce")   // 关键：common 在 com.ecommerce.* 下，默认扫描不到
@MapperScan("com.ecommerce.promotionservice.mapper")
@EnableScheduling                                // 超时释放/过期扫描定时任务
public class PromotionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PromotionServiceApplication.class, args);
    }

}
