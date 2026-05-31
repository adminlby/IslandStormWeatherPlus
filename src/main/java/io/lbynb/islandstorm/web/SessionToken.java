package io.lbynb.islandstorm.web;

/** 内存中的会话令牌：令牌串 + 所属用户名 + 过期时间（现实毫秒）。重启后失效。 */
public class SessionToken {

    private final String token;
    private final String username;
    private final long expiresAtMillis;

    public SessionToken(String token, String username, long expiresAtMillis) {
        this.token = token;
        this.username = username;
        this.expiresAtMillis = expiresAtMillis;
    }

    public String token() {
        return token;
    }

    public String username() {
        return username;
    }

    public long expiresAtMillis() {
        return expiresAtMillis;
    }

    public boolean isValid(long now) {
        return now < expiresAtMillis;
    }
}
