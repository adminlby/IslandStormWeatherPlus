package io.lbynb.islandstorm.web;

import com.sun.net.httpserver.HttpServer;
import io.lbynb.islandstorm.IslandStormPlugin;
import io.lbynb.islandstorm.web.api.ApiRouter;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * 网页控制台 HTTP 服务管理器：基于 JDK 内置 {@link HttpServer}，不依赖外部 Web 服务。
 *
 * <p>启动时把打包在 jar 内的前端资源释放到配置的静态目录（不存在才写出），
 * 注册 /api（{@link ApiRouter}）与 /（{@link StaticFileHandler}）两个上下文。</p>
 */
public class WebServerManager {

    /** 需要从 jar 释放到静态目录的前端文件清单。 */
    private static final String[] WEB_ASSETS = {
            "login.html", "console.html", "style.css", "app.js", "map.js"
    };

    private final IslandStormPlugin plugin;
    private HttpServer server;

    public WebServerManager(IslandStormPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.configManager().webEnabled()) {
            plugin.getLogger().info("网页控制台已在配置中关闭。");
            return;
        }
        File staticRoot = resolveStaticRoot();
        extractAssets(staticRoot);

        String bind = plugin.configManager().webBind();
        int port = plugin.configManager().webPort();
        try {
            server = HttpServer.create(new InetSocketAddress(bind, port), 0);
            server.createContext("/api", new ApiRouter(plugin));
            // 公开只读天气卡（无需鉴权，供 OBS 浏览器源）；前缀比 "/" 更长，优先匹配 /card/*
            server.createContext("/card", new CardHandler(plugin));
            server.createContext("/", new StaticFileHandler(staticRoot));
            // 小型线程池处理请求；Bukkit 相关逻辑在 Handler 内部经 runSync 切回主线程
            server.setExecutor(Executors.newFixedThreadPool(4));
            server.start();
            plugin.getLogger().info("网页控制台已启动： http://" + bind + ":" + port
                    + " （静态目录 " + staticRoot.getPath() + "）");
            plugin.getLogger().info("天气卡实时 URL（可直接加入 OBS 浏览器源）："
                    + " http://" + bind + ":" + port + "/card/preview"
                    + " ， http://" + bind + ":" + port + "/card/hourly");
            plugin.getLogger().warning("注意：默认监听 0.0.0.0，请确保已配置防火墙/反向代理，避免控制台暴露公网。");
        } catch (IOException e) {
            plugin.getLogger().severe("网页控制台启动失败（端口 " + port + " 可能被占用）：" + e.getMessage());
            server = null;
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            plugin.getLogger().info("网页控制台已停止。");
        }
    }

    private File resolveStaticRoot() {
        String configured = plugin.configManager().webStaticFolder();
        File f = new File(configured);
        // 相对路径相对于服务器根目录；统一规整
        if (!f.isAbsolute()) {
            f = new File(configured);
        }
        if (!f.exists()) {
            f.mkdirs();
        }
        return f;
    }

    private void extractAssets(File staticRoot) {
        for (String name : WEB_ASSETS) {
            File target = new File(staticRoot, name);
            if (target.exists()) continue;
            try (var in = plugin.getResource("web/" + name)) {
                if (in == null) {
                    plugin.getLogger().warning("缺少前端资源 web/" + name + "（jar 内未找到）。");
                    continue;
                }
                java.nio.file.Files.copy(in, target.toPath());
            } catch (IOException e) {
                plugin.getLogger().warning("释放前端资源 " + name + " 失败：" + e.getMessage());
            }
        }
    }
}
