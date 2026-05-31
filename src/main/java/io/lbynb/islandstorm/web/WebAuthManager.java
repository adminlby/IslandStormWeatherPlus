package io.lbynb.islandstorm.web;

import io.lbynb.islandstorm.IslandStormPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网页控制台鉴权管理器：读写 web-users.yml，管理用户与内存中的会话令牌。
 *
 * <p>密码以 PBKDF2 哈希存储，不落明文。首次加载若无任何用户，则自动创建 admin，
 * 生成随机临时密码并打印到控制台（仅此一次）。令牌仅存内存，重启后失效。</p>
 */
public class WebAuthManager {

    private final IslandStormPlugin plugin;
    private final File file;
    private final Map<String, WebUser> users = new LinkedHashMap<>();
    private final Map<String, SessionToken> tokens = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public WebAuthManager(IslandStormPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "web-users.yml");
    }

    public void load() {
        users.clear();
        if (!file.exists()) {
            plugin.saveResourceIfAbsent("web-users.yml");
        }

        if (file.exists()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection root = yml.getConfigurationSection("users");
            if (root != null) {
                for (String name : root.getKeys(false)) {
                    ConfigurationSection s = root.getConfigurationSection(name);
                    if (s == null) continue;
                    WebUser u = new WebUser(name, s.getString("password-hash", ""));
                    u.permissions().addAll(s.getStringList("permissions"));
                    users.put(name.toLowerCase(), u);
                }
            }
        }

        if (users.isEmpty()) {
            createDefaultAdmin();
        }
    }

    private void createDefaultAdmin() {
        String tempPassword = PasswordHasher.randomPassword();
        WebUser admin = new WebUser("admin", PasswordHasher.hash(tempPassword));
        admin.grant("*");
        users.put("admin", admin);
        save();
        plugin.getLogger().warning("==================================================");
        plugin.getLogger().warning(" 已创建默认网页控制台用户 admin");
        plugin.getLogger().warning(" 临时随机密码：" + tempPassword);
        plugin.getLogger().warning(" 请尽快用 /storm webuser passwd admin <新密码> 修改！");
        plugin.getLogger().warning("==================================================");
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (WebUser u : users.values()) {
            String base = "users." + u.username();
            yml.set(base + ".password-hash", u.passwordHash());
            yml.set(base + ".permissions", new ArrayList<>(u.permissions()));
        }
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("保存 web-users.yml 失败：" + e.getMessage());
        }
    }

    // ---- 用户管理 ----
    public WebUser get(String username) {
        return username == null ? null : users.get(username.toLowerCase());
    }

    public boolean exists(String username) {
        return username != null && users.containsKey(username.toLowerCase());
    }

    public Collection<WebUser> all() {
        return new ArrayList<>(users.values());
    }

    public boolean createUser(String username, String password, List<String> perms) {
        if (exists(username)) return false;
        WebUser u = new WebUser(username, PasswordHasher.hash(password));
        if (perms != null) u.permissions().addAll(perms);
        users.put(username.toLowerCase(), u);
        save();
        return true;
    }

    public boolean setPassword(String username, String password) {
        WebUser u = get(username);
        if (u == null) return false;
        u.setPasswordHash(PasswordHasher.hash(password));
        save();
        return true;
    }

    public boolean deleteUser(String username) {
        if (!exists(username)) return false;
        users.remove(username.toLowerCase());
        // 同时清掉该用户的令牌
        tokens.values().removeIf(t -> t.username().equalsIgnoreCase(username));
        save();
        return true;
    }

    public boolean grant(String username, String permission) {
        WebUser u = get(username);
        if (u == null) return false;
        boolean ok = u.grant(permission);
        if (ok) save();
        return ok;
    }

    public boolean revoke(String username, String permission) {
        WebUser u = get(username);
        if (u == null) return false;
        boolean ok = u.revoke(permission);
        if (ok) save();
        return ok;
    }

    // ---- 鉴权与令牌 ----

    /** 校验账号密码，成功返回新令牌串，失败返回 null。 */
    public String authenticate(String username, String password) {
        WebUser u = get(username);
        if (u == null) return null;
        if (!PasswordHasher.verify(password, u.passwordHash())) return null;

        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        long expire = System.currentTimeMillis()
                + plugin.configManager().raw().getLong("web.token-expire-minutes", 120) * 60_000L;
        tokens.put(token, new SessionToken(token, u.username(), expire));
        return token;
    }

    /** 校验令牌，返回对应用户；无效或过期返回 null。 */
    public WebUser validate(String token) {
        if (token == null) return null;
        SessionToken st = tokens.get(token);
        if (st == null) return null;
        if (!st.isValid(System.currentTimeMillis())) {
            tokens.remove(token);
            return null;
        }
        return get(st.username());
    }

    public void logout(String token) {
        if (token != null) tokens.remove(token);
    }

    /** 清理所有过期令牌。 */
    public void purgeExpiredTokens() {
        long now = System.currentTimeMillis();
        tokens.values().removeIf(t -> !t.isValid(now));
    }
}
