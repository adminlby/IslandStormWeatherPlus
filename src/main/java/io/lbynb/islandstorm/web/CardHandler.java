package io.lbynb.islandstorm.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.lbynb.islandstorm.IslandStormPlugin;
import io.lbynb.islandstorm.html.HtmlWeatherCardGenerator;
import io.lbynb.islandstorm.web.api.ApiSupport;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 公开只读天气卡处理器：给 OBS 浏览器源使用的「实时 URL」，无需登录。
 *
 * <ul>
 *   <li>{@code GET /card/preview} —— 赛事风当前天气预览卡。</li>
 *   <li>{@code GET /card/hourly}  —— 央视风小时预报卡。</li>
 * </ul>
 *
 * <p>支持 {@code ?mode=global|player} 与 {@code ?player=<名>}：player 模式显示该玩家附近天气，
 * 玩家不在线自动回落全局。每次请求实时渲染，页面自带 {@code meta refresh}，OBS 常驻自动更新。</p>
 */
public class CardHandler implements HttpHandler {

    private final IslandStormPlugin plugin;

    public CardHandler(IslandStormPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
            String mode = q.getOrDefault("mode", "global");
            String player = q.get("player");

            HtmlWeatherCardGenerator gen = plugin.htmlGenerator();
            if (gen == null) {
                send(ex, 503, "<h1>HTML 生成器未就绪</h1>");
                return;
            }
            final boolean hourly = path.endsWith("/hourly");
            // 触及世界/玩家/区域/风暴数据，切回主线程渲染
            String html = ApiSupport.runSync(plugin, () -> {
                HtmlWeatherCardGenerator.CardContext ctx = gen.resolveContext(mode, player);
                return hourly ? gen.buildHourlyHtml(ctx) : gen.buildPreviewHtml(ctx);
            });
            send(ex, 200, html);
        } catch (Exception e) {
            send(ex, 500, "<h1>渲染失败</h1><p>" + escape(e.getMessage()) + "</p>");
        } finally {
            ex.close();
        }
    }

    private void send(HttpExchange ex, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        // 公开只读卡片：允许跨源嵌入，便于在网页/OBS 内嵌
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
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

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
