package io.lbynb.islandstorm.weather;

import io.lbynb.islandstorm.wind.WindDirection;

/**
 * 预报 / 排期条目。在「纯人工」模型下，预报就是导演预先排好的天气时间线：
 * 每条记录一种天气、对应风速风向、危险等级与持续时长。
 *
 * <p>当前天气到期后（方案 C），WeatherManager 会取排期里的下一条应用。</p>
 */
public class ForecastEntry {

    private final WeatherType type;
    private final double windSpeed;
    private final WindDirection windDirection;
    private final DangerLevel dangerLevel;
    private final long durationMillis;

    public ForecastEntry(WeatherType type, double windSpeed, WindDirection windDirection,
                         DangerLevel dangerLevel, long durationMillis) {
        this.type = type;
        this.windSpeed = windSpeed;
        this.windDirection = windDirection;
        this.dangerLevel = dangerLevel;
        this.durationMillis = durationMillis;
    }

    /** 用天气类型的默认属性快速构造一条排期。 */
    public static ForecastEntry of(WeatherType type, long durationMillis) {
        return new ForecastEntry(type, type.defaultWindSpeed(), type.defaultWindDirection(),
                type.dangerLevel(), durationMillis);
    }

    public WeatherType type() {
        return type;
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

    public long durationMillis() {
        return durationMillis;
    }
}
