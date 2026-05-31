package io.lbynb.islandstorm.web;

import java.util.LinkedHashSet;
import java.util.Set;

/** 网页控制台用户：用户名 + 密码哈希 + 权限集合。 */
public class WebUser {

    private final String username;
    private String passwordHash;
    private final Set<String> permissions = new LinkedHashSet<>();

    public WebUser(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public String username() {
        return username;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Set<String> permissions() {
        return permissions;
    }

    /** 是否拥有某权限（持有 "*" 即拥有全部）。 */
    public boolean has(String permission) {
        return permissions.contains("*") || permissions.contains(permission);
    }

    public boolean grant(String permission) {
        return permissions.add(permission);
    }

    public boolean revoke(String permission) {
        return permissions.remove(permission);
    }
}
