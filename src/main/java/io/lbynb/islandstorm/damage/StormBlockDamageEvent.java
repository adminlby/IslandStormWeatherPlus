package io.lbynb.islandstorm.damage;

import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * 风暴/区域破坏某个方块前触发的自定义事件。其他插件可监听并取消，以保护特定方块。
 *
 * <p>事件可取消；监听方也可修改 {@link #setMode(BlockDamageMode)} 改变破坏表现。</p>
 */
public class StormBlockDamageEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Block block;
    private final int level;
    private final String source;
    private BlockDamageMode mode;
    private boolean cancelled;

    public StormBlockDamageEvent(Block block, int level, BlockDamageMode mode, String source) {
        this.block = block;
        this.level = level;
        this.mode = mode;
        this.source = source;
    }

    public Block getBlock() {
        return block;
    }

    /** 触发本次破坏的破坏等级。 */
    public int getLevel() {
        return level;
    }

    /** 破坏来源描述（如 storm:typhoon-001 / region:main-island / global）。 */
    public String getSource() {
        return source;
    }

    public BlockDamageMode getMode() {
        return mode;
    }

    public void setMode(BlockDamageMode mode) {
        this.mode = mode;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
