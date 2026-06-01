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

        boolean needSave = false;
        long now = System.currentTimeMillis();
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
                double ptRadius = m.containsKey("radius") ? toDouble(m.get("radius")) : 0;
                path.addPoint(new StormPathPoint(x, z, sec * 1000L, intensity, ptRadius));
            }

            // 恢复 active 与计时基准（含暂停状态）：重启/重载后让 active:true 的风暴真正继续运行。
            // 之前把 active 仅作记录，导致 reload 后风暴悄悄停掉，台风不下雨、不破坏、预报不显示。
            boolean active = s.getBoolean("active", false);
            if (active && !path.points().isEmpty()) {
                long startMs = s.getLong("start-epoch-millis", -1L);
                boolean paused = s.getBoolean("paused", false);
                long pausedElapsed = s.getLong("paused-elapsed-millis", 0L);
                if (startMs > 0) {
                    path.restore(startMs, paused, pausedElapsed);
                } else {
                    // 手动在文件里写了 active:true 但没有起点时间 → 从现在开始计时，并回写持久化。
                    path.start(now);
                    needSave = true;
                }
            }
            paths.put(id.toLowerCase(), path);
        }
        // 把新生成的起点时间持久化，避免下次重载又被重置。
        if (needSave) save();
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (StormPath p : paths.values()) {
            String base = "storms." + p.id();
            yml.set(base + ".type", p.type().name());
            yml.set(base + ".world", p.world());
            yml.set(base + ".active", p.active());
            yml.set(base + ".start-epoch-millis", p.startEpochMillis());
            yml.set(base + ".paused", p.paused());
            yml.set(base + ".paused-elapsed-millis", p.pausedElapsedMillis());
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
                m.put("radius", pt.radius());
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
            double radius = p.radiusAt(now); // 每段可单独配置半径
            if (dist > radius) continue;
            // 中心系数 2.0，边缘系数 ~0.2，线性插值；再乘以当前段强度（每段可单独配置）
            double factor = (0.2 + 1.8 * (1.0 - dist / Math.max(1.0, radius))) * p.intensityAt(now);
            if (best == null || factor > best.factor) {
                best = new StormInfluence(p, c[0], c[1], dist, factor);
            }
        }
        return best;
    }

    /** 该世界此刻是否有正在进行的风暴（active 且处于路径时间窗内）。 */
    public boolean hasActiveStormIn(String world) {
        return ongoingStormTypeAt(System.currentTimeMillis(), world) != null;
    }

    /**
     * 给定现实时间，返回该世界「正在进行」的最强风暴类型（EXTREME_STORM 优先于 TYPHOON）；无则 null。
     * 用于：原版天气同步（台风期间下雨打雷）与小时预报时间线叠加。
     *
     * <p>只看「时间窗」而非地理半径——雨/雷是世界级，原版打雷本身无法只在局部半径生效。</p>
     */
    public WeatherType ongoingStormTypeAt(long epochMillis, String world) {
        WeatherType best = null;
        for (StormPath p : paths.values()) {
            if (world != null && !p.world().equals(world)) continue;
            if (!p.isOngoingAt(epochMillis)) continue;
            if (best == null || severity(p.type()) > severity(best)) {
                best = p.type();
            }
        }
        return best;
    }

    private static int severity(WeatherType t) {
        if (t == WeatherType.EXTREME_STORM) return 2;
        if (t == WeatherType.TYPHOON) return 1;
        return 0;
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
