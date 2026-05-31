package io.lbynb.islandstorm.web.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.lbynb.islandstorm.IslandStormPlugin;
import io.lbynb.islandstorm.web.WebPermission;
import io.lbynb.islandstorm.web.WebUser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 网页用户管理（需 user.manage 权限）。密码经 PBKDF2 哈希，不回传明文/哈希。 */
public class UserApiHandler {

    private final IslandStormPlugin plugin;

    public UserApiHandler(IslandStormPlugin plugin) {
        this.plugin = plugin;
    }

    /** GET /api/users → 用户列表（不含密码哈希）。 */
    public Object list() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (WebUser u : plugin.webAuthManager().all()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", u.username());
            m.put("permissions", new ArrayList<>(u.permissions()));
            list.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("users", list);
        out.put("availablePermissions", WebPermission.ALL);
        return out;
    }

    /** POST /api/users/create {username, password, permissions?:[]}。 */
    public Object create(JsonObject b) {
        String username = str(b, "username");
        String password = str(b, "password");
        if (username == null || password == null) throw ApiException.badRequest("缺少 username 或 password");
        List<String> perms = perms(b);
        boolean ok = plugin.webAuthManager().createUser(username, password, perms);
        if (!ok) throw ApiException.badRequest("用户已存在：" + username);
        return list();
    }

    /** POST /api/users/password {username, password}。 */
    public Object password(JsonObject b) {
        String username = str(b, "username");
        String password = str(b, "password");
        if (username == null || password == null) throw ApiException.badRequest("缺少 username 或 password");
        boolean ok = plugin.webAuthManager().setPassword(username, password);
        if (!ok) throw ApiException.notFound("用户不存在：" + username);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        return out;
    }

    /** POST /api/users/delete {username}。 */
    public Object delete(JsonObject b) {
        String username = str(b, "username");
        boolean ok = plugin.webAuthManager().deleteUser(username);
        if (!ok) throw ApiException.notFound("用户不存在：" + username);
        return list();
    }

    private List<String> perms(JsonObject b) {
        List<String> perms = new ArrayList<>();
        if (b.has("permissions") && b.get("permissions").isJsonArray()) {
            JsonArray arr = b.getAsJsonArray("permissions");
            for (JsonElement el : arr) {
                String node = el.getAsString();
                if (WebPermission.isValid(node)) perms.add(node);
            }
        }
        return perms;
    }

    private static String str(JsonObject o, String k) {
        return (o != null && o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : null;
    }
}
