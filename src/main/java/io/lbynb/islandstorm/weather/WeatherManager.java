package io.lbynb.islandstorm.weather;

import io.lbynb.islandstorm.IslandStormPlugin;
import io.lbynb.islandstorm.config.ConfigManager;
import io.lbynb.islandstorm.util.MessageUtil;
import io.lbynb.islandstorm.wind.WindDirection;
import io.lbynb.islandstorm.wind.WindManager;
import io.lbynb.islandstorm.wind.WindState;
import org.bukkit.World;

import java.util.function.Supplier;

/**
 * 天气管理器（纯人工控制）：只负责保存、应用（原版同步）、广播当前人工设置的天气。
 * 不做任何自动生成或预测。
 *
 * <p>到期行为（方案 C）：当前天气时长到期后，从「排期源」取下一条应用；排期为空则回落默认天气。
 * 排期源由 P2 的 ForecastManager 通过 {@link #setScheduleSupplier(Supplier)} 注入。</p>
 */
public class WeatherManager {

    private final IslandStormPlugin plugin;
    private final ConfigManager config;
    private final WindManager wind;

    private WeatherState current;
    /** 排期源：返回下一条排期，无则返回 null。默认空源。 */
    private Supplier<ForecastEntry> scheduleSupplier = () -> null;
    /** 天气变化回调（广播后触发，用于 HTML 自动生成等）。默认空操作。 */
    private Runnable onWeatherChange = () -> {};

    public WeatherManager(IslandStormPlugin plugin, ConfigManager config, WindManager wind) {
        this.plugin = plugin;
        this.config = config;
        this.wind = wind;
    }

    /** 启用时初始化为默认天气（不广播，避免启动刷屏）。 */
    public void initDefault() {
        WeatherType def = config.weatherDefaultType();
        long dur = config.weatherDefaultDurationMinutes() * 60_000L;
        applyState(def, dur, def.defaultWindSpeed(), def.defaultWindDirection(), "default", false);
    }

    public WeatherState current() {
        return current;
    }

    public WindManager wind() {
        return wind;
    }

    public void setScheduleSupplier(Supplier<ForecastEntry> supplier) {
        this.scheduleSupplier = (supplier == null) ? () -> null : supplier;
    }

    public void setOnWeatherChange(Runnable cb) {
        this.onWeatherChange = (cb == null) ? () -> {} : cb;
    }

    /** 人工设置天气：使用该天气的默认风，并广播。durationMillis<=0 表示永久。 */
    public void setWeather(WeatherType type, long durationMillis, String source) {
        applyState(type, durationMillis, type.defaultWindSpeed(), type.defaultWindDirection(), source, true);
    }

    /** 应用一条完整状态（天气 + 风 + 时长），统一入口。 */
    private void applyState(WeatherType type, long durationMillis, double windSpeed,
                            WindDirection windDir, String source, boolean broadcast) {
        long now = System.currentTimeMillis();
        long end = durationMillis <= 0 ? -1 : now + durationMillis;
        this.current = new WeatherState(type, now, end, source);
        wind.set(windSpeed, windDir);
        applyVanilla();
        if (broadcast) {
            broadcastChange();
        }
        // 天气变化回调：HTML 自动生成等（由主类注入；开关在回调内判断）
        try {
            onWeatherChange.run();
        } catch (Throwable t) {
            plugin.getLogger().warning("天气变化回调异常：" + t.getMessage());
        }
    }

    /** 按混合模式把当前天气同步到原版天气。 */
    public void applyVanilla() {
        if (current == null) return;
        VanillaSyncMode mode = config.vanillaSyncMode();
        // REGIONAL 由各区域驱动（P3）；INDEPENDENT 不动原版。
        if (mode != VanillaSyncMode.GLOBAL) return;
        if (!config.vanillaSyncEnabledFor(current.type())) return;

        boolean rain = current.type().isRain();
        boolean thunder = current.type().isThunder();
        for (World w : plugin.getServer().getWorlds()) {
            if (w.getEnvironment() != World.Environment.NORMAL) continue;
            w.setStorm(rain);
            w.setThundering(thunder);
            // 用很长的持续时间「钉住」当前天气，避免原版天气循环来回切换。
            w.setWeatherDuration(Integer.MAX_VALUE);
            w.setThunderDuration(thunder ? Integer.MAX_VALUE : 0);
            try {
                w.setClearWeatherDuration(rain ? 0 : Integer.MAX_VALUE);
            } catch (Throwable ignored) {
                // 个别核心可能不支持该 API，忽略。
            }
        }
    }

    /** 广播当前天气变化（读 messages.yml 模板）。 */
    public void broadcastChange() {
        if (!config.weatherBroadcastEnabled() || current == null) return;
        WindState ws = wind.global();
        String tmpl = MessageUtil.msg("weather-changed",
                "&f天气变化：当前天气已变为 &b{weather}&f，风速 &b{speed} km/h&f，风向 &b{direction}&f。");
        MessageUtil.broadcast(MessageUtil.format(tmpl,
                "weather", current.type().displayName(),
                "speed", String.valueOf((int) ws.speed()),
                "direction", ws.direction().name()));
    }

    /** 由 WeatherCycleTask 周期调用：到期则推进到排期下一条，空则回落默认。 */
    public void tickCheck() {
        if (current == null) return;
        if (current.isExpired(System.currentTimeMillis())) {
            ForecastEntry next = scheduleSupplier.get();
            if (next != null) {
                applyState(next.type(), next.durationMillis(), next.windSpeed(),
                        next.windDirection(), "schedule", true);
            } else {
                // 排期为空 → 回落默认天气（带广播，让观众看到变化）
                WeatherType def = config.weatherDefaultType();
                long dur = config.weatherDefaultDurationMinutes() * 60_000L;
                applyState(def, dur, def.defaultWindSpeed(), def.defaultWindDirection(), "default", true);
            }
        }
    }
}
