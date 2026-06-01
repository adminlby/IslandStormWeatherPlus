package io.lbynb.islandstorm.forecast;

import io.lbynb.islandstorm.config.ConfigManager;
import io.lbynb.islandstorm.storm.StormPathManager;
import io.lbynb.islandstorm.time.GameTimeUtil;
import io.lbynb.islandstorm.time.ParsedDuration;
import io.lbynb.islandstorm.weather.ForecastEntry;
import io.lbynb.islandstorm.weather.WeatherManager;
import io.lbynb.islandstorm.weather.WeatherState;
import io.lbynb.islandstorm.weather.WeatherType;
import io.lbynb.islandstorm.wind.WindDirection;
import io.lbynb.islandstorm.wind.WindManager;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 小时级预报生成器（纯人工，非预测）。
 *
 * <p>做法：把「当前天气剩余时长 + 排期队列 + 末尾默认天气」拼成一条时间线，
 * 再按配置步长（默认 1 现实小时）切成 {@code hourly-count}（默认 24）个时段，
 * 取每个时段起点所处的天气作为该小时的预报。小时预报完全由导演排好的时间线渲染而来。</p>
 */
public class HourlyForecastManager {

    private static final long TICK_MS = 50L;

    private final ConfigManager config;
    private final WeatherManager weather;
    private final WindManager wind;
    private final ForecastManager forecast;
    private final StormPathManager storms;

    public HourlyForecastManager(ConfigManager config, WeatherManager weather,
                                 WindManager wind, ForecastManager forecast,
                                 StormPathManager storms) {
        this.config = config;
        this.weather = weather;
        this.wind = wind;
        this.forecast = forecast;
        this.storms = storms;
    }

    /** 时间线上的一段：某天气在 [start,end) 的真实毫秒偏移内有效（end=MAX 表示无限）。 */
    private static final class Segment {
        WeatherType type;
        double windSpeed;
        WindDirection dir;
        long start;
        long end;
    }

    public List<HourlyForecastEntry> generate() {
        int count = Math.max(1, config.hourlyCount());
        long step = stepMillis();
        long now = System.currentTimeMillis();
        World w = resolveWorld();
        long fullNow = (w != null) ? w.getFullTime() : 0L;

        String forecastWorld = (w != null) ? w.getName() : config.mapDefaultWorld();
        List<Segment> segs = buildTimeline(now);
        List<HourlyForecastEntry> out = new ArrayList<>();
        for (int k = 0; k < count; k++) {
            long off = (long) k * step;
            Segment s = segmentAt(segs, off);
            long realStart = now + off;
            long realEnd = realStart + step;
            long mcStart = fullNow + off / TICK_MS;
            long mcEnd = mcStart + step / TICK_MS;

            // 风暴叠加：该时段若处于某活动台风/极端风暴的时间窗内，则显示风暴天气（覆盖排期，
            // 因为风暴是更强的天气事件），让小时预报在风暴期间不再「始终晴天」。
            WeatherType type = s.type;
            double windSpeed = s.windSpeed;
            WindDirection dir = s.dir;
            WeatherType stormType = (storms != null) ? storms.ongoingStormTypeAt(realStart, forecastWorld) : null;
            if (stormType != null) {
                type = stormType;
                windSpeed = stormType.defaultWindSpeed();
                dir = stormType.defaultWindDirection();
            }

            out.add(new HourlyForecastEntry(k, type, realStart, realEnd, mcStart, mcEnd,
                    windSpeed, dir, type.dangerLevel(), config.timeDefaultMode()));
        }
        return out;
    }

    private long stepMillis() {
        ParsedDuration pd = GameTimeUtil.parse(trimNum(config.hourlyStepValue()) + config.hourlyStepUnit(),
                config.hourlyStepMode());
        if (pd == null || pd.realMillis() <= 0) return 3_600_000L; // 默认 1 小时
        return pd.realMillis();
    }

    private static String trimNum(double d) {
        return (d == Math.floor(d)) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private List<Segment> buildTimeline(long now) {
        List<Segment> segs = new ArrayList<>();
        long cursor = 0;

        WeatherState cur = weather.current();
        if (cur != null) {
            Segment s0 = new Segment();
            s0.type = cur.type();
            s0.windSpeed = wind.global().speed();
            s0.dir = wind.global().direction();
            s0.start = 0;
            long rem = cur.remainingMillis(now);
            if (rem < 0) {
                s0.end = Long.MAX_VALUE;
                segs.add(s0);
                return segs; // 当前天气永久 → 整段时间线都是它
            }
            s0.end = rem;
            segs.add(s0);
            cursor = rem;
        }

        for (ForecastEntry e : forecast.all()) {
            Segment s = new Segment();
            s.type = e.type();
            s.windSpeed = e.windSpeed();
            s.dir = e.windDirection();
            s.start = cursor;
            long dur = e.durationMillis() <= 0 ? Long.MAX_VALUE : e.durationMillis();
            s.end = (dur == Long.MAX_VALUE) ? Long.MAX_VALUE : cursor + dur;
            segs.add(s);
            if (s.end == Long.MAX_VALUE) return segs;
            cursor = s.end;
        }

        // 末尾补默认天气直到无限
        WeatherType def = config.weatherDefaultType();
        Segment tail = new Segment();
        tail.type = def;
        tail.windSpeed = def.defaultWindSpeed();
        tail.dir = def.defaultWindDirection();
        tail.start = cursor;
        tail.end = Long.MAX_VALUE;
        segs.add(tail);
        return segs;
    }

    private Segment segmentAt(List<Segment> segs, long off) {
        for (Segment s : segs) {
            if (off >= s.start && off < s.end) return s;
        }
        return segs.get(segs.size() - 1);
    }

    private World resolveWorld() {
        World w = Bukkit.getWorld(config.mapDefaultWorld());
        if (w == null && !Bukkit.getWorlds().isEmpty()) {
            w = Bukkit.getWorlds().get(0);
        }
        return w;
    }
}
