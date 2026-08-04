package com.ecommerce.csservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(basePackages = "com.ecommerce")
@EnableScheduling
public class CsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CsServiceApplication.class, args);
    }

}
