package io.lbynb.islandstorm.html;

import io.lbynb.islandstorm.IslandStormPlugin;
import io.lbynb.islandstorm.config.ConfigManager;
import io.lbynb.islandstorm.forecast.HourlyForecastEntry;
import io.lbynb.islandstorm.time.GameTimeUtil;
import io.lbynb.islandstorm.weather.WeatherState;
import io.lbynb.islandstorm.weather.WeatherType;
import io.lbynb.islandstorm.wind.WindState;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * HTML 天气卡生成器。输出两个独立、自包含（内联 CSS、不依赖 CDN）的 16:9 页面：
 *
 * <ul>
 *   <li>{@code weather-preview.html} —— 赛事风当前天气预览卡。</li>
 *   <li>{@code hourly-forecast.html} —— 央视风未来逐小时预报卡（双时间）。</li>
 * </ul>
 *
 * <p>两个页面均带 {@code <meta http-equiv="refresh">} 自动刷新，适合 OBS 浏览器源常驻。</p>
 */
public class HtmlWeatherCardGenerator {

    private final IslandStormPlugin plugin;
    private final ConfigManager config;

    public HtmlWeatherCardGenerator(IslandStormPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    /** which ∈ {preview, open, forecast, all}；返回给用户的提示信息。 */
    public String generate(String which) {
        try {
            switch (which == null ? "preview" : which.toLowerCase()) {
                case "forecast":
                    return "已生成小时预报卡：" + generateHourlyForecast().getPath();
                case "all":
                    File a = generateWeatherPreview();
                    File b = generateHourlyForecast();
                    return "已生成全部：" + a.getName() + " + " + b.getName();
                case "preview":
                case "open":
                default:
                    return "已生成天气预览卡：" + generateWeatherPreview().getPath();
            }
        } catch (IOException e) {
            plugin.getLogger().severe("生成 HTML 失败：" + e.getMessage());
            return "生成 HTML 失败：" + e.getMessage();
        }
    }

    public File generateAll() throws IOException {
        generateWeatherPreview();
        return generateHourlyForecast();
    }

    // ============================ 赛事风预览卡 ============================

    public File generateWeatherPreview() throws IOException {
        WeatherState cur = plugin.weatherManager().current();
        WindState wind = plugin.windManager().global();
        WeatherType t = cur != null ? cur.type() : WeatherType.CLEAR;

        String serverName = esc(config.serverName());
        String danger = t.dangerLevel().display();
        String dangerColor = t.dangerLevel().hexColor();
        long now = System.currentTimeMillis();
        String remain = (cur == null || cur.isInfinite()) ? "永久"
                : GameTimeUtil.bothDurationLabel(cur.remainingMillis(now));

        // 未来预报小卡（取小时预报前 5 条）
        List<HourlyForecastEntry> fc = safeHourly();
        StringBuilder fcHtml = new StringBuilder();
        for (int i = 0; i < Math.min(5, fc.size()); i++) {
            HourlyForecastEntry e = fc.get(i);
            fcHtml.append("""
                    <div class="fc">
                      <div class="fc-t">%s</div>
                      <div class="fc-ic">%s</div>
                      <div class="fc-nm">%s</div>
                      <div class="fc-wd">%d km/h %s</div>
                      <div class="fc-dg" style="color:%s">%s</div>
                    </div>
                    """.formatted(esc(GameTimeUtil.formatRealMillis(e.realStartMillis())), e.icon(), esc(e.displayName()),
                    (int) e.windSpeed(), e.windDirection().name(),
                    e.dangerLevel().hexColor(), e.dangerLevel().display()));
        }

        String html = """
                <!DOCTYPE html>
                <html lang="zh-CN"><head><meta charset="UTF-8">
                <meta http-equiv="refresh" content="15">
                <title>IslandStorm 天气预览</title>
                <style>
                %s
                </style></head>
                <body><div class="stage"><div class="card">
                  <div class="head">
                    <div class="title">%s 天气预览</div>
                    <div class="sub">IslandStorm Weather Center · 更新 %s</div>
                  </div>
                  <div class="main">
                    <div class="now">
                      <div class="big-icon">%s</div>
                      <div class="now-name">%s</div>
                      <div class="now-desc">%s</div>
                    </div>
                    <div class="metrics">
                      <div class="m"><div class="mk">风速</div><div class="mv">%d<span>km/h</span></div></div>
                      <div class="m"><div class="mk">风向</div><div class="mv">%s</div></div>
                      <div class="m"><div class="mk">危险等级</div><div class="mv" style="color:%s">%s</div></div>
                      <div class="m"><div class="mk">能见度</div><div class="mv">%s</div></div>
                      <div class="m"><div class="mk">持续</div><div class="mv small">%s</div></div>
                    </div>
                  </div>
                  <div class="fcbar">%s</div>
                  <div class="foot">%s · 100H Island Survival</div>
                </div></div></body></html>
                """.formatted(previewCss(), serverName, GameTimeUtil.nowRealFormatted(),
                t.icon(), esc(t.displayName()), esc(t.description()),
                (int) wind.speed(), wind.direction().name(),
                dangerColor, danger, esc(t.visibility()), remain,
                fcHtml, serverName);

        return write(config.raw().getString("html.file-name", "weather-preview.html"), html);
    }

    private String previewCss() {
        return """
                *{margin:0;padding:0;box-sizing:border-box;font-family:"Segoe UI","Microsoft YaHei",sans-serif}
                body{width:100vw;height:100vh;overflow:hidden;background:linear-gradient(135deg,#04162c,#0c2440 55%,#10406e)}
                .stage{width:100vw;height:100vh;display:flex;align-items:center;justify-content:center}
                .card{width:92vw;max-width:1600px;aspect-ratio:16/9;background:rgba(14,40,72,.55);
                  border:1px solid rgba(120,180,255,.2);border-radius:24px;backdrop-filter:blur(10px);
                  box-shadow:0 30px 80px rgba(0,0,0,.5);padding:3.2vh 3vw;display:flex;flex-direction:column;color:#dce9ff}
                .head{display:flex;justify-content:space-between;align-items:flex-end;border-bottom:1px solid rgba(120,180,255,.2);padding-bottom:1.6vh}
                .title{font-size:4.2vh;font-weight:800;color:#58e1ff;letter-spacing:1px}
                .sub{font-size:1.8vh;color:#8fb4e0}
                .main{flex:1;display:flex;align-items:center;gap:4vw;padding:2vh 0}
                .now{flex:0 0 38%;text-align:center}
                .big-icon{font-size:18vh;line-height:1}
                .now-name{font-size:5vh;font-weight:800;margin-top:1vh}
                .now-desc{font-size:2vh;color:#8fb4e0;margin-top:1vh}
                .metrics{flex:1;display:grid;grid-template-columns:1fr 1fr;gap:1.6vh 2vw}
                .m{background:rgba(7,20,39,.5);border:1px solid rgba(120,180,255,.16);border-radius:14px;padding:1.8vh 1.6vw}
                .mk{font-size:1.7vh;color:#8fb4e0}
                .mv{font-size:4.4vh;font-weight:800;color:#58e1ff;margin-top:.4vh}
                .mv span{font-size:2vh;color:#8fb4e0;margin-left:.4vw}
                .mv.small{font-size:2.4vh;color:#dce9ff}
                .fcbar{display:flex;gap:1vw;padding-top:1.6vh;border-top:1px solid rgba(120,180,255,.2)}
                .fc{flex:1;background:rgba(7,20,39,.45);border:1px solid rgba(120,180,255,.16);border-radius:12px;padding:1.2vh .6vw;text-align:center}
                .fc-t{font-size:1.5vh;color:#8fb4e0}.fc-ic{font-size:4vh;margin:.4vh 0}.fc-nm{font-size:1.8vh}
                .fc-wd{font-size:1.4vh;color:#8fb4e0;margin-top:.3vh}.fc-dg{font-size:1.4vh;margin-top:.2vh}
                .foot{text-align:center;color:#5f7da0;font-size:1.5vh;margin-top:1.4vh;letter-spacing:1px}
                """;
    }

    // ============================ 央视风小时预报卡 ============================

    public File generateHourlyForecast() throws IOException {
        WeatherState cur = plugin.weatherManager().current();
        WindState wind = plugin.windManager().global();
        WeatherType t = cur != null ? cur.type() : WeatherType.CLEAR;

        World w = resolveWorld();
        long fullTime = w != null ? w.getFullTime() : 0L;
        String mcNow = GameTimeUtil.formatWorldTimeHHmm(fullTime);
        long mcDay = GameTimeUtil.mcDayNumber(fullTime);
        String worldName = w != null ? w.getName() : "-";

        List<HourlyForecastEntry> list = safeHourly();
        StringBuilder cells = new StringBuilder();
        for (HourlyForecastEntry e : list) {
            boolean high = switch (e.dangerLevel()) {
                case HIGH, EXTREME -> true;
                default -> false;
            };
            cells.append("""
                    <div class="hour %s">
                      <div class="h-real">%s</div>
                      <div class="h-mc">MC %s</div>
                      <div class="h-ic">%s</div>
                      <div class="h-nm">%s</div>
                      <div class="h-wd">%d km/h · %s</div>
                      <div class="h-dg" style="color:%s">%s</div>
                    </div>
                    """.formatted(high ? "hot" : "",
                    esc(GameTimeUtil.formatRealMillis(e.realStartMillis())),
                    esc(GameTimeUtil.formatWorldTimeHHmm(e.mcStartTick())),
                    e.icon(), esc(e.displayName()), (int) e.windSpeed(),
                    e.windDirection().name(), e.dangerLevel().hexColor(), e.dangerLevel().display()));
        }

        String html = """
                <!DOCTYPE html>
                <html lang="zh-CN"><head><meta charset="UTF-8">
                <meta http-equiv="refresh" content="30">
                <title>IslandStorm 小时预报</title>
                <style>
                %s
                </style></head>
                <body><div class="stage"><div class="card">
                  <div class="top">
                    <div class="brand">IslandStorm Hourly Forecast</div>
                    <div class="brand-sub">100H Island Survival</div>
                    <div class="upd">更新 %s</div>
                  </div>
                  <div class="mid">
                    <div class="center">
                      <div class="c-ic">%s</div>
                      <div class="c-nm">%s</div>
                      <div class="c-meta">%d km/h %s · <span style="color:%s">危险 %s</span></div>
                    </div>
                    <div class="side">
                      <div class="srow"><span>当前世界</span><b>%s</b></div>
                      <div class="srow"><span>现实时间</span><b>%s</b></div>
                      <div class="srow"><span>MC 时间</span><b>第%d天 %s</b></div>
                      <div class="srow"><span>能见度</span><b>%s</b></div>
                    </div>
                  </div>
                  <div class="hours">%s</div>
                  <div class="foot">%s · IslandStorm Weather Center</div>
                </div></div></body></html>
                """.formatted(hourlyCss(), GameTimeUtil.nowRealFormatted(),
                t.icon(), esc(t.displayName()), (int) wind.speed(), wind.direction().name(),
                t.dangerLevel().hexColor(), t.dangerLevel().display(),
                esc(worldName), GameTimeUtil.nowRealFormatted(), mcDay, mcNow, esc(t.visibility()),
                cells, esc(config.serverName()));

        return write(config.raw().getString("html.hourly-file-name", "hourly-forecast.html"), html);
    }

    private String hourlyCss() {
        return """
                *{margin:0;padding:0;box-sizing:border-box;font-family:"Segoe UI","Microsoft YaHei",sans-serif}
                body{width:100vw;height:100vh;overflow:hidden;background:linear-gradient(160deg,#03122a,#0a2546 60%,#0e3a68)}
                .stage{width:100vw;height:100vh;display:flex;align-items:center;justify-content:center}
                .card{width:94vw;max-width:1700px;aspect-ratio:16/9;background:rgba(10,34,66,.5);
                  border:1px solid rgba(120,180,255,.18);border-radius:22px;backdrop-filter:blur(10px);
                  box-shadow:0 30px 80px rgba(0,0,0,.55);padding:2.6vh 2.4vw;display:flex;flex-direction:column;color:#eaf2ff}
                .top{display:flex;align-items:baseline;gap:1.4vw;border-bottom:2px solid rgba(88,225,255,.4);padding-bottom:1.4vh}
                .brand{font-size:3.8vh;font-weight:800;color:#58e1ff;letter-spacing:1px}
                .brand-sub{font-size:1.8vh;color:#8fb4e0}
                .upd{margin-left:auto;font-size:1.8vh;color:#8fb4e0}
                .mid{display:flex;gap:3vw;padding:2vh 0;border-bottom:1px solid rgba(120,180,255,.16)}
                .center{flex:0 0 42%;display:flex;align-items:center;gap:2vw}
                .c-ic{font-size:13vh;line-height:1}
                .c-nm{font-size:5vh;font-weight:800}
                .c-meta{font-size:2.2vh;color:#8fb4e0;margin-top:1vh}
                .side{flex:1;display:flex;flex-direction:column;justify-content:center;gap:1vh}
                .srow{display:flex;justify-content:space-between;background:rgba(7,20,39,.45);
                  border:1px solid rgba(120,180,255,.14);border-radius:10px;padding:1.1vh 1.4vw;font-size:2vh}
                .srow span{color:#8fb4e0}.srow b{color:#58e1ff}
                .hours{flex:1;display:flex;gap:.8vw;overflow:hidden;padding-top:1.6vh}
                .hour{flex:1;min-width:0;background:rgba(7,20,39,.5);border:1px solid rgba(120,180,255,.14);
                  border-radius:12px;padding:1vh .4vw;text-align:center;display:flex;flex-direction:column;gap:.3vh}
                .hour.hot{background:rgba(248,81,73,.16);border-color:rgba(248,81,73,.5)}
                .h-real{font-size:1.5vh;font-weight:700}.h-mc{font-size:1.2vh;color:#8fb4e0}
                .h-ic{font-size:3.4vh;margin:.3vh 0}.h-nm{font-size:1.5vh}
                .h-wd{font-size:1.2vh;color:#8fb4e0}.h-dg{font-size:1.3vh;font-weight:700}
                .foot{text-align:center;color:#5f7da0;font-size:1.5vh;margin-top:1.2vh;letter-spacing:1px}
                """;
    }

    // ============================ 工具 ============================

    private List<HourlyForecastEntry> safeHourly() {
        try {
            return plugin.hourlyForecastManager().generate();
        } catch (Throwable t) {
            return List.of();
        }
    }

    private World resolveWorld() {
        World w = Bukkit.getWorld(config.mapDefaultWorld());
        if (w == null && !Bukkit.getWorlds().isEmpty()) w = Bukkit.getWorlds().get(0);
        return w;
    }

    private File write(String fileName, String html) throws IOException {
        File dir = new File(config.raw().getString("html.output-folder", "plugins/IslandStorm/html"));
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建输出目录：" + dir.getPath());
        }
        File out = new File(dir, fileName);
        Files.write(out.toPath(), html.getBytes(StandardCharsets.UTF_8));
        return out;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
