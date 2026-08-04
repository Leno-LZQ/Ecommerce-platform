package com.ecommerce.promotionservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class RedisScriptConfig {

    /**
     * 下单优惠锁定 Lua 脚本。
     * 返回 Long：1=成功 / -1=券已被占用 / -2=预算不足 / -3=超每日参与限制
     */
    @Bean
    public RedisScript<Long> lockPromotionScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/lock_promotion.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
