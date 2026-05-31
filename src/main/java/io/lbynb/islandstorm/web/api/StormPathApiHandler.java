package io.lbynb.islandstorm.web.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.lbynb.islandstorm.IslandStormPlugin;
import io.lbynb.islandstorm.storm.StormPath;
import io.lbynb.islandstorm.storm.StormPathPoint;
import io.lbynb.islandstorm.weather.WeatherType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 风暴路径查询与设置（网页地图拖拽得到的点集整体提交）。 */
public class StormPathApiHandler {

    private final IslandStormPlugin plugin;

    public StormPathApiHandler(IslandStormPlugin plugin) {
        this.plugin = plugin;
    }

    /** GET /api/storm/path → 所有风暴路径。 */
    public Object get() {
        List<Map<String, Object>> list = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (StormPath p : plugin.stormPathManager().all()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.id());
            m.put("type", p.type().name());
            m.put("world", p.world());
            m.put("radius", p.radius());
            m.put("active", p.active());
            m.put("blockDamageEnabled", p.blockDamageEnabled());
            m.put("blockDamageLevel", p.blockDamageLevel());
            List<Map<String, Object>> pts = new ArrayList<>();
            for (StormPathPoint pt : p.points()) {
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("x", pt.x());
                pm.put("z", pt.z());
                pm.put("arriveAfterSeconds", pt.arriveAfterSeconds());
                pts.add(pm);
            }
            m.put("points", pts);
            double[] c = p.centerAt(now);
            if (c != null) {
                Map<String, Object> center = new LinkedHashMap<>();
                center.put("x", c[0]);
                center.put("z", c[1]);
                m.put("center", center);
            }
            list.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("storms", list);
        return out;
    }

    /**
     * POST /api/storm/path/set
     * {id, type, world, radius, blockDamageEnabled?, blockDamageLevel?, active?, points:[{x,z,arriveAfterSeconds}]}
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
        if (b.has("blockDamageEnabled")) path.setBlockDamageEnabled(b.get("blockDamageEnabled").getAsBoolean());
        if (b.has("blockDamageLevel")) path.setBlockDamageLevel(b.get("blockDamageLevel").getAsInt());

        if (b.has("points") && b.get("points").isJsonArray()) {
            JsonArray arr = b.getAsJsonArray("points");
            for (JsonElement el : arr) {
                JsonObject p = el.getAsJsonObject();
                double x = p.get("x").getAsDouble();
                double z = p.get("z").getAsDouble();
                long sec = p.has("arriveAfterSeconds") ? p.get("arriveAfterSeconds").getAsLong() : 0L;
                path.addPoint(new StormPathPoint(x, z, sec * 1000L));
            }
        }
        boolean active = b.has("active") && b.get("active").getAsBoolean();
        if (active && !path.points().isEmpty()) {
            path.start(System.currentTimeMillis());
        }
        plugin.stormPathManager().add(path);
        return get();
    }

    private static String str(JsonObject o, String k) {
        return (o != null && o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : null;
    }
}
