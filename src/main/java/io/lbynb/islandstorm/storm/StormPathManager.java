package io.lbynb.islandstorm.storm;

import io.lbynb.islandstorm.IslandStormPlugin;
import io.lbynb.islandstorm.weather.WeatherType;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 风暴路径管理器：读写 storm-paths.yml，维护所有风暴路径，并对外提供
 * 「某位置是否处于某风暴半径内、风力增强多少」的查询（供 WindManager 使用）。
 */
public class StormPathManager {

    private final IslandStormPlugin plugin;
    private final File file;
    private final Map<String, StormPath> paths = new LinkedHashMap<>();

    public StormPathManager(IslandStormPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "storm-paths.yml");
    }

    public void load() {
        paths.clear();
        if (!file.exists()) {
            plugin.saveResourceIfAbsent("storm-paths.yml");
        }
        if (!file.exists()) return;

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("storms");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            WeatherType type = WeatherType.fromString(s.getString("type"), WeatherType.TYPHOON);
            StormPath path = new StormPath(id, type, s.getString("world", "world"),
                    s.getDouble("radius", type.defaultRadius()));
            path.setBlockDamageEnabled(s.getBoolean("block-damage-enabled", type.allowBlockDamage()));
            path.setBlockDamageLevel(s.getInt("block-damage-level", type.defaultDamageLevel()));
            path.setCurved(s.getBoolean("curved", false));

            List<Map<?, ?>> pts = s.getMapList("points");
            for (Map<?, ?> m : pts) {
                double x = toDouble(m.get("x"));
                double z = toDouble(m.get("z"));
                long sec = toLong(m.get("arrive-after-seconds"));
                double intensity = m.containsKey("intensity") ? toDouble(m.get("intensity")) : 1.0;
                path.addPoint(new StormPathPoint(x, z, sec * 1000L, intensity));
            }
            // 持久化的 active 仅作记录；重启后需要重新 start 才计时，避免时间基准错乱。
            paths.put(id.toLowerCase(), path);
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (StormPath p : paths.values()) {
            String base = "storms." + p.id();
            yml.set(base + ".type", p.type().name());
            yml.set(base + ".world", p.world());
            yml.set(base + ".active", p.active());
            yml.set(base + ".radius", p.radius());
            yml.set(base + ".block-damage-enabled", p.blockDamageEnabled());
            yml.set(base + ".block-damage-level", p.blockDamageLevel());
            yml.set(base + ".curved", p.curved());
            List<Map<String, Object>> pts = new ArrayList<>();
            for (StormPathPoint pt : p.points()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("x", pt.x());
                m.put("z", pt.z());
                m.put("arrive-after-seconds", pt.arriveAfterSeconds());
                m.put("intensity", pt.intensity());
                pts.add(m);
            }
            yml.set(base + ".points", pts);
        }
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("保存 storm-paths.yml 失败：" + e.getMessage());
        }
    }

    public StormPath get(String id) {
        return id == null ? null : paths.get(id.toLowerCase());
    }

    public boolean exists(String id) {
        return id != null && paths.containsKey(id.toLowerCase());
    }

    public Collection<StormPath> all() {
        return new ArrayList<>(paths.values());
    }

    public void add(StormPath path) {
        paths.put(path.id().toLowerCase(), path);
        save();
    }

    public boolean remove(String id) {
        if (id == null) return false;
        boolean removed = paths.remove(id.toLowerCase()) != null;
        if (removed) save();
        return removed;
    }

    /**
     * 计算某位置受到的风暴风力增益系数（0=不在任何风暴内；1=恰在半径边缘；>1 越靠近中心越大）。
     * 取所有活动风暴中的最大值。返回命中的风暴（用于风向/破坏），未命中返回 null。
     */
    public StormInfluence influenceAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        long now = System.currentTimeMillis();
        StormInfluence best = null;
        for (StormPath p : paths.values()) {
            if (!p.active()) continue;
            if (!p.world().equals(loc.getWorld().getName())) continue;
            double[] c = p.centerAt(now);
            if (c == null) continue;
            double dx = loc.getX() - c[0];
            double dz = loc.getZ() - c[1];
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > p.radius()) continue;
            // 中心系数 2.0，边缘系数 ~0.2，线性插值；再乘以当前段强度（每段可单独配置）
            double factor = (0.2 + 1.8 * (1.0 - dist / Math.max(1.0, p.radius()))) * p.intensityAt(now);
            if (best == null || factor > best.factor) {
                best = new StormInfluence(p, c[0], c[1], dist, factor);
            }
        }
        return best;
    }

    /** 命中风暴的信息快照。 */
    public static final class StormInfluence {
        public final StormPath storm;
        public final double centerX;
        public final double centerZ;
        public final double distance;
        public final double factor;

        StormInfluence(StormPath storm, double centerX, double centerZ, double distance, double factor) {
            this.storm = storm;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.distance = distance;
            this.factor = factor;
        }
    }

    private static double toDouble(Object o) {
        return (o instanceof Number) ? ((Number) o).doubleValue() : 0.0;
    }

    private static long toLong(Object o) {
        return (o instanceof Number) ? ((Number) o).longValue() : 0L;
    }
}
