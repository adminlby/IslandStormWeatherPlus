package io.lbynb.islandstorm.storm;

import io.lbynb.islandstorm.weather.WeatherType;

import java.util.ArrayList;
import java.util.List;

/**
 * 风暴路径：由多个 {@link StormPathPoint} 组成的横移路径。仅 TYPHOON / EXTREME_STORM 使用。
 *
 * <p>风暴中心随时间沿路径线性插值移动；半径内玩家受到增强风，越靠近中心风力越强。
 * {@code startEpochMillis<=0} 表示尚未启动。</p>
 */
public class StormPath {

    private final String id;
    private WeatherType type;
    private String world;
    private boolean active;
    private double radius;
    private boolean blockDamageEnabled;
    private int blockDamageLevel;
    private final List<StormPathPoint> points = new ArrayList<>();

    /** 路径启动的绝对现实时间；<=0 表示未启动。 */
    private long startEpochMillis;

    public StormPath(String id, WeatherType type, String world, double radius) {
        this.id = id;
        this.type = type;
        this.world = world;
        this.radius = radius;
        this.active = false;
        this.blockDamageEnabled = type.allowBlockDamage();
        this.blockDamageLevel = type.defaultDamageLevel();
        this.startEpochMillis = -1;
    }

    public String id() {
        return id;
    }

    public WeatherType type() {
        return type;
    }

    public void setType(WeatherType type) {
        this.type = type;
    }

    public String world() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public boolean active() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public double radius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    public boolean blockDamageEnabled() {
        return blockDamageEnabled;
    }

    public void setBlockDamageEnabled(boolean v) {
        this.blockDamageEnabled = v;
    }

    public int blockDamageLevel() {
        return blockDamageLevel;
    }

    public void setBlockDamageLevel(int v) {
        this.blockDamageLevel = v;
    }

    public List<StormPathPoint> points() {
        return points;
    }

    public void addPoint(StormPathPoint p) {
        points.add(p);
    }

    public long startEpochMillis() {
        return startEpochMillis;
    }

    /** 启动路径：记录起点时间并置为 active。 */
    public void start(long nowMillis) {
        this.startEpochMillis = nowMillis;
        this.active = true;
    }

    public void stop() {
        this.active = false;
    }

    /** 路径总时长（最后一个点的到达时间），无点返回 0。 */
    public long totalMillis() {
        if (points.isEmpty()) return 0;
        return points.get(points.size() - 1).arriveAfterMillis();
    }

    /**
     * 计算当前风暴中心坐标（X/Z）。未启动或无点返回 null。
     * 在首点之前停在首点；末点之后停在末点。
     */
    public double[] centerAt(long nowMillis) {
        if (points.isEmpty() || startEpochMillis <= 0) return null;
        long elapsed = nowMillis - startEpochMillis;
        if (elapsed <= points.get(0).arriveAfterMillis()) {
            return new double[]{points.get(0).x(), points.get(0).z()};
        }
        for (int i = 0; i < points.size() - 1; i++) {
            StormPathPoint a = points.get(i);
            StormPathPoint b = points.get(i + 1);
            if (elapsed >= a.arriveAfterMillis() && elapsed <= b.arriveAfterMillis()) {
                long span = Math.max(1, b.arriveAfterMillis() - a.arriveAfterMillis());
                double t = (elapsed - a.arriveAfterMillis()) / (double) span;
                double x = a.x() + (b.x() - a.x()) * t;
                double z = a.z() + (b.z() - a.z()) * t;
                return new double[]{x, z};
            }
        }
        StormPathPoint last = points.get(points.size() - 1);
        return new double[]{last.x(), last.z()};
    }

    /** 路径是否已走完（末点之后）。 */
    public boolean isFinished(long nowMillis) {
        if (points.isEmpty() || startEpochMillis <= 0) return false;
        return (nowMillis - startEpochMillis) > totalMillis();
    }
}
