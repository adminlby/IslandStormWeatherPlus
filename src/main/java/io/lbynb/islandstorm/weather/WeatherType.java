package io.lbynb.islandstorm.weather;

import io.lbynb.islandstorm.wind.WindDirection;

/**
 * 天气类型枚举。每种天气内置一组默认属性（显示名 / 描述 / 图标 / 默认时长 /
 * 默认风速风向 / 是否下雨打雷 / 能见度 / 危险等级 / 是否允许破坏方块 / 默认破坏等级 / 默认影响半径）。
 *
 * <p>这些只是「默认值」；导演可通过指令、网页或配置在具体一次设置中覆盖。</p>
 */
public enum WeatherType {
    //            显示名      描述                          图标  时长 风速 风向            雨     雷     能见度  危险等级            破坏  级 半径
    CLEAR("晴天", "天空晴朗，海岛平静。", "☀", 30, 5, WindDirection.NE, false, false, "良好", DangerLevel.NONE, false, 0, 0),
    CLOUDY("多云", "云层增多，微风拂面。", "⛅", 30, 10, WindDirection.NE, false, false, "良好", DangerLevel.LOW, false, 0, 0),
    RAIN("小雨", "细雨绵绵，地面湿滑。", "🌦", 20, 20, WindDirection.NE, true, false, "较好", DangerLevel.LOW, false, 0, 0),
    HEAVY_RAIN("暴雨", "暴雨倾盆，视线受阻。", "🌧", 20, 55, WindDirection.NE, true, false, "较差", DangerLevel.MEDIUM, false, 0, 0),
    THUNDERSTORM("雷暴", "电闪雷鸣，狂风骤雨。", "⛈", 15, 70, WindDirection.NE, true, true, "差", DangerLevel.HIGH, false, 0, 0),
    FOG("浓雾", "浓雾弥漫，能见度极低。", "🌫", 20, 10, WindDirection.N, false, false, "极差", DangerLevel.MEDIUM, false, 0, 0),
    WINDY("大风", "狂风大作，站立不稳。", "💨", 20, 80, WindDirection.NE, false, false, "良好", DangerLevel.MEDIUM, false, 0, 0),
    TYPHOON("台风", "台风过境，极度危险。", "🌀", 15, 110, WindDirection.NE, true, true, "极差", DangerLevel.HIGH, true, 2, 120),
    EXTREME_STORM("极端风暴", "毁灭级风暴，万物飘摇。", "🌪", 10, 140, WindDirection.NE, true, true, "极差", DangerLevel.EXTREME, true, 3, 180);

    private final String displayName;
    private final String description;
    private final String icon;
    private final int defaultDurationMinutes;
    private final int defaultWindSpeed;
    private final WindDirection defaultWindDirection;
    private final boolean rain;
    private final boolean thunder;
    private final String visibility;
    private final DangerLevel dangerLevel;
    private final boolean allowBlockDamage;
    private final int defaultDamageLevel;
    private final int defaultRadius;

    WeatherType(String displayName, String description, String icon, int defaultDurationMinutes,
                int defaultWindSpeed, WindDirection defaultWindDirection, boolean rain, boolean thunder,
                String visibility, DangerLevel dangerLevel, boolean allowBlockDamage,
                int defaultDamageLevel, int defaultRadius) {
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
        this.defaultDurationMinutes = defaultDurationMinutes;
        this.defaultWindSpeed = defaultWindSpeed;
        this.defaultWindDirection = defaultWindDirection;
        this.rain = rain;
        this.thunder = thunder;
        this.visibility = visibility;
        this.dangerLevel = dangerLevel;
        this.allowBlockDamage = allowBlockDamage;
        this.defaultDamageLevel = defaultDamageLevel;
        this.defaultRadius = defaultRadius;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public String icon() {
        return icon;
    }

    public int defaultDurationMinutes() {
        return defaultDurationMinutes;
    }

    public int defaultWindSpeed() {
        return defaultWindSpeed;
    }

    public WindDirection defaultWindDirection() {
        return defaultWindDirection;
    }

    /** 是否下雨（用于原版天气同步）。 */
    public boolean isRain() {
        return rain;
    }

    /** 是否打雷（用于原版天气同步）。 */
    public boolean isThunder() {
        return thunder;
    }

    public String visibility() {
        return visibility;
    }

    public DangerLevel dangerLevel() {
        return dangerLevel;
    }

    public boolean allowBlockDamage() {
        return allowBlockDamage;
    }

    public int defaultDamageLevel() {
        return defaultDamageLevel;
    }

    public int defaultRadius() {
        return defaultRadius;
    }

    /** 大小写不敏感解析；失败返回 def。 */
    public static WeatherType fromString(String s, WeatherType def) {
        if (s == null) return def;
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
