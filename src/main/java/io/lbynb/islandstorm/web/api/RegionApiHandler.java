package io.lbynb.islandstorm.web.api;

import com.google.gson.JsonObject;
import io.lbynb.islandstorm.IslandStormPlugin;
import io.lbynb.islandstorm.region.WeatherRegion;
import io.lbynb.islandstorm.weather.DangerLevel;
import io.lbynb.islandstorm.weather.WeatherType;
import io.lbynb.islandstorm.wind.WindDirection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 区域天气 CRUD。create/update/delete 修改后立即保存并即时生效。 */
public class RegionApiHandler {

    private final IslandStormPlugin plugin;

    public RegionApiHandler(IslandStormPlugin plugin) {
        this.plugin = plugin;
    }

    /** GET /api/regions → 区域列表。 */
    public Object list() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (WeatherRegion r : plugin.regionManager().all()) {
            list.add(toMap(r));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("regions", list);
        return out;
    }

    /** POST /api/regions/create。 */
    public Object create(JsonObject b) {
        String name = str(b, "name");
        if (name == null || name.isEmpty()) throw ApiException.badRequest("缺少区域名称");
        if (plugin.regionManager().exists(name)) throw ApiException.badRequest("区域已存在：" + name);
        WeatherType type = WeatherType.fromString(str(b, "weather"), WeatherType.CLEAR);
        requireNonStorm(type);
        WeatherRegion r = new WeatherRegion(
                name,
                str(b, "world") == null ? plugin.configManager().mapDefaultWorld() : str(b, "world"),
                getInt(b, "minX", 0), getInt(b, "minZ", 0), getInt(b, "maxX", 0), getInt(b, "maxZ", 0),
                type,
                getDouble(b, "windSpeed", type.defaultWindSpeed()),
                WindDirection.fromString(str(b, "windDirection"), type.defaultWindDirection()),
                DangerLevel.fromString(str(b, "dangerLevel"), type.dangerLevel()),
                durationToEnd(getInt(b, "durationMinutes", 0)));
        plugin.regionManager().add(r);
        return toMap(r);
    }

    /** POST /api/regions/update。 */
    public Object update(JsonObject b) {
        String name = str(b, "name");
        WeatherRegion r = plugin.regionManager().get(name);
        if (r == null) throw ApiException.notFound("区域不存在：" + name);
        if (b.has("weather")) {
            WeatherType nt = WeatherType.fromString(str(b, "weather"), r.weather());
            requireNonStorm(nt);
            r.setWeather(nt);
        }
        if (b.has("windSpeed")) r.setWindSpeed(b.get("windSpeed").getAsDouble());
        if (b.has("windDirection"))
            r.setWindDirection(WindDirection.fromString(str(b, "windDirection"), r.windDirection()));
        if (b.has("dangerLevel"))
            r.setDangerLevel(DangerLevel.fromString(str(b, "dangerLevel"), r.dangerLevel()));
        if (b.has("minX") || b.has("minZ") || b.has("maxX") || b.has("maxZ")) {
            r.setBounds(getInt(b, "minX", r.minX()), getInt(b, "minZ", r.minZ()),
                    getInt(b, "maxX", r.maxX()), getInt(b, "maxZ", r.maxZ()));
        }
        if (b.has("durationMinutes")) r.setEndEpochMillis(durationToEnd(b.get("durationMinutes").getAsInt()));
        plugin.regionManager().save();
        return toMap(r);
    }

    /** POST /api/regions/delete {name}。 */
    public Object delete(JsonObject b) {
        String name = str(b, "name");
        boolean ok = plugin.regionManager().remove(name);
        if (!ok) throw ApiException.notFound("区域不存在：" + name);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        return out;
    }

    private Map<String, Object> toMap(WeatherRegion r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", r.name());
        m.put("world", r.world());
        m.put("minX", r.minX());
        m.put("minZ", r.minZ());
        m.put("maxX", r.maxX());
        m.put("maxZ", r.maxZ());
        m.put("weather", r.weather().name());
        m.put("displayName", r.weather().displayName());
        m.put("windSpeed", (int) r.windSpeed());
        m.put("windDirection", r.windDirection().name());
        m.put("dangerLevel", r.dangerLevel().name());
        m.put("dangerColor", r.dangerLevel().hexColor());
        long now = System.currentTimeMillis();
        m.put("remainingMinutes", r.endEpochMillis() <= 0 ? 0 : Math.max(0, (r.endEpochMillis() - now) / 60_000L));
        return m;
    }

    /** 矩形区域不允许台风/极端风暴天气（这两个是风暴路径专属）。 */
    private static void requireNonStorm(WeatherType type) {
        if (type == WeatherType.TYPHOON || type == WeatherType.EXTREME_STORM) {
            throw ApiException.badRequest("区域不能使用台风/极端风暴天气，请用风暴路径功能（两点以上路径）创建。");
        }
    }

    private static long durationToEnd(int minutes) {
        return minutes <= 0 ? -1 : System.currentTimeMillis() + minutes * 60_000L;
    }

    private static String str(JsonObject o, String k) {
        return (o != null && o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : null;
    }

    private static int getInt(JsonObject o, String k, int def) {
        return (o != null && o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsInt() : def;
    }

    private static double getDouble(JsonObject o, String k, double def) {
        return (o != null && o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsDouble() : def;
    }
}
