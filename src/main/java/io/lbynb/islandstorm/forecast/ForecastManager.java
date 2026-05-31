package io.lbynb.islandstorm.forecast;

import io.lbynb.islandstorm.weather.ForecastEntry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 预报 / 排期管理器（纯人工）。
 *
 * <p>它持有导演预排的天气时间线（一个有序队列）。当前天气到期时，
 * WeatherManager 通过 {@link #pollNext()} 取出队首作为下一段天气（方案 C）；
 * 预报展示则用 {@link #upcoming(int)} 查看接下来的若干条而不消费它们。</p>
 */
public class ForecastManager {

    private final Deque<ForecastEntry> queue = new ArrayDeque<>();

    public void add(ForecastEntry entry) {
        if (entry != null) queue.addLast(entry);
    }

    public void clear() {
        queue.clear();
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /** 到期推进时调用：取出并移除队首；空则返回 null（由调用方回落默认天气）。 */
    public ForecastEntry pollNext() {
        return queue.pollFirst();
    }

    /** 查看接下来的前 n 条（不移除）。 */
    public List<ForecastEntry> upcoming(int n) {
        List<ForecastEntry> out = new ArrayList<>();
        int i = 0;
        for (ForecastEntry e : queue) {
            if (i++ >= n) break;
            out.add(e);
        }
        return out;
    }

    /** 全部排期的快照（不移除）。 */
    public List<ForecastEntry> all() {
        return new ArrayList<>(queue);
    }
}
