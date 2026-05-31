package io.lbynb.islandstorm.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

/**
 * 静态文件处理器：从 web 静态目录提供前端文件。
 *
 * <p>做了基本的路径穿越防护（规范化后必须仍在根目录内）。根路径 "/" 默认返回 login.html。</p>
 */
public class StaticFileHandler implements HttpHandler {

    private final File root;

    public StaticFileHandler(File root) {
        this.root = root;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/login.html";
            }
            File target = new File(root, path).getCanonicalFile();
            // 路径穿越防护：必须仍在 root 之内
            if (!target.getPath().startsWith(root.getCanonicalPath())) {
                send(ex, 403, "Forbidden", "text/plain; charset=utf-8");
                return;
            }
            if (!target.exists() || target.isDirectory()) {
                send(ex, 404, "Not Found: " + path, "text/plain; charset=utf-8");
                return;
            }
            byte[] data = Files.readAllBytes(target.toPath());
            ex.getResponseHeaders().set("Content-Type", contentType(target.getName()));
            ex.sendResponseHeaders(200, data.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(data);
            }
        } catch (Exception e) {
            send(ex, 500, "Internal Error", "text/plain; charset=utf-8");
        } finally {
            ex.close();
        }
    }

    private void send(HttpExchange ex, int status, String body, String type) throws IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String contentType(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".html") || n.endsWith(".htm")) return "text/html; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (n.endsWith(".json")) return "application/json; charset=utf-8";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }
}
