package com.ecommerce.userservice.util;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 手机号加解密工具。
 *
 * 设计原则：
 * - phoneHash   = HMAC-SHA256(明文, hmacKey)  → 确定性输出，用于数据库唯一索引查重
 * - phoneEncrypted = AES-256-CBC(明文, 随机IV, aesKey) → 随机输出，用于解密还原明文
 *
 * 两个密钥完全独立，生产环境从 KMS / 环境变量注入。
 */
public class PhoneCryptoUtils {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String AES_ALGORITHM  = "AES/CBC/PKCS5Padding";
    private static final int    IV_LENGTH      = 16;

    // ==================== 确定性哈希（唯一索引） ====================

    /**
     * 同一手机号永远生成相同哈希值，使 UNIQUE 索引可用。
     */
    public static String hash(String plainPhone, String hmacKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(hmacKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(plainPhone.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("HMAC 哈希计算失败", e);
        }
    }

    // ==================== 随机加密（解密还原） ====================

    /**
     * AES-256-CBC 加密，随机 IV 使同一明文每次生成不同密文。
     * 返回 Base64(IV[16B] + 密文)，IV 前置以便解密。
     */
    public static String encryptRandomIv(String plainPhone, String aesKey) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(aesKey.getBytes(StandardCharsets.UTF_8), "AES"),
                new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(plainPhone.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, combined, IV_LENGTH, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("AES 加密失败", e);
        }
    }

    /**
     * 解密还原手机号明文。
     * @param base64Combined Base64(IV + 密文)
     */
    public static String decrypt(String base64Combined, String aesKey) {
        try {
            byte[] combined = Base64.getDecoder().decode(base64Combined);
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(aesKey.getBytes(StandardCharsets.UTF_8), "AES"),
                new IvParameterSpec(iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES 解密失败", e);
        }
    }

    // ==================== 展示脱敏 ====================

    /** 13812348888 → 138****8888 */
    public static String mask(String plainPhone) {
        if (plainPhone == null || plainPhone.length() != 11) return plainPhone;
        return plainPhone.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
    }
}
