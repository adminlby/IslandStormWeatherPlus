package io.lbynb.islandstorm.wind;

import org.bukkit.util.Vector;

/**
 * 八方位风向。
 *
 * <p><b>约定：</b>风向表示风「吹向」的方位（即对玩家的推动方向）。
 * Minecraft 坐标中 +X 为东、+Z 为南、-Z 为北。</p>
 */
public enum WindDirection {
    N(0, 0, -1),
    NE(45, 1, -1),
    E(90, 1, 0),
    SE(135, 1, 1),
    S(180, 0, 1),
    SW(225, -1, 1),
    W(270, -1, 0),
    NW(315, -1, -1);

    private final int bearing;
    private final double dx;
    private final double dz;

    WindDirection(int bearing, double dx, double dz) {
        this.bearing = bearing;
        this.dx = dx;
        this.dz = dz;
    }

    /** 方位角（度），N=0 顺时针。 */
    public int bearing() {
        return bearing;
    }

    /** 风吹向的单位向量（y=0）。 */
    public Vector toVector() {
        Vector v = new Vector(dx, 0, dz);
        return v.lengthSquared() == 0 ? v : v.normalize();
    }

    public static WindDirection fromString(String s, WindDirection def) {
        if (s == null) return def;
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
