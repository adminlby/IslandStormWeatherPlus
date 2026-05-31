package io.lbynb.islandstorm.task;

import io.lbynb.islandstorm.storm.StormPath;
import io.lbynb.islandstorm.storm.StormPathManager;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 风暴移动任务：周期推进风暴路径。实际的中心坐标由 {@link StormPath#centerAt(long)}
 * 按当前时间实时插值得出，本任务主要负责把「已走完」的风暴自动停用。
 */
public class StormMovementTask extends BukkitRunnable {

    private final StormPathManager storms;

    public StormMovementTask(StormPathManager storms) {
        this.storms = storms;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (StormPath p : storms.all()) {
            if (p.active() && p.isFinished(now)) {
                p.stop();
                changed = true;
            }
        }
        if (changed) storms.save();
    }
}
