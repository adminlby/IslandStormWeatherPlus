package io.lbynb.islandstorm.web.api;

import com.google.gson.JsonObject;
import io.lbynb.islandstorm.IslandStormPlugin;
import io.lbynb.islandstorm.web.WebAuthManager;

import java.util.LinkedHashMap;
import java.util.Map;

/** 登录 / 登出。登录无需令牌；登出注销当前令牌。 */
public class AuthApiHandler {

    private final IslandStormPlugin plugin;

    public AuthApiHandler(IslandStormPlugin plugin) {
        this.plugin = plugin;
    }

    /** POST /api/login {username, password} → {token, permissions}。 */
    public Object login(JsonObject body) {
        String username = str(body, "username");
        String password = str(body, "password");
        if (username == null || password == null) {
            throw ApiException.badRequest("缺少 username 或 password");
        }
        WebAuthManager auth = plugin.webAuthManager();
        String token = auth.authenticate(username, password);
        if (token == null) {
            throw new ApiException(401, "用户名或密码错误");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("token", token);
        out.put("username", username);
        out.put("permissions", auth.get(username).permissions());
        return out;
    }

    /** POST /api/logout（携带令牌）→ {ok:true}。 */
    public Object logout(String token) {
        plugin.webAuthManager().logout(token);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        return out;
    }

    private static String str(JsonObject o, String k) {
        return (o != null && o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : null;
    }
}
