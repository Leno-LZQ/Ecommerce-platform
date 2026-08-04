package com.ecommerce.productservice.config;

import com.ecommerce.client.UserClient;
import com.ecommerce.dto.user.UserProfileResponse;
import com.ecommerce.result.Result;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class UserProfileCacheConfig {

    @Bean
    public Cache<Long, UserProfileResponse> userProfileCache(UserClient userClient) {
        return Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(5000)
            .build();
    }

}
