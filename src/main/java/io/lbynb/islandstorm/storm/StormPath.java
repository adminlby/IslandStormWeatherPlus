package io.lbynb.islandstorm.storm;

import io.lbynb.islandstorm.weather.WeatherType;

import java.util.ArrayList;
import java.util.List;

/**
 * 风暴路径：由多个 {@link StormPathPoint} 组成的横移路径。仅 TYPHOON / EXTREME_STORM 使用。
 *
 * <p>风暴中心随时间沿路径移动（线性或 Catmull-Rom 曲线）；半径内玩家受到增强风，越靠近中心、
 * 该段强度越高风力越强。{@code startEpochMillis<=0} 或未 active 表示尚未生效，此时
 * {@link #centerAt(long)} 返回 {@code null}，所有显示/影响随之消失。</p>
 *
 * <p>支持运行中暂停：{@link #pause(long)} 冻结已用时长，{@link #resume(long)} 以冻结时长续走。</p>
 */
public class StormPath {

    private final String id;
    private WeatherType type;
    private String world;
    private boolean active;
    private double radius;
    private boolean blockDamageEnabled;
    private int blockDamageLevel;
    /** 是否走平滑曲线（Catmull-Rom）；false 为分段直线。 */
    private boolean curved;
    private final List<StormPathPoint> points = new ArrayList<>();

    /** 路径启动的绝对现实时间；<=0 表示未启动。 */
    private long startEpochMillis;
    /** 是否处于暂停状态（active 仍为 true，但时间冻结）。 */
    private boolean paused;
    /** 暂停时冻结的「已用时长」（毫秒）。 */
    private long pausedElapsedMillis;

    public StormPath(String id, WeatherType type, String world, double radius) {
        this.id = id;
        this.type = type;
        this.world = world;
        this.radius = radius;
        this.active = false;
        this.blockDamageEnabled = type.allowBlockDamage();
        this.blockDamageLevel = type.defaultDamageLevel();
        this.curved = false;
        this.startEpochMillis = -1;
        this.paused = false;
        this.pausedElapsedMillis = 0;
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

    public boolean curved() {
        return curved;
    }

    public void setCurved(boolean curved) {
        this.curved = curved;
    }

    public boolean paused() {
        return paused;
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

    /** 启动路径：记录起点时间并置为 active（清除暂停状态）。 */
    public void start(long nowMillis) {
        this.startEpochMillis = nowMillis;
        this.active = true;
        this.paused = false;
        this.pausedElapsedMillis = 0;
    }

    /** 停止路径：置为非 active（可重新 start）。 */
    public void stop() {
        this.active = false;
        this.paused = false;
    }

    /** 暂停：冻结当前已用时长。仅对正在运行（active 且未暂停）的路径有效。 */
    public void pause(long nowMillis) {
        if (!active || paused || startEpochMillis <= 0) return;
        this.pausedElapsedMillis = Math.max(0, nowMillis - startEpochMillis);
        this.paused = true;
    }

    /** 继续：以冻结的已用时长为基准重新计时。 */
    public void resume(long nowMillis) {
        if (!paused) return;
        this.startEpochMillis = nowMillis - pausedElapsedMillis;
        this.paused = false;
    }

    /** 路径总时长（最后一个点的到达时间），无点返回 0。 */
    public long totalMillis() {
        if (points.isEmpty()) return 0;
        return points.get(points.size() - 1).arriveAfterMillis();
    }

    /** 计算「有效已用时长」：未启动/未 active 返回 -1；暂停时返回冻结值。 */
    private long effectiveElapsed(long nowMillis) {
        if (points.isEmpty() || startEpochMillis <= 0 || !active) return -1;
        return paused ? pausedElapsedMillis : (nowMillis - startEpochMillis);
    }

    /**
     * 计算当前风暴中心坐标（X/Z）。未启动、未 active 或无点返回 null（显示/影响随之消失）。
     * 在首点之前停在首点；末点之后停在末点。{@code curved} 时用 Catmull-Rom 平滑插值。
     */
    public double[] centerAt(long nowMillis) {
        long elapsed = effectiveElapsed(nowMillis);
        if (elapsed < 0) return null;
        if (elapsed <= points.get(0).arriveAfterMillis()) {
            return new double[]{points.get(0).x(), points.get(0).z()};
        }
        for (int i = 0; i < points.size() - 1; i++) {
            StormPathPoint a = points.get(i);
            StormPathPoint b = points.get(i + 1);
            if (elapsed >= a.arriveAfterMillis() && elapsed <= b.arriveAfterMillis()) {
                long span = Math.max(1, b.arriveAfterMillis() - a.arriveAfterMillis());
                double t = (elapsed - a.arriveAfterMillis()) / (double) span;
                if (curved && points.size() >= 3) {
                    StormPathPoint p0 = points.get(Math.max(0, i - 1));
                    StormPathPoint p3 = points.get(Math.min(points.size() - 1, i + 2));
                    double x = catmullRom(p0.x(), a.x(), b.x(), p3.x(), t);
                    double z = catmullRom(p0.z(), a.z(), b.z(), p3.z(), t);
                    return new double[]{x, z};
                }
                double x = a.x() + (b.x() - a.x()) * t;
                double z = a.z() + (b.z() - a.z()) * t;
                return new double[]{x, z};
            }
        }
        StormPathPoint last = points.get(points.size() - 1);
        return new double[]{last.x(), last.z()};
    }

    /** 当前所处段的强度倍率（沿段线性插值）；未生效或无点返回 1.0。 */
    public double intensityAt(long nowMillis) {
        long elapsed = effectiveElapsed(nowMillis);
        if (elapsed < 0) return 1.0;
        if (elapsed <= points.get(0).arriveAfterMillis()) return points.get(0).intensity();
        for (int i = 0; i < points.size() - 1; i++) {
            StormPathPoint a = points.get(i);
            StormPathPoint b = points.get(i + 1);
            if (elapsed >= a.arriveAfterMillis() && elapsed <= b.arriveAfterMillis()) {
                long span = Math.max(1, b.arriveAfterMillis() - a.arriveAfterMillis());
                double t = (elapsed - a.arriveAfterMillis()) / (double) span;
                return a.intensity() + (b.intensity() - a.intensity()) * t;
            }
        }
        return points.get(points.size() - 1).intensity();
    }

    /** 当前半径（预留：将来可做每段半径，目前等于路径半径）。 */
    public double radiusAt(long nowMillis) {
        return radius;
    }

    /** 路径是否已走完（末点之后）。暂停或未 active 时永不算走完。 */
    public boolean isFinished(long nowMillis) {
        if (points.isEmpty() || startEpochMillis <= 0 || !active || paused) return false;
        return (nowMillis - startEpochMillis) > totalMillis();
    }

    /** 标准 Catmull-Rom 样条插值（uniform，张力 0.5）。 */
    private static double catmullRom(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return 0.5 * ((2 * p1)
                + (-p0 + p2) * t
                + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2
                + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }
}
