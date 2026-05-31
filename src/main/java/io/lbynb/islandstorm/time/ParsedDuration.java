package io.lbynb.islandstorm.time;

/**
 * 解析后的时长：同时持有 MC ticks 与现实毫秒两种表示。
 *
 * <p>无论来源是现实时间还是 MC 时间，都按 20 TPS（1 tick = 50ms）互相换算，
 * 以便统一用现实毫秒做调度，同时保留 ticks 供 MC 时间展示。</p>
 */
public class ParsedDuration {

    private final TimeMode mode;
    private final long ticks;
    private final long realMillis;
    private final String raw;

    public ParsedDuration(TimeMode mode, long ticks, long realMillis, String raw) {
        this.mode = mode;
        this.ticks = ticks;
        this.realMillis = realMillis;
        this.raw = raw;
    }

    public TimeMode mode() {
        return mode;
    }

    public long ticks() {
        return ticks;
    }

    public long realMillis() {
        return realMillis;
    }

    public String raw() {
        return raw;
    }
}
