package io.lbynb.islandstorm.web.api;

import io.lbynb.islandstorm.IslandStormPlugin;
import io.lbynb.islandstorm.map.BlueMapProviderPlaceholder;
import io.lbynb.islandstorm.map.DynmapProviderPlaceholder;
import io.lbynb.islandstorm.map.MapData;
import io.lbynb.islandstorm.map.MapProvider;
import io.lbynb.islandstorm.map.VanillaMapProvider;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 世界列表与简化地图。地图采样触及世界数据，全部 runSync。 */
public class MapApiHandler {

    private final IslandStormPlugin plugin;

    public MapApiHandler(IslandStormPlugin plugin) {
        this.plugin = plugin;
    }

    /** GET /api/worlds → 世界名列表（仅普通世界优先）。 */
    public Object worlds() {
        return ApiSupport.runSync(plugin, () -> {
            List<String> names = new ArrayList<>();
            for (World w : Bukkit.getWorlds()) names.add(w.getName());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("worlds", names);
            out.put("defaultWorld", plugin.configManager().mapDefaultWorld());
            return out;
        });
    }

    /** GET /api/map?world=&minX=&minZ=&maxX=&maxZ=&cols= → 简化地图 + 区域 + 风暴中心。 */
    public Object map(Map<String, String> q) {
        return ApiSupport.runSync(plugin, () -> {
            String worldName = q.getOrDefault("world", plugin.configManager().mapDefaultWorld());
            World world = Bukkit.getWorld(worldName);
            int minX = parse(q.get("minX"), plugin.configManager().mapMinX());
            int minZ = parse(q.get("minZ"), plugin.configManager().mapMinZ());
            int maxX = parse(q.get("maxX"), plugin.configManager().mapMaxX());
            int maxZ = parse(q.get("maxZ"), plugin.configManager().mapMaxZ());
            int cols = parse(q.get("cols"), 48);

            MapProvider provider = resolveProvider();
            MapData data = provider.getMap(world, minX, minZ, maxX, maxZ, cols);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("map", data);
            out.put("regions", regionsInWorld(worldName));
            out.put("storms", stormsInWorld(worldName));
            // BlueMap 网页地址：供前端「🌍 BlueMap」按钮嵌入/跳转（与所选 provider 无关，始终下发）
            out.put("bluemapWebUrl", plugin.configManager().bluemapWebUrl());
            return out;
        });
    }

    private MapProvider resolveProvider() {
        String id = plugin.configManager().mapProvider();
        if ("bluemap".equalsIgnoreCase(id)) return new BlueMapProviderPlaceholder();
        if ("dynmap".equalsIgnoreCase(id)) return new DynmapProviderPlaceholder();
        return new VanillaMapProvider();
    }

    private List<Map<String, Object>> regionsInWorld(String worldName) {
        List<Map<String, Object>> list = new ArrayList<>();
        plugin.regionManager().all().forEach(r -> {
            if (!r.world().equals(worldName)) return;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", r.name());
            m.put("minX", r.minX());
            m.put("minZ", r.minZ());
            m.put("maxX", r.maxX());
            m.put("maxZ", r.maxZ());
            m.put("weather", r.weather().name());
            m.put("dangerColor", r.dangerLevel().hexColor());
            list.add(m);
        });
        return list;
    }

    private List<Map<String, Object>> stormsInWorld(String worldName) {
        List<Map<String, Object>> list = new ArrayList<>();
        long now = System.currentTimeMillis();
        plugin.stormPathManager().all().forEach(p -> {
            if (!p.world().equals(worldName)) return;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.id());
            m.put("type", p.type().name());
            m.put("radius", p.radius());
            m.put("active", p.active());
            List<Map<String, Object>> pts = new ArrayList<>();
            p.points().forEach(pt -> {
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("x", pt.x());
                pm.put("z", pt.z());
                pm.put("arriveAfterSeconds", pt.arriveAfterSeconds());
                pts.add(pm);
            });
            m.put("points", pts);
            double[] c = p.centerAt(now);
            if (c != null) {
                Map<String, Object> center = new LinkedHashMap<>();
                center.put("x", c[0]);
                center.put("z", c[1]);
                m.put("center", center);
            }
            list.add(m);
        });
        return list;
    }

    private static int parse(String s, int def) {
        if (s == null) return def;
        try {
            return (int) Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
