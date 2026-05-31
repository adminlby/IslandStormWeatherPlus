package io.lbynb.islandstorm.web;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * 密码哈希工具：使用 JDK 原生 PBKDF2WithHmacSHA256（无外部依赖）。
 *
 * <p>存储格式：{@code PBKDF2$sha256$<iterations>$<saltBase64>$<hashBase64>}。
 * 校验时按存储的迭代次数与盐重算并做常量时间比较，避免明文密码落盘。</p>
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 100_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    /** 生成密码哈希字符串。 */
    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
        return "PBKDF2$sha256$" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    /** 校验明文密码是否匹配存储的哈希。格式非法时返回 false。 */
    public static boolean verify(String password, String stored) {
        if (password == null || stored == null) return false;
        String[] parts = stored.split("\\$");
        // 期望 5 段：PBKDF2 / sha256 / iterations / salt / hash
        if (parts.length != 5 || !"PBKDF2".equals(parts[0])) return false;
        try {
            int iterations = Integer.parseInt(parts[2]);
            byte[] salt = Base64.getDecoder().decode(parts[3]);
            byte[] expected = Base64.getDecoder().decode(parts[4]);
            byte[] actual = pbkdf2(password.toCharArray(), salt, iterations, expected.length * 8);
            return constantTimeEquals(expected, actual);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 生成一个可读的随机临时密码（首次启动 admin 用）。 */
    public static String randomPassword() {
        final String chars = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 14; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyBits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyBits);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (InvalidKeySpecException | java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("PBKDF2 计算失败：" + e.getMessage(), e);
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}
