package com.ecommerce.userservice.service;

import com.ecommerce.constant.ErrorCode;
import com.ecommerce.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyCodeService {

    private final StringRedisTemplate redisTemplate;

    private static final String CODE_KEY  = "sms:code:";      // 验证码
    private static final String RATE_KEY  = "sms:rate:";      // 60s 发送间隔
    private static final String DAILY_KEY = "sms:daily:";     // 单日上限
    private static final int CODE_EXPIRE_SECONDS = 300;       // 5 分钟
    private static final int RATE_LIMIT_SECONDS = 60;         // 60 秒
    private static final int DAILY_LIMIT        = 10;         // 单日 10 次

    @Value("${sms.enabled:false}")
    private boolean smsEnabled;  // 开发环境 false → 打印日志

    /**
     * 发送验证码。
     */
    public void sendCode(String phone) {
        // 1. 频控：60 秒内不可重复发送
        String rateKey = RATE_KEY + phone;
        Boolean locked = redisTemplate.opsForValue()
            .setIfAbsent(rateKey, "1", Duration.ofSeconds(RATE_LIMIT_SECONDS));
        if (Boolean.FALSE.equals(locked)) {
            throw new BusinessException(ErrorCode.SMS_SEND_TOO_FAST);
        }

        // 2. 日限：单日最多 10 条
        String dailyKey = DAILY_KEY + phone;
        Long dailyCount = redisTemplate.opsForValue().increment(dailyKey);
        if (dailyCount == 1) {
            redisTemplate.expire(dailyKey, Duration.ofHours(24));
        }
        if (dailyCount > DAILY_LIMIT) {
            throw new BusinessException(ErrorCode.SMS_DAILY_LIMIT_EXCEEDED);
        }

        // 3. 生成 6 位验证码
        String code = String.format("%06d",
            ThreadLocalRandom.current().nextInt(100000, 999999));

        // 4. 存入 Redis（5 分钟有效）
        String codeKey = CODE_KEY + phone;
        redisTemplate.opsForValue().set(codeKey, code, Duration.ofSeconds(CODE_EXPIRE_SECONDS));

        // 5. 发送短信（开发环境打印到控制台）
        if (smsEnabled) {
            // smsSender.send(phone, "验证码：" + code + "，5分钟内有效");
            log.info("【短信已发送】phone={}", phone);
        }
        log.info("【验证码】phone={} code={} (dev mode)", phone, code);
    }

    /**
     * 校验验证码。通过后删除，防止复用。
     */
    public void verifyAndConsume(String phone, String code) {
        String codeKey = CODE_KEY + phone;
        String stored = redisTemplate.opsForValue().get(codeKey);
        if (stored == null) {
            throw new BusinessException(ErrorCode.SMS_CODE_EXPIRED);
        }
        if (!stored.equals(code)) {
            throw new BusinessException(ErrorCode.SMS_CODE_ERROR);
        }
        redisTemplate.delete(codeKey);  // 立即删除，防止重复使用
    }
}
