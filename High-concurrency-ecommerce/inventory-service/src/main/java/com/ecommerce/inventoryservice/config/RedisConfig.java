package com.ecommerce.inventoryservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

@Configuration
public class RedisConfig {

    /**
     * 批量预扣 Lua 脚本。
     * Lua 返回 {1} 或 {0, index, stock, qty} → Java List<Long>
     * 所以 resultType 必须是 List.class，不能是 Long.class。
     */
    @Bean
    public RedisScript<List> batchReserveScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/batch_reserve.lua"));
        script.setResultType(List.class);
        return script;
    }

    @Bean
    public RedisScript<Long> reserveStockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/reserve_stock.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public RedisScript<Long> releaseStockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/release_stock.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
