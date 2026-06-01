package io.lbynb.islandstorm.region;

import io.lbynb.islandstorm.IslandStormPlugin;
import io.lbynb.islandstorm.weather.DangerLevel;
import io.lbynb.islandstorm.weather.WeatherType;
import io.lbynb.islandstorm.wind.WindDirection;
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
 * 区域管理器：从 regions.yml 读写矩形区域天气，并提供「某位置所属区域」查询。
 *
 * <p>持久化只保存 duration-minutes（人类可读）；运行时换算成绝对到期时间。
 * 这样 reload / 重启后区域不会因为存了绝对时间戳而立刻过期。</p>
 */
public class RegionManager {

    private final IslandStormPlugin plugin;
    private final File file;
    private final Map<String, WeatherRegion> regions = new LinkedHashMap<>();

    public RegionManager(IslandStormPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "regions.yml");
    }

    public void load() {
        regions.clear();
        if (!file.exists()) {
            // 释放一份带示例的默认文件
            plugin.saveResourceIfAbsent("regions.yml");
        }
        if (!file.exists()) return;

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("regions");
        if (root == null) return;

        long now = System.currentTimeMillis();
        for (String name : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(name);
            if (s == null) continue;
            WeatherType weather = WeatherType.fromString(s.getString("weather"), WeatherType.CLEAR);
            double windSpeed = s.getDouble("wind-speed", weather.defaultWindSpeed());
            WindDirection windDir = WindDirection.fromString(s.getString("wind-direction"),
                    weather.defaultWindDirection());
            DangerLevel danger = DangerLevel.fromString(s.getString("danger-level"), weather.dangerLevel());
            // 区域不再含方块破坏（block-damage-* 为旧字段，读取时忽略）
            int durMin = s.getInt("duration-minutes", 0); // 0 = 永久
            long end = durMin <= 0 ? -1 : now + durMin * 60_000L;

            WeatherRegion region = new WeatherRegion(
                    name,
                    s.getString("world", "world"),
                    s.getInt("min-x"), s.getInt("min-z"),
                    s.getInt("max-x"), s.getInt("max-z"),
                    weather, windSpeed, windDir, danger, end);
            regions.put(name.toLowerCase(), region);
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        long now = System.currentTimeMillis();
        for (WeatherRegion r : regions.values()) {
            String base = "regions." + r.name();
            yml.set(base + ".world", r.world());
            yml.set(base + ".min-x", r.minX());
            yml.set(base + ".max-x", r.maxX());
            yml.set(base + ".min-z", r.minZ());
            yml.set(base + ".max-z", r.maxZ());
            yml.set(base + ".weather", r.weather().name());
            yml.set(base + ".wind-speed", r.windSpeed());
            yml.set(base + ".wind-direction", r.windDirection().name());
            yml.set(base + ".danger-level", r.dangerLevel().name());
            long remainMin = r.endEpochMillis() <= 0 ? 0 : Math.max(0, (r.endEpochMillis() - now) / 60_000L);
            yml.set(base + ".duration-minutes", remainMin);
        }
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("保存 regions.yml 失败：" + e.getMessage());
        }
    }

    public WeatherRegion get(String name) {
        return name == null ? null : regions.get(name.toLowerCase());
    }

    public boolean exists(String name) {
        return name != null && regions.containsKey(name.toLowerCase());
    }

    public Collection<WeatherRegion> all() {
        return new ArrayList<>(regions.values());
    }

    public void add(WeatherRegion region) {
        regions.put(region.name().toLowerCase(), region);
        save();
    }

    public boolean remove(String name) {
        if (name == null) return false;
        boolean removed = regions.remove(name.toLowerCase()) != null;
        if (removed) save();
        return removed;
    }

    /**
     * 查询某位置所属区域（命中第一个匹配的，未过期优先）。
     * 后续如需「重叠区域优先级」，可在此扩展为按面积或显式 priority 排序。
     */
    public WeatherRegion regionAt(Location loc) {
        if (loc == null) return null;
        long now = System.currentTimeMillis();
        for (WeatherRegion r : regions.values()) {
            if (r.isExpired(now)) continue;
            if (r.contains(loc)) return r;
        }
        return null;
    }

    /** 移除所有已过期区域并落盘；返回移除数量。 */
    public int purgeExpired() {
        long now = System.currentTimeMillis();
        List<String> dead = new ArrayList<>();
        for (Map.Entry<String, WeatherRegion> e : regions.entrySet()) {
            if (e.getValue().isExpired(now)) dead.add(e.getKey());
        }
        for (String k : dead) regions.remove(k);
        if (!dead.isEmpty()) save();
        return dead.size();
    }
}
