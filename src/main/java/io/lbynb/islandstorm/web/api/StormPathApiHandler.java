package io.lbynb.islandstorm.web.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.lbynb.islandstorm.IslandStormPlugin;
import io.lbynb.islandstorm.storm.StormPath;
import io.lbynb.islandstorm.storm.StormPathPoint;
import io.lbynb.islandstorm.weather.WeatherType;
import io.lbynb.islandstorm.wind.WindState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 风暴路径查询、整体设置与生命周期控制（启动/暂停/继续/停止/删除）。 */
public class StormPathApiHandler {

    private final IslandStormPlugin plugin;

    public StormPathApiHandler(IslandStormPlugin plugin) {
        this.plugin = plugin;
    }

    /** GET /api/storm/path → 所有风暴路径（含当前中心与中心处分区天气）。 */
    public Object get() {
        List<Map<String, Object>> list = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (StormPath p : plugin.stormPathManager().all()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.id());
            m.put("type", p.type().name());
            m.put("typeDisplay", p.type().displayName());
            m.put("icon", p.type().icon());
            m.put("world", p.world());
            m.put("radius", p.radius());
            m.put("active", p.active());
            m.put("paused", p.paused());
            m.put("curved", p.curved());
            m.put("ended", p.startEpochMillis() > 0 && !p.active());
            m.put("blockDamageEnabled", p.blockDamageEnabled());
            m.put("blockDamageLevel", p.blockDamageLevel());
            List<Map<String, Object>> pts = new ArrayList<>();
            for (StormPathPoint pt : p.points()) {
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("x", pt.x());
                pm.put("z", pt.z());
                pm.put("arriveAfterSeconds", pt.arriveAfterSeconds());
                pm.put("intensity", pt.intensity());
                pts.add(pm);
            }
            m.put("points", pts);
            double[] c = p.centerAt(now);
            if (c != null) {
                Map<String, Object> center = new LinkedHashMap<>();
                center.put("x", c[0]);
                center.put("z", c[1]);
                m.put("center", center);
                // 中心处「分区天气」：台风类型 + 该处有效风（含本风暴增益与所在区域叠加）
                double intensity = p.intensityAt(now);
                m.put("intensity", intensity);
                m.put("dangerLevel", p.type().dangerLevel().name());
                m.put("dangerColor", p.type().dangerLevel().hexColor());
                fillCenterWind(m, p, c, intensity);
            }
            list.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("storms", list);
        return out;
    }

    /** 计算并写入中心处的风速/风向/等级；失败时回落到「类型默认风速 × 中心增益」估算。 */
    private void fillCenterWind(Map<String, Object> m, StormPath p, double[] c, double intensity) {
        try {
            World w = Bukkit.getWorld(p.world());
            if (w != null) {
                WindState ws = plugin.windManager().effectiveWindAt(new Location(w, c[0], 64, c[1]));
                m.put("centerWindSpeed", (int) Math.round(ws.speed()));
                m.put("centerWindDirection", ws.direction().name());
                m.put("centerWindLevel", ws.levelLabel());
                return;
            }
        } catch (Throwable ignored) {
            // 回落到下方估算
        }
        // 中心增益系数为 2.0（dist=0），乘以当前段强度
        int est = (int) Math.round(p.type().defaultWindSpeed() * 2.0 * Math.max(0.1, intensity));
        m.put("centerWindSpeed", est);
        m.put("centerWindDirection", p.type().defaultWindDirection().name());
        m.put("centerWindLevel", "");
    }

