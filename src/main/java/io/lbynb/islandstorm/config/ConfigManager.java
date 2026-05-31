package io.lbynb.islandstorm.config;

import io.lbynb.islandstorm.IslandStormPlugin;
import io.lbynb.islandstorm.damage.BlockDamageMode;
import io.lbynb.islandstorm.time.TimeMode;
import io.lbynb.islandstorm.weather.VanillaSyncMode;
import io.lbynb.islandstorm.weather.WeatherType;
import io.lbynb.islandstorm.wind.WindDirection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.Set;

/**
 * 配置管理器：封装对 config.yml 的读取，提供强类型 getter，避免到处硬编码 key。
 * getter 直接读取当前配置（不缓存），因此 {@code /storm reload} 后即时生效。
 */
public class ConfigManager {

    private final IslandStormPlugin plugin;
    private FileConfiguration cfg;

    public ConfigManager(IslandStormPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.reloadConfig();
        this.cfg = plugin.getConfig();
    }

    public FileConfiguration raw() {
        return cfg;
    }

    // ---- 通用 ----
    public boolean debug() {
        return cfg.getBoolean("debug", false);
    }

    public String serverName() {
        return cfg.getString("server-name", "IslandStorm");
    }

    // ---- 天气 ----
    public WeatherType weatherDefaultType() {
        return WeatherType.fromString(cfg.getString("weather.default-weather"), WeatherType.CLEAR);
    }

    public int weatherDefaultDurationMinutes() {
        return cfg.getInt("weather.default-duration-minutes", 30);
    }

    public boolean weatherBroadcastEnabled() {
        return cfg.getBoolean("weather.broadcast-enabled", true);
    }

    public int warnBeforeSeconds() {
        return cfg.getInt("weather.warn-before-seconds", 300);
    }

    // ---- 原版天气同步 ----
    public VanillaSyncMode vanillaSyncMode() {
        return VanillaSyncMode.fromString(cfg.getString("vanilla-sync.mode"), VanillaSyncMode.GLOBAL);
    }

    public boolean vanillaSyncEnabledFor(WeatherType type) {
        return cfg.getBoolean("vanilla-sync.per-weather." + type.name(), true);
    }

    // ---- 风 ----
    public boolean windEnabled() {
        return cfg.getBoolean("wind.enabled", true);
    }

    public double windDefaultSpeed() {
        return cfg.getDouble("wind.default-speed", 15);
    }

    public WindDirection windDefaultDirection() {
        return WindDirection.fromString(cfg.getString("wind.default-direction"), WindDirection.NE);
    }

    public boolean groundEffectEnabled() {
        return cfg.getBoolean("wind.ground-effect-enabled", true);
    }

    public boolean elytraEffectEnabled() {
        return cfg.getBoolean("wind.elytra-effect-enabled", true);
    }

    public double pushMultiplier() {
        return cfg.getDouble("wind.push-multiplier", 0.015);
    }

    public double elytraMultiplier() {
        return cfg.getDouble("wind.elytra-multiplier", 0.035);
    }

    public double dangerousSpeed() {
        return cfg.getDouble("wind.dangerous-speed", 90);
    }

    public double extremeSpeed() {
        return cfg.getDouble("wind.extreme-speed", 120);
    }

    public boolean allowBlowAway() {
        return cfg.getBoolean("wind.allow-blow-away", true);
    }

    public String windBypassPermission() {
        return cfg.getString("wind.bypass-permission", "islandstorm.bypass");
    }

    // ---- 时间（双轨） ----
    public TimeMode timeDefaultMode() {
        return TimeMode.fromString(cfg.getString("time.default-mode"), TimeMode.REAL_TIME);
    }

    public long mcDayTicks() {
        return cfg.getLong("time.minecraft-day-ticks", 24000L);
    }

    public String realTimeZone() {
        return cfg.getString("time.real-time-zone", "Asia/Tokyo");
    }

    public String displayRealTimeFormat() {
        return cfg.getString("time.display-real-time-format", "HH:mm");
    }

