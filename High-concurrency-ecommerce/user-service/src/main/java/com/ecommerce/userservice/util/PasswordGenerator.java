package com.ecommerce.userservice.util;

import java.security.SecureRandom;

/**
 * 初始密码：16 位随机字符（大小写字母 + 数字 + 特殊符号）。
 */
public class PasswordGenerator {

    private static final String UPPER  = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER  = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SYMBOLS = "!@#$%^&*";
    private static final String ALL = UPPER + LOWER + DIGITS + SYMBOLS;
    private static final int LENGTH = 16;
    private static final SecureRandom RNG = new SecureRandom();

    /**
     * 生成强随机密码，保证至少包含大写、小写、数字、符号各一个。
     */
    public static String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        // 确保每类至少一个
        sb.append(UPPER.charAt(RNG.nextInt(UPPER.length())));
        sb.append(LOWER.charAt(RNG.nextInt(LOWER.length())));
        sb.append(DIGITS.charAt(RNG.nextInt(DIGITS.length())));
        sb.append(SYMBOLS.charAt(RNG.nextInt(SYMBOLS.length())));
        // 剩余随机
        for (int i = 4; i < LENGTH; i++) {
            sb.append(ALL.charAt(RNG.nextInt(ALL.length())));
        }
        // 打乱顺序
        return shuffle(sb.toString());
    }

    private static String shuffle(String input) {
        char[] chars = input.toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RNG.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }
}
