package io.lbynb.islandstorm.forecast;

import io.lbynb.islandstorm.time.TimeMode;
import io.lbynb.islandstorm.weather.DangerLevel;
import io.lbynb.islandstorm.weather.WeatherType;
import io.lbynb.islandstorm.wind.WindDirection;

/**
 * 小时级预报的一个时段条目。同时携带现实时间区间与 MC 时间区间，满足「双时间」展示要求。
 * 显示名 / 图标 / 能见度 / 描述均由 {@link WeatherType} 派生。
 */
public class HourlyForecastEntry {

    private final int index;
    private final WeatherType weatherType;
    private final long realStartMillis;
    private final long realEndMillis;
    private final long mcStartTick;
    private final long mcEndTick;
    private final double windSpeed;
    private final WindDirection windDirection;
    private final DangerLevel dangerLevel;
    private final TimeMode timeMode;

    public HourlyForecastEntry(int index, WeatherType weatherType,
                               long realStartMillis, long realEndMillis,
                               long mcStartTick, long mcEndTick,
                               double windSpeed, WindDirection windDirection,
                               DangerLevel dangerLevel, TimeMode timeMode) {
        this.index = index;
        this.weatherType = weatherType;
        this.realStartMillis = realStartMillis;
        this.realEndMillis = realEndMillis;
        this.mcStartTick = mcStartTick;
        this.mcEndTick = mcEndTick;
        this.windSpeed = windSpeed;
        this.windDirection = windDirection;
        this.dangerLevel = dangerLevel;
        this.timeMode = timeMode;
    }

    public int index() {
        return index;
    }

    public WeatherType weatherType() {
        return weatherType;
    }

    public String displayName() {
        return weatherType.displayName();
    }

    public String icon() {
        return weatherType.icon();
    }

    public String visibility() {
        return weatherType.visibility();
    }

    public String description() {
        return weatherType.description();
    }

    public long realStartMillis() {
        return realStartMillis;
    }

    public long realEndMillis() {
        return realEndMillis;
    }

    public long mcStartTick() {
        return mcStartTick;
    }

    public long mcEndTick() {
        return mcEndTick;
    }

    public double windSpeed() {
        return windSpeed;
    }

    public WindDirection windDirection() {
        return windDirection;
    }

    public DangerLevel dangerLevel() {
        return dangerLevel;
    }

    public TimeMode timeMode() {
        return timeMode;
    }
}
