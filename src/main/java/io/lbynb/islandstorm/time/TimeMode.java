package io.lbynb.islandstorm.time;

/** 时间模式：现实时间 / Minecraft 时间。所有涉及时长、到达、预警、预报的功能都需双轨支持。 */
public enum TimeMode {
    REAL_TIME,
    MC_TIME;

    public static TimeMode fromString(String s, TimeMode def) {
        if (s == null) return def;
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
