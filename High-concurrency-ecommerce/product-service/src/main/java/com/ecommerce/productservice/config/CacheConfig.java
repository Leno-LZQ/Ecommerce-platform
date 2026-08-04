package com.ecommerce.productservice.config;

import com.ecommerce.productservice.dto.CategoryResponse;
import com.ecommerce.productservice.dto.ProductResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Value("${cache.caffeine.product-ttl-seconds:5}")
    private int productTtlSeconds;

    @Value("${cache.caffeine.product-max-size:10000}")
    private int productMaxSize;

    @Value("${cache.caffeine.category-ttl-seconds:1800}")
    private int categoryTtlSeconds;

    @Value("${cache.caffeine.category-max-size:500}")
    private int categoryMaxSize;

    /** 商品 Caffeine 缓存 */
    @Bean
    public Cache<Long, ProductResponse> productCaffeineCache() {
        return Caffeine.newBuilder()
            .expireAfterWrite(productTtlSeconds, TimeUnit.SECONDS)
            .maximumSize(productMaxSize)
            .recordStats()
            .build();
    }

    /** 分类 Caffeine 缓存 */
    @Bean
    public Cache<String, List<CategoryResponse>> categoryCaffeineCache() {
        return Caffeine.newBuilder()
            .expireAfterWrite(categoryTtlSeconds, TimeUnit.SECONDS)
            .maximumSize(categoryMaxSize)
            .recordStats()
            .build();
    }

    /** RedisTemplate 配置 */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jsonRedisSerializer());
        return template;
    }

    /** 支持 LocalDateTime 的 JSON 序列化器（默认 GenericJackson2JsonRedisSerializer 不支持 JSR310） */
    private GenericJackson2JsonRedisSerializer jsonRedisSerializer() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        om.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
            ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(om);
    }

    /** Redisson BloomFilter */
    @Bean
    public RBloomFilter<Long> productBloomFilter(RedissonClient redisson,
                                                 @Value("${cache.bloom-filter.expected-insertions:1000000}") long expected,
                                                 @Value("${cache.bloom-filter.false-probability:0.01}") double probability) {
        RBloomFilter<Long> filter = redisson.getBloomFilter("bf:product:ids");
        filter.tryInit(expected, probability);
        return filter;
    }
}
