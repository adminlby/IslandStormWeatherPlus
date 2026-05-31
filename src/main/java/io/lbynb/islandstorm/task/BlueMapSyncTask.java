package io.lbynb.islandstorm.task;

import io.lbynb.islandstorm.map.BlueMapHook;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 周期性把天气区域/风暴标记刷新到 BlueMap（台风中心随时间移动需要持续更新）。
 * 仅在服务端安装了 BlueMap 时由 {@code IslandStormPlugin} 创建并调度。
 */
public class BlueMapSyncTask extends BukkitRunnable {

    private final BlueMapHook hook;

    public BlueMapSyncTask(BlueMapHook hook) {
        this.hook = hook;
    }

    @Override
    public void run() {
        hook.sync();
    }
}
