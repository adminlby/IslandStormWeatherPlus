package io.lbynb.islandstorm.web.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.lbynb.islandstorm.IslandStormPlugin;
import io.lbynb.islandstorm.web.WebPermission;
import io.lbynb.islandstorm.web.WebUser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST API 路由：解析路径/方法/查询/请求体，做令牌鉴权与权限校验，分发到各 Handler，
 * 并把返回对象序列化为 JSON。所有响应均为 JSON。
 *
 * <p>令牌来源：请求头 {@code Authorization: Bearer <token>}，或 {@code X-Auth-Token}，或 Cookie {@code IST_TOKEN}。
 * 仅 /api/login 无需令牌。</p>
 */
public class ApiRouter implements HttpHandler {

    private final IslandStormPlugin plugin;
    private final Gson gson = new Gson();

    private final AuthApiHandler auth;
    private final WeatherApiHandler weather;
    private final RegionApiHandler region;
    private final MapApiHandler map;
    private final StormPathApiHandler storm;
    private final UserApiHandler user;

    public ApiRouter(IslandStormPlugin plugin) {
        this.plugin = plugin;
        this.auth = new AuthApiHandler(plugin);
        this.weather = new WeatherApiHandler(plugin);
        this.region = new RegionApiHandler(plugin);
        this.map = new MapApiHandler(plugin);
        this.storm = new StormPathApiHandler(plugin);
        this.user = new UserApiHandler(plugin);
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();

            if ("OPTIONS".equalsIgnoreCase(method)) {
                ex.getResponseHeaders().add("Allow", "GET, POST, OPTIONS");
                send(ex, 204, "");
                return;
            }

            Map<String, String> query = parseQuery(ex.getRequestURI().getRawQuery());
            JsonObject body = readBody(ex);
            String token = extractToken(ex);

            Object result = route(path, method, query, body, token, ex);
            sendJson(ex, 200, result);
        } catch (ApiException e) {
            sendError(ex, e.status(), e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("API 处理异常 " + ex.getRequestURI() + " : " + e);
            sendError(ex, 500, "内部错误");
        } finally {
            ex.close();
        }
    }

    private Object route(String path, String method, Map<String, String> query,
                         JsonObject body, String token, HttpExchange ex) {
        // 登录无需令牌
        if (path.equals("/api/login")) {
            requirePost(method);
            return auth.login(body);
        }

        // 其余一律要求有效令牌
        WebUser u = plugin.webAuthManager().validate(token);
        if (u == null) throw new ApiException(401, "未登录或令牌已过期");

        switch (path) {
            case "/api/logout":
                requirePost(method);
                return auth.logout(token);

            case "/api/status":
                need(u, WebPermission.SERVER_STATUS);
                return weather.status();

            case "/api/weather":
                need(u, WebPermission.WEATHER_VIEW);
                return weather.weather();
            case "/api/weather/set":
                requirePost(method);
                need(u, WebPermission.WEATHER_SET);
                return weather.setWeather(body);
            case "/api/wind/set":
                requirePost(method);
                need(u, WebPermission.WIND_SET);
                return weather.setWind(body);
            case "/api/forecast":
                need(u, WebPermission.FORECAST_VIEW);
                return weather.forecast();
            case "/api/forecast/hourly":
                need(u, WebPermission.FORECAST_VIEW);
                return weather.hourly();

            case "/api/regions":
                need(u, WebPermission.REGION_VIEW);
                return region.list();
            case "/api/regions/create":
                requirePost(method);
                need(u, WebPermission.REGION_CREATE);
                return region.create(body);
            case "/api/regions/update":
                requirePost(method);
                need(u, WebPermission.REGION_EDIT);
                return region.update(body);
            case "/api/regions/delete":
                requirePost(method);
                need(u, WebPermission.REGION_DELETE);
                return region.delete(body);

            case "/api/worlds":
                need(u, WebPermission.MAP_VIEW);
                return map.worlds();
            case "/api/map":
                need(u, WebPermission.MAP_VIEW);
                return map.map(query);

            case "/api/storm/path":
                need(u, WebPermission.STORM_PATH_VIEW);
                return storm.get();
            case "/api/storm/path/set":
                requirePost(method);
                need(u, WebPermission.STORM_PATH_EDIT);
                return storm.set(body);
            case "/api/storm/path/start":
                requirePost(method);
                need(u, WebPermission.STORM_PATH_EDIT);
                return storm.start(body);
            case "/api/storm/path/pause":
                requirePost(method);
                need(u, WebPermission.STORM_PATH_EDIT);
                return storm.pause(body);
            case "/api/storm/path/resume":
                requirePost(method);
                need(u, WebPermission.STORM_PATH_EDIT);
                return storm.resume(body);
            case "/api/storm/path/stop":
                requirePost(method);
                need(u, WebPermission.STORM_PATH_EDIT);
                return storm.stop(body);
            case "/api/storm/path/delete":
                requirePost(method);
                need(u, WebPermission.STORM_PATH_EDIT);
                return storm.delete(body);

            case "/api/html/generate":
                requirePost(method);
                need(u, WebPermission.HTML_GENERATE);
                return htmlResult("preview");
            case "/api/html/generate/hourly":
                requirePost(method);
                need(u, WebPermission.HTML_GENERATE);
                return htmlResult("forecast");
            case "/api/html/generate/all":
                requirePost(method);
                need(u, WebPermission.HTML_GENERATE);
                return htmlResult("all");

            case "/api/users":
                need(u, WebPermission.USER_MANAGE);
                return user.list();
            case "/api/users/create":
                requirePost(method);
                need(u, WebPermission.USER_MANAGE);
                return user.create(body);
            case "/api/users/password":
                requirePost(method);
                need(u, WebPermission.USER_MANAGE);
                return user.password(body);
            case "/api/users/delete":
                requirePost(method);
                need(u, WebPermission.USER_MANAGE);
                return user.delete(body);

            default:
                throw ApiException.notFound("未知 API：" + path);
        }
    }

