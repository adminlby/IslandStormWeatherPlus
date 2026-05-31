package io.lbynb.islandstorm.region;

import io.lbynb.islandstorm.weather.DangerLevel;
import io.lbynb.islandstorm.weather.WeatherType;
import io.lbynb.islandstorm.wind.WindDirection;
import org.bukkit.Location;

/**
 * 矩形区域天气（第一版只支持矩形，按 X/Z 平面圈定，不限 Y）。
 *
 * <p>每个区域可独立设置天气、风速、风向、危险等级、持续时长与是否允许方块破坏。
 * 数据持久化到 regions.yml。{@code endEpochMillis<=0} 表示永久有效。</p>
 */
public class WeatherRegion {

    private final String name;
    private String world;
    private int minX;
    private int minZ;
    private int maxX;
    private int maxZ;

    private WeatherType weather;
    private double windSpeed;
    private WindDirection windDirection;
    private DangerLevel dangerLevel;
    private boolean blockDamageEnabled;
    private int blockDamageLevel;

    /** 绝对到期时间（现实毫秒）；<=0 表示永久。 */
    private long endEpochMillis;

    public WeatherRegion(String name, String world, int minX, int minZ, int maxX, int maxZ,
                         WeatherType weather, double windSpeed, WindDirection windDirection,
                         DangerLevel dangerLevel, boolean blockDamageEnabled, int blockDamageLevel,
                         long endEpochMillis) {
        this.name = name;
        this.world = world;
        // 归一化，保证 min<=max
        this.minX = Math.min(minX, maxX);
        this.maxX = Math.max(minX, maxX);
        this.minZ = Math.min(minZ, maxZ);
        this.maxZ = Math.max(minZ, maxZ);
        this.weather = weather;
        this.windSpeed = windSpeed;
        this.windDirection = windDirection;
        this.dangerLevel = dangerLevel;
        this.blockDamageEnabled = blockDamageEnabled;
        this.blockDamageLevel = blockDamageLevel;
        this.endEpochMillis = endEpochMillis;
    }

    /** 判断某位置是否落在区域内（同世界且 X/Z 在矩形范围内）。 */
    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(world)) return false;
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean isExpired(long now) {
        return endEpochMillis > 0 && now >= endEpochMillis;
    }

    // ---- getters / setters ----
    public String name() {
        return name;
    }

    public String world() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public int minX() {
        return minX;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxZ() {
        return maxZ;
    }

    public void setBounds(int minX, int minZ, int maxX, int maxZ) {
        this.minX = Math.min(minX, maxX);
        this.maxX = Math.max(minX, maxX);
        this.minZ = Math.min(minZ, maxZ);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public WeatherType weather() {
        return weather;
    }

    public void setWeather(WeatherType weather) {
        this.weather = weather;
    }

    public double windSpeed() {
        return windSpeed;
    }

    public void setWindSpeed(double windSpeed) {
        this.windSpeed = windSpeed;
    }

    public WindDirection windDirection() {
        return windDirection;
    }

    public void setWindDirection(WindDirection windDirection) {
        this.windDirection = windDirection;
    }

    public DangerLevel dangerLevel() {
        return dangerLevel;
    }

    public void setDangerLevel(DangerLevel dangerLevel) {
        this.dangerLevel = dangerLevel;
    }

    public boolean blockDamageEnabled() {
        return blockDamageEnabled;
    }

    public void setBlockDamageEnabled(boolean blockDamageEnabled) {
        this.blockDamageEnabled = blockDamageEnabled;
    }

    public int blockDamageLevel() {
        return blockDamageLevel;
    }

    public void setBlockDamageLevel(int blockDamageLevel) {
        this.blockDamageLevel = blockDamageLevel;
    }

    public long endEpochMillis() {
        return endEpochMillis;
    }

    public void setEndEpochMillis(long endEpochMillis) {
        this.endEpochMillis = endEpochMillis;
    }
}