    public String displayMcTimeFormat() {
        return cfg.getString("time.display-mc-time-format", "HH:mm");
    }

    public boolean showBothTimeInHtml() {
        return cfg.getBoolean("time.show-both-time-in-html", true);
    }

    public boolean showBothTimeInChat() {
        return cfg.getBoolean("time.show-both-time-in-chat", true);
    }

    // ---- 预报 ----
    public int forecastCount() {
        return cfg.getInt("forecast.count", 3);
    }

    public boolean hourlyEnabled() {
        return cfg.getBoolean("forecast.hourly-enabled", true);
    }

    public int hourlyCount() {
        return cfg.getInt("forecast.hourly-count", 24);
    }

    public double hourlyStepValue() {
        return cfg.getDouble("forecast.hourly-step.value", 1);
    }

    public String hourlyStepUnit() {
        return cfg.getString("forecast.hourly-step.unit", "h");
    }

    public TimeMode hourlyStepMode() {
        return TimeMode.fromString(cfg.getString("forecast.hourly-step.mode"), TimeMode.REAL_TIME);
    }

    public boolean regenerateOnWeatherChange() {
        return cfg.getBoolean("forecast.regenerate-on-weather-change", true);
    }

    public boolean regenerateOnRegionChange() {
        return cfg.getBoolean("forecast.regenerate-on-region-change", true);
    }

    // ---- 网页控制台 ----
    public boolean webEnabled() {
        return cfg.getBoolean("web.enabled", true);
    }

    public String webBind() {
        return cfg.getString("web.bind", "0.0.0.0");
    }

    public int webPort() {
        return cfg.getInt("web.port", 8765);
    }

    public int webTokenExpireMinutes() {
        return cfg.getInt("web.token-expire-minutes", 120);
    }

    public String webStaticFolder() {
        return cfg.getString("web.static-folder", "plugins/IslandStorm/web");
    }

    // ---- 地图 ----
    public String mapDefaultWorld() {
        return cfg.getString("map.default-world", "world");
    }

    public int mapMinX() {
        return cfg.getInt("map.min-x", -1000);
    }

    public int mapMaxX() {
        return cfg.getInt("map.max-x", 1000);
    }

    public int mapMinZ() {
        return cfg.getInt("map.min-z", -1000);
    }

    public int mapMaxZ() {
        return cfg.getInt("map.max-z", 1000);
    }

    public String mapProvider() {
        return cfg.getString("map.provider", "vanilla");
    }

    // ---- 方块破坏 ----
    public boolean blockDamageEnabled() {
        return cfg.getBoolean("block-damage.enabled", false);
    }

    public int blockDamageIntervalTicks() {
        return cfg.getInt("block-damage.interval-ticks", 40);
    }

    public int blockDamageChecksPerRun() {
        return cfg.getInt("block-damage.checks-per-run", 80);
    }

    public int blockDamageHorizontalRadius() {
        return cfg.getInt("block-damage.horizontal-radius", 24);
    }

    public BlockDamageMode blockDamageMode() {
        return BlockDamageMode.fromString(cfg.getString("block-damage.default-mode"), BlockDamageMode.FALLING_BLOCK);
    }

    public int blockDamageMaxY() {
        return cfg.getInt("block-damage.max-y", 120);
    }

    public int blockDamageMinY() {
        return cfg.getInt("block-damage.min-y", 50);
    }

    /** 指定等级的破坏概率（0~1）。 */
    public double blockDamageChance(int level) {
        return cfg.getDouble("block-damage.chance.level-" + level, 0.0);
    }

    public Set<String> blockDamageBlacklist() {
        return new HashSet<>(cfg.getStringList("block-damage.blacklist"));
    }

    public Set<String> blockDamageWhitelist() {
        return new HashSet<>(cfg.getStringList("block-damage.whitelist"));
    }

    // ---- 性能 ----
    public int updateIntervalTicks() {
        return cfg.getInt("performance.update-interval-ticks", 10);
    }

    public boolean onlyAffectSurvival() {
        return cfg.getBoolean("performance.only-affect-survival", true);
    }
}
