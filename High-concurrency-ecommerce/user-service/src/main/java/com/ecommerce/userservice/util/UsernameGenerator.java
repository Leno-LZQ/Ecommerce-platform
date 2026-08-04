package com.ecommerce.userservice.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 用户名自动生成：u_ + 8 位随机小写字母数字。
 * u_8a3f2c1b
 */
public class UsernameGenerator {

    private static final String PREFIX = "u_";
    private static final int RANDOM_LENGTH = 8;
    private static final String CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    public static String generate() {
        StringBuilder sb = new StringBuilder(PREFIX);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            sb.append(CHARS.charAt(rng.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