    /**
     * POST /api/storm/path/set
     * {id, type, world, radius, curved?, blockDamageEnabled?, blockDamageLevel?, active?,
     *  points:[{x,z,arriveAfterSeconds,intensity?}]}
     * 整体创建或覆盖一条风暴路径。
     */
    public Object set(JsonObject b) {
        String id = str(b, "id");
        if (id == null || id.isEmpty()) throw ApiException.badRequest("缺少风暴 id");
        WeatherType type = WeatherType.fromString(str(b, "type"), WeatherType.TYPHOON);
        if (type != WeatherType.TYPHOON && type != WeatherType.EXTREME_STORM) {
            throw ApiException.badRequest("风暴类型只能是 TYPHOON 或 EXTREME_STORM");
        }
        String world = str(b, "world");
        if (world == null) world = plugin.configManager().mapDefaultWorld();
        double radius = b.has("radius") ? b.get("radius").getAsDouble() : type.defaultRadius();

        StormPath path = new StormPath(id, type, world, radius);
        if (b.has("curved")) path.setCurved(b.get("curved").getAsBoolean());
        if (b.has("blockDamageEnabled")) path.setBlockDamageEnabled(b.get("blockDamageEnabled").getAsBoolean());
        if (b.has("blockDamageLevel")) path.setBlockDamageLevel(b.get("blockDamageLevel").getAsInt());

        if (b.has("points") && b.get("points").isJsonArray()) {
            JsonArray arr = b.getAsJsonArray("points");
            for (JsonElement el : arr) {
                JsonObject p = el.getAsJsonObject();
                double x = p.get("x").getAsDouble();
                double z = p.get("z").getAsDouble();
                long sec = p.has("arriveAfterSeconds") ? p.get("arriveAfterSeconds").getAsLong() : 0L;
                double intensity = p.has("intensity") ? p.get("intensity").getAsDouble() : 1.0;
                path.addPoint(new StormPathPoint(x, z, sec * 1000L, intensity));
            }
        }
        boolean active = b.has("active") && b.get("active").getAsBoolean();
        if (active && !path.points().isEmpty()) {
            path.start(System.currentTimeMillis());
        }
        plugin.stormPathManager().add(path);
        return get();
    }

    /** POST /api/storm/path/start {id} → 启动（需有点）。 */
    public Object start(JsonObject b) {
        StormPath p = require(b);
        if (p.points().isEmpty()) throw ApiException.badRequest("该路径还没有任何点，无法启动");
        p.start(System.currentTimeMillis());
        plugin.stormPathManager().save();
        return get();
    }

    /** POST /api/storm/path/pause {id} → 暂停（冻结进度）。 */
    public Object pause(JsonObject b) {
        StormPath p = require(b);
        if (!p.active() || p.paused()) throw ApiException.badRequest("该风暴未在运行或已暂停");
        p.pause(System.currentTimeMillis());
        plugin.stormPathManager().save();
        return get();
    }

    /** POST /api/storm/path/resume {id} → 继续。 */
    public Object resume(JsonObject b) {
        StormPath p = require(b);
        if (!p.paused()) throw ApiException.badRequest("该风暴未处于暂停状态");
        p.resume(System.currentTimeMillis());
        plugin.stormPathManager().save();
        return get();
    }

    /** POST /api/storm/path/stop {id} → 停止（取消显示，可重新启动）。 */
    public Object stop(JsonObject b) {
        StormPath p = require(b);
        p.stop();
        plugin.stormPathManager().save();
        return get();
    }

    /** POST /api/storm/path/delete {id} → 彻底删除。 */
    public Object delete(JsonObject b) {
        String id = str(b, "id");
        if (id == null || id.isEmpty()) throw ApiException.badRequest("缺少风暴 id");
        if (!plugin.stormPathManager().remove(id)) throw ApiException.notFound("风暴不存在：" + id);
        return get();
    }

    private StormPath require(JsonObject b) {
        String id = str(b, "id");
        if (id == null || id.isEmpty()) throw ApiException.badRequest("缺少风暴 id");
        StormPath p = plugin.stormPathManager().get(id);
        if (p == null) throw ApiException.notFound("风暴不存在：" + id);
        return p;
    }

    private static String str(JsonObject o, String k) {
        return (o != null && o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : null;
    }
}
