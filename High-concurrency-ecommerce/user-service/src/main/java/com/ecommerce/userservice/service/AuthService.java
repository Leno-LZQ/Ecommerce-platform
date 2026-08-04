package com.ecommerce.userservice.service;


import cn.hutool.core.util.IdUtil;
import com.ecommerce.Security.SessionData;
import com.ecommerce.client.MerchantClient;
import com.ecommerce.constant.ErrorCode;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.userservice.dto.LoginResponse;
import com.ecommerce.userservice.entity.User;
import com.ecommerce.userservice.mapper.UserMapper;
import com.ecommerce.userservice.mapper.UserRoleMapper;
import com.ecommerce.userservice.util.PasswordGenerator;
import com.ecommerce.userservice.util.PhoneCryptoUtils;
import com.ecommerce.userservice.util.UsernameGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final VerifyCodeService verifyCodeService;
    private final StringRedisTemplate redisTemplate;

    private final MerchantClient merchantClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${security.phone.hmac-key}")
    private String hmacKey;

    @Value("${security.phone.aes-key}")
    private String aesKey;

    private static final String LOGIN_FAIL_KEY = "login:fail:";
    private static final int MAX_FAIL_COUNT = 5;
    private static final int LOCKOUT_MINUTES = 15;

    /**
     * 发送短信验证码
     *
     * @param phone 手机号
     */
    public void sendSmsCode(String phone) {
        verifyCodeService.sendCode(phone);
    }

    @Transactional
    public LoginResponse loginByCode(String phone, String code) {
        // 1. 校验验证码
        verifyCodeService.verifyAndConsume(phone, code);

        // 2. 查重
        String phoneHash = PhoneCryptoUtils.hash(phone, hmacKey);
        User user = userMapper.selectByPhoneHash(phoneHash);

        boolean isNewUser = false;
        if (user == null) {
            // 3. 新用户 → 自动注册
            user = autoRegister(phone, phoneHash);
            isNewUser = true;
        }

        // 4. 检查账户状态
        if (user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        // 5. 签发双 Token
        return buildLoginResponse(user, isNewUser);
    }

    private User autoRegister(String phone, String phoneHash) {
        long userId = IdUtil.getSnowflakeNextId();

        // 自动生成用户名（冲突时重试）
        String username;
        for (int i = 0; i < 5; i++) {
            username = UsernameGenerator.generate();
            if (userMapper.countByUsername(username) == 0) {
                break;
            }
            if (i == 4) {
                username = "u_" + IdUtil.fastSimpleUUID().substring(0, 8);
            }
        }
        // 编译通过：username 在 for 循环内赋值，这里用变量捕获有问题。重写：
        String finalUsername = generateUniqueUsername();

        String initPassword = PasswordGenerator.generate();
        String encodedPassword = passwordEncoder.encode(initPassword);
        String phoneEncrypted = PhoneCryptoUtils.encryptRandomIv(phone, aesKey);

        User user = new User();
        user.setId(userId);
        user.setUsername(finalUsername);
        user.setPassword(encodedPassword);
        user.setPhoneHash(phoneHash);
        user.setPhoneEncrypted(phoneEncrypted);
        user.setNickname("用户" + String.format("%04d", userId % 10000));
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);

        // 分配 ROLE_USER（id=2）
        userRoleMapper.insertUserRole(IdUtil.getSnowflakeNextId(), userId, 2L);

        log.info("【新用户注册】phone={} username={} userId={}",
            PhoneCryptoUtils.mask(phone), finalUsername, userId);
        log.info("【初始密码】{}", initPassword);
        // TODO 生产：将初始密码通过短信发送给用户
        // smsSender.send(phone, "注册成功！用户名：" + finalUsername + "，初始密码：" + initPassword + "，请登录后修改");

        return user;
    }

    private String generateUniqueUsername() {
        for (int i = 0; i < 5; i++) {
            String u = UsernameGenerator.generate();
            if (userMapper.countByUsername(u) == 0) return u;
        }
        return "u_" + IdUtil.fastSimpleUUID().substring(0, 8);
    }

    // ==================== 密码登录 ====================

    public LoginResponse loginByPassword(String phone, String password) {
        // 1. 查用户
        String phoneHash = PhoneCryptoUtils.hash(phone, hmacKey);
        User user = userMapper.selectByPhoneHash(phoneHash);
        if (user == null) {
            throw new BusinessException(ErrorCode.PHONE_ERROR);
        }

        // 2. 检查锁定
        String blockKey = "login:blocked:" + phone;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(blockKey))) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        // 3. 检查账户状态
        if (user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        // 4. 密码校验
        if (!passwordEncoder.matches(password, user.getPassword())) {
            recordLoginFailure(phone);
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }

        // 5. 清除失败计数
        clearLoginFailure(phone);

        return buildLoginResponse(user, false);
    }

    private void recordLoginFailure(String phone) {
        String key = LOGIN_FAIL_KEY + phone;
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(LOCKOUT_MINUTES));
        }
        if (attempts >= MAX_FAIL_COUNT) {
            redisTemplate.opsForValue().set(
                "login:blocked:" + phone, "1", Duration.ofMinutes(LOCKOUT_MINUTES));
            log.warn("【安全】手机号 {} 连续登录失败 {} 次，锁定 {} 分钟",
                PhoneCryptoUtils.mask(phone), attempts, LOCKOUT_MINUTES);
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
    }

    private void clearLoginFailure(String phone) {
        redisTemplate.delete(LOGIN_FAIL_KEY + phone);
        redisTemplate.delete("login:blocked:" + phone);
    }

    // ==================== 修改密码 ====================

    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 校验旧密码
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }

        // 新密码强度
        validatePassword(newPassword);

        // 新密码不能与旧密码相同
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_SAME_AS_OLD);
        }

        if(userMapper.updatePassword(userId, passwordEncoder.encode(newPassword)) == 0){
            throw new BusinessException(ErrorCode.PASSWORD_UPDATE_FAILED);
        }
        log.info("用户 {} 修改密码成功", userId);
    }

    public void resetPassword(String phone, String code ,String newPassword) {
        verifyCodeService.verifyAndConsume(phone, code);
        String phoneHash = PhoneCryptoUtils.hash(phone, hmacKey);
        User user = userMapper.selectByPhoneHash(phoneHash);
        if (user == null) {
            throw new BusinessException(ErrorCode.PHONE_ERROR);
        }
        // 新密码强度
        validatePassword(newPassword);
        // 新密码不能与旧密码相同
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_SAME_AS_OLD);
        }
        if(userMapper.updatePassword(user.getId(), passwordEncoder.encode(newPassword)) == 0){
            throw new BusinessException(ErrorCode.PASSWORD_RESET_FAILED);
        }
        log.info("用户 {} 重置密码成功", user.getId());
    }

    public Map<String, Object> verifyPassword(Long userId,String password){
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_ERROR);
        }

        String permitToken = UUID.randomUUID().toString();

        redisTemplate.opsForValue().set(
            "change:phone:permit:" + userId,
            permitToken,
            300, TimeUnit.SECONDS          // ← Redis TTL, 5分钟后自动失效
        );

        return Map.of("permitToken", permitToken, "expireSeconds", 300);
    }

    public void confirmPhone(Long userId, String permitToken, String newPhone, String newPhoneCode){
        String stored = redisTemplate.opsForValue()
            .get("change:phone:permit:" + userId);

        if (stored == null) {
            throw new BusinessException(ErrorCode.CHANGE_PHONE_TOKEN_EXPIRED);
        }
        if (!stored.equals(permitToken)) {
            throw new BusinessException(ErrorCode.CHANGE_PHONE_TOKEN_INVALID);
        }
        verifyCodeService.verifyAndConsume(newPhone, newPhoneCode);

        String phoneHash = PhoneCryptoUtils.hash(newPhone, hmacKey);
        User newUser = userMapper.selectByPhoneHash(phoneHash);
        if (newUser != null) {
            throw new BusinessException(ErrorCode.PHONE_DUPLICATE);
        }
        String phoneEncrypted = PhoneCryptoUtils.encryptRandomIv(newPhone, aesKey);
        if(userMapper.updatePhone(userId, newPhone, phoneEncrypted) == 0){
            throw new BusinessException(ErrorCode.CHANGE_PHONE_FAILED);
        }
        log.info("用户 {} 换绑手机号成功", userId);
    }


    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new BusinessException(ErrorCode.PASSWORD_TOO_WEAK);
        }
        boolean hasUpper = password.matches(".*[A-Z].*");
        boolean hasLower = password.matches(".*[a-z].*");
        boolean hasDigit = password.matches(".*\\d.*");
        boolean hasSymbol = password.matches(".*[!@#$%^&*].*");
        int kinds = (hasUpper ? 1 : 0) + (hasLower ? 1 : 0) + (hasDigit ? 1 : 0) + (hasSymbol ? 1 : 0);
        if (kinds < 2) {
            throw new BusinessException(ErrorCode.PASSWORD_TOO_WEAK);
        }
    }

    // ==================== session 续期 ====================

    public LoginResponse extendSession(String sessionId) {
        String json = redisTemplate.opsForValue().get("session:" + sessionId);
        if (json == null) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }
        redisTemplate.expire("session:" + sessionId, Duration.ofMinutes(30));
        return LoginResponse.builder()
            .sessionId(sessionId)
            .expiresIn(1800L)
            .build();
    }

    // ==================== 登出 ====================

    public void logout(String sessionId) {
        redisTemplate.delete("session:" + sessionId);
    }

    // ==================== 辅助 ====================

    private LoginResponse buildLoginResponse(User user, boolean isNewUser) {
        List<String> roles = userMapper.selectRolesByUserId(user.getId());
        List<String> permissions = userMapper.selectPermissionsByUserId(user.getId());

        Long merchantId = null;
        if (roles.contains("ROLE_MERCHANT")) {
            try {
                merchantId = merchantClient.getMerchantIdByUserId(user.getId()).getData();
            } catch (Exception e) {
                log.error("获取 merchantId 失败，userId={}", user.getId(), e);
            }
        }

        String sessionId = UUID.randomUUID().toString().replace("-", "");

        SessionData session = new SessionData();
        session.setUserId(user.getId());
        session.setRoles(roles);
        session.setPermissions(permissions);
        session.setMerchantId(merchantId);

        try {
            redisTemplate.opsForValue().set(
                "session:" + sessionId,
                mapper.writeValueAsString(session),
                Duration.ofMinutes(30));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return LoginResponse.builder()
            .sessionId(sessionId)
            .expiresIn(1800L)
            .isNewUser(isNewUser)
            .username(user.getUsername())
            .nickname(user.getNickname())
            .build();
    }

}
