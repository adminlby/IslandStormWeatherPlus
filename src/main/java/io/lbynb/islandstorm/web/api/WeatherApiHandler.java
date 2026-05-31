package io.lbynb.islandstorm.web.api;

import com.google.gson.JsonObject;
import io.lbynb.islandstorm.IslandStormPlugin;
import io.lbynb.islandstorm.forecast.HourlyForecastEntry;
import io.lbynb.islandstorm.time.GameTimeUtil;
import io.lbynb.islandstorm.time.ParsedDuration;
import io.lbynb.islandstorm.time.TimeMode;
import io.lbynb.islandstorm.weather.ForecastEntry;
import io.lbynb.islandstorm.weather.WeatherState;
import io.lbynb.islandstorm.weather.WeatherType;
import io.lbynb.islandstorm.wind.WindDirection;
import io.lbynb.islandstorm.wind.WindState;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 天气 / 风 / 预报相关 API，以及服务器状态。 */
public class WeatherApiHandler {

    private final IslandStormPlugin plugin;

    public WeatherApiHandler(IslandStormPlugin plugin) {
        this.plugin = plugin;
    }

    /** GET /api/weather → 当前天气 + 风。 */
    public Object weather() {
        WeatherState cur = plugin.weatherManager().current();
        WindState wind = plugin.windManager().global();
        Map<String, Object> out = new LinkedHashMap<>();
        if (cur == null) {
            out.put("present", false);
            return out;
        }
        WeatherType t = cur.type();
        out.put("present", true);
        out.put("type", t.name());
        out.put("displayName", t.displayName());
        out.put("icon", t.icon());
        out.put("description", t.description());
        out.put("visibility", t.visibility());
        out.put("dangerLevel", t.dangerLevel().name());
        out.put("dangerColor", t.dangerLevel().hexColor());
        out.put("windSpeed", (int) wind.speed());
        out.put("windDirection", wind.direction().name());
        out.put("windLevel", wind.levelLabel());
        long now = System.currentTimeMillis();
        out.put("infinite", cur.isInfinite());
        out.put("remainingMillis", cur.remainingMillis(now));
        out.put("source", cur.source());
        return out;
    }

    /** POST /api/weather/set {weather, duration?, mode?} → runSync 应用。 */
    public Object setWeather(JsonObject body) {
        WeatherType type = WeatherType.fromString(str(body, "weather"), null);
        if (type == null) throw ApiException.badRequest("未知天气类型");
        long durationMillis = type.defaultDurationMinutes() * 60_000L;
        String duration = str(body, "duration");
        if (duration != null && !duration.isEmpty()) {
            TimeMode hint = TimeMode.fromString(str(body, "mode"), null);
            ParsedDuration pd = GameTimeUtil.parse(duration, hint);
            if (pd == null) throw ApiException.badRequest("无效时长：" + duration);
            durationMillis = pd.realMillis();
        }
        final long dur = durationMillis;
        ApiSupport.runSync(plugin, () -> {
            plugin.weatherManager().setWeather(type, dur, "web");
            return null;
        });
        return weather();
    }

    /** POST /api/wind/set {speed, direction}。 */
    public Object setWind(JsonObject body) {
        if (body == null || !body.has("speed")) throw ApiException.badRequest("缺少 speed");
        double speed = body.get("speed").getAsDouble();
        if (speed < 0) throw ApiException.badRequest("风速不能为负");
        WindDirection dir = WindDirection.fromString(str(body, "direction"), null);
        if (dir == null) throw ApiException.badRequest("未知风向");
        ApiSupport.runSync(plugin, () -> {
            plugin.windManager().set(speed, dir);
            return null;
        });
        return weather();
    }

    /** GET /api/forecast → 排期列表。 */
    public Object forecast() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ForecastEntry e : plugin.forecastManager().all()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", e.type().name());
            m.put("displayName", e.type().displayName());
            m.put("icon", e.type().icon());
            m.put("windSpeed", (int) e.windSpeed());
            m.put("windDirection", e.windDirection().name());
            m.put("dangerLevel", e.dangerLevel().name());
            m.put("durationMillis", e.durationMillis());
            list.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entries", list);
        return out;
    }

    /** GET /api/forecast/hourly → 小时级预报（触及世界时间，runSync）。 */
    public Object hourly() {
        List<HourlyForecastEntry> entries = ApiSupport.runSync(plugin,
                () -> plugin.hourlyForecastManager().generate());
        List<Map<String, Object>> list = new ArrayList<>();
        for (HourlyForecastEntry e : entries) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("index", e.index());
            m.put("type", e.weatherType().name());
            m.put("displayName", e.displayName());
            m.put("icon", e.icon());
            m.put("windSpeed", (int) e.windSpeed());
            m.put("windDirection", e.windDirection().name());
            m.put("dangerLevel", e.dangerLevel().name());
            m.put("dangerColor", e.dangerLevel().hexColor());
            m.put("realStart", GameTimeUtil.formatRealMillis(e.realStartMillis()));
            m.put("mcStart", GameTimeUtil.formatWorldTimeHHmm(e.mcStartTick()));
            m.put("mcDay", GameTimeUtil.mcDayNumber(e.mcStartTick()));
            list.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entries", list);
        return out;
    }

    /** GET /api/status → 服务器与插件运行状态（runSync）。 */
    public Object status() {
        return ApiSupport.runSync(plugin, () -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("online", Bukkit.getOnlinePlayers().size());
            out.put("maxPlayers", Bukkit.getMaxPlayers());
            out.put("serverName", plugin.configManager().serverName());
            try {
                double[] tps = Bukkit.getTPS();
                out.put("tps", Math.round(Math.min(20.0, tps[0]) * 100.0) / 100.0);
            } catch (Throwable t) {
                out.put("tps", null);
            }
            out.put("regions", plugin.regionManager().all().size());
            out.put("storms", plugin.stormPathManager().all().size());
            out.put("blockDamage", plugin.configManager().blockDamageEnabled());
            out.put("realTime", GameTimeUtil.nowRealFormatted());
            out.put("weather", weather());
            return out;
        });
    }

    private static String str(JsonObject o, String k) {
        return (o != null && o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : null;
    }
}
