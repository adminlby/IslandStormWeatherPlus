package io.lbynb.islandstorm.task;

import io.lbynb.islandstorm.damage.BlockDamageManager;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 方块破坏任务：周期触发一轮抽查破坏。实际逻辑（含默认关闭判断、分批、概率、事件、黑白名单）
 * 全部在 {@link BlockDamageManager#runDamagePass()} 中完成，任务本身只负责调度。
 */
public class BlockDamageTask extends BukkitRunnable {

    private final BlockDamageManager damage;

    public BlockDamageTask(BlockDamageManager damage) {
        this.damage = damage;
    }

    @Override
    public void run() {
        damage.runDamagePass();
    }
}
