package io.lbynb.islandstorm.weather;

/**
 * 原版天气同步模式。
 *
 * <ul>
 *   <li>{@code GLOBAL} —— 按当前全局天气同步所有主世界的原版天气。</li>
 *   <li>{@code REGIONAL} —— 不做全局同步，由各区域天气分别驱动原版天气（见 P3）。</li>
 *   <li>{@code INDEPENDENT} —— 完全独立，永不改动原版天气。</li>
 * </ul>
 */
public enum VanillaSyncMode {
    GLOBAL,
    REGIONAL,
    INDEPENDENT;

    public static VanillaSyncMode fromString(String s, VanillaSyncMode def) {
        if (s == null) return def;
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