    private Object htmlResult(String which) {
        String msg = ApiSupport.runSync(plugin, () -> plugin.generateHtml(which));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("message", msg);
        return out;
    }

    // ---- 鉴权/校验 ----
    private void need(WebUser u, String perm) {
        if (!u.has(perm)) throw ApiException.forbidden("缺少权限：" + perm);
    }

    private void requirePost(String method) {
        if (!"POST".equalsIgnoreCase(method)) throw new ApiException(405, "需要 POST");
    }

    // ---- 请求解析 ----
    private JsonObject readBody(HttpExchange ex) {
        try (InputStream in = ex.getRequestBody()) {
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) return new JsonObject();
            String s = new String(bytes, StandardCharsets.UTF_8).trim();
            if (s.isEmpty()) return new JsonObject();
            return JsonParser.parseString(s).getAsJsonObject();
        } catch (Exception e) {
            throw ApiException.badRequest("请求体不是合法 JSON");
        }
    }

    private String extractToken(HttpExchange ex) {
        String authz = ex.getRequestHeaders().getFirst("Authorization");
        if (authz != null && authz.startsWith("Bearer ")) {
            return authz.substring(7).trim();
        }
        String x = ex.getRequestHeaders().getFirst("X-Auth-Token");
        if (x != null && !x.isEmpty()) return x.trim();
        String cookie = ex.getRequestHeaders().getFirst("Cookie");
        if (cookie != null) {
            for (String part : cookie.split(";")) {
                String p = part.trim();
                if (p.startsWith("IST_TOKEN=")) return p.substring("IST_TOKEN=".length());
            }
        }
        return null;
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> q = new HashMap<>();
        if (raw == null || raw.isEmpty()) return q;
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            if (i < 0) {
                q.put(urlDecode(pair), "");
            } else {
                q.put(urlDecode(pair.substring(0, i)), urlDecode(pair.substring(i + 1)));
            }
        }
        return q;
    }

    private static String urlDecode(String s) {
        try {
            return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    // ---- 响应 ----
    private void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        send(ex, status, gson.toJson(body));
    }

    private void sendError(HttpExchange ex, int status, String message) throws IOException {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("error", true);
        out.put("message", message);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        send(ex, status, gson.toJson(out));
    }

    private void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
