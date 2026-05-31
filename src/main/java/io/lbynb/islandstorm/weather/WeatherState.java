package io.lbynb.islandstorm.weather;

/**
 * 当前（全局）天气状态快照：天气类型 + 起止时间（绝对现实毫秒）+ 来源。
 *
 * <p>风状态单独由 WindManager 持有；需要组合展示时（广播/HTML/预报）在调用处合并。
 * 时长统一归一化为绝对结束时间 {@code endEpochMillis}，无论来源是现实时间还是 MC 时间
 * （MC 时间由 P2 的 GameTimeUtil 换算为现实毫秒）。{@code endEpochMillis <= 0} 表示永久。</p>
 */
public class WeatherState {

    private final WeatherType type;
    private final long startEpochMillis;
    private final long endEpochMillis;
    private final String source;

    public WeatherState(WeatherType type, long startEpochMillis, long endEpochMillis, String source) {
        this.type = type;
        this.startEpochMillis = startEpochMillis;
        this.endEpochMillis = endEpochMillis;
        this.source = source;
    }

    public WeatherType type() {
        return type;
    }

    public long startEpochMillis() {
        return startEpochMillis;
    }

    public long endEpochMillis() {
        return endEpochMillis;
    }

    public String source() {
        return source;
    }

    public boolean isInfinite() {
        return endEpochMillis <= 0;
    }

    public boolean isExpired(long now) {
        return !isInfinite() && now >= endEpochMillis;
    }

    /** 剩余毫秒；永久返回 -1。 */
    public long remainingMillis(long now) {
        return isInfinite() ? -1 : Math.max(0, endEpochMillis - now);
    }
}
