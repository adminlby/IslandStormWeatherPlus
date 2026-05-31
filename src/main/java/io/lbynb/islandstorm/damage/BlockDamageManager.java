package io.lbynb.islandstorm.damage;

import io.lbynb.islandstorm.IslandStormPlugin;
import io.lbynb.islandstorm.config.ConfigManager;
import io.lbynb.islandstorm.region.RegionManager;
import io.lbynb.islandstorm.region.WeatherRegion;
import io.lbynb.islandstorm.storm.StormPathManager;
import io.lbynb.islandstorm.weather.WeatherManager;
import io.lbynb.islandstorm.weather.WeatherState;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 方块破坏管理器（默认关闭，需 config.block-damage.enabled 显式开启）。
 *
 * <p>性能安全：不做大范围扫描；每次只在「处于破坏区域内的在线玩家」周围随机抽查
 * {@code checks-per-run} 个方块。破坏前触发 {@link StormBlockDamageEvent}，允许其他插件取消。
 * 默认保护箱子、床、潜影盒、命令方块、刷怪笼、屏障、结构方块、末影箱、基岩等。</p>
 *
 * <p>WorldGuard：检测其是否存在并预留 Hook（{@link #isWorldGuardProtected(Block)}），
 * 第一版为占位实现（不做实际区域查询）。</p>
 */
public class BlockDamageManager {

    private final IslandStormPlugin plugin;
    private final ConfigManager config;
    private final RegionManager regions;
    private final StormPathManager storms;
    private final WeatherManager weather;
    private final Random random = new Random();
    private final boolean worldGuardPresent;

    public BlockDamageManager(IslandStormPlugin plugin, ConfigManager config, RegionManager regions,
                              StormPathManager storms, WeatherManager weather) {
        this.plugin = plugin;
        this.config = config;
        this.regions = regions;
        this.storms = storms;
        this.weather = weather;
        this.worldGuardPresent = Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
        if (worldGuardPresent) {
            plugin.getLogger().info("检测到 WorldGuard：方块破坏将预留保护 Hook（第一版为占位）。");
        }
    }

    /** 计算某位置的有效破坏等级（取 区域 / 风暴 / 全局天气 三者最大值）；0 表示不破坏。 */
    public int effectiveDamageLevelAt(Location loc) {
        if (!config.blockDamageEnabled()) return 0;
        int level = 0;

        WeatherRegion region = regions.regionAt(loc);
        if (region != null && region.blockDamageEnabled()) {
            level = Math.max(level, region.blockDamageLevel());
        }

        StormPathManager.StormInfluence inf = storms.influenceAt(loc);
        if (inf != null && inf.storm.blockDamageEnabled()) {
            level = Math.max(level, inf.storm.blockDamageLevel());
        }

        WeatherState cur = weather.current();
        if (cur != null && cur.type().allowBlockDamage()) {
            level = Math.max(level, cur.type().defaultDamageLevel());
        }
        return level;
    }

    /** 由 BlockDamageTask 调用：执行一轮抽查破坏，返回实际破坏的方块数。 */
    public int runDamagePass() {
        if (!config.blockDamageEnabled()) return 0;

        List<Player> targets = new ArrayList<>();
        List<Integer> levels = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            GameMode gm = p.getGameMode();
            if (gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) continue;
            int lvl = effectiveDamageLevelAt(p.getLocation());
            if (lvl > 0) {
                targets.add(p);
                levels.add(lvl);
            }
        }
        if (targets.isEmpty()) return 0;

        final int checks = Math.max(0, config.blockDamageChecksPerRun());
        final int r = Math.max(1, config.blockDamageHorizontalRadius());
        final int minY = config.blockDamageMinY();
        final int maxY = config.blockDamageMaxY();
        final BlockDamageMode mode = config.blockDamageMode();
        final Set<String> blacklist = config.blockDamageBlacklist();
        final Set<String> whitelist = config.blockDamageWhitelist();

        int broken = 0;
        for (int i = 0; i < checks; i++) {
            int idx = random.nextInt(targets.size());
            Player p = targets.get(idx);
            int level = levels.get(idx);
            Location base = p.getLocation();
            World w = base.getWorld();
            if (w == null) continue;

            int x = base.getBlockX() + random.nextInt(2 * r + 1) - r;
            int z = base.getBlockZ() + random.nextInt(2 * r + 1) - r;
            int yLo = Math.max(minY, w.getMinHeight());
            int yHi = Math.min(maxY, w.getMaxHeight() - 1);
            if (yHi < yLo) continue;
            int y = yLo + random.nextInt(yHi - yLo + 1);

            if (processBlock(w.getBlockAt(x, y, z), level, mode, blacklist, whitelist, "block-damage")) {
                broken++;
            }
        }
        return broken;
    }

    private boolean processBlock(Block b, int level, BlockDamageMode mode,
                                 Set<String> blacklist, Set<String> whitelist, String source) {
        Material m = b.getType();
        if (m.isAir()) return false;
        if (isProtected(m)) return false;
        if (blacklist.contains(m.name())) return false;

        if (!whitelist.isEmpty()) {
            if (!whitelist.contains(m.name())) return false;
        } else {
            int tier = BlockDamageLevel.tierOf(m);
            if (tier == 0 || tier > level) return false;
        }

        double chance = config.blockDamageChance(level);
        if (chance <= 0 || random.nextDouble() > chance) return false;

        if (isWorldGuardProtected(b)) return false;

        StormBlockDamageEvent ev = new StormBlockDamageEvent(b, level, mode, source);
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) return false;

        applyDamage(b, ev.getMode());
        return true;
    }

    private void applyDamage(Block b, BlockDamageMode mode) {
        switch (mode) {
            case DROP:
                b.breakNaturally();
                break;
            case FALLING_BLOCK:
                Location spawn = b.getLocation().add(0.5, 0, 0.5);
                b.getWorld().spawnFallingBlock(spawn, b.getBlockData());
                b.setType(Material.AIR, false);
                break;
            case AIR:
            default:
                b.setType(Material.AIR, false);
                break;
        }
    }

    /** 硬编码受保护方块（与配置黑名单叠加，双保险）。 */
    private boolean isProtected(Material m) {
        String n = m.name();
        if (n.equals("BEDROCK") || n.equals("BARRIER") || n.equals("COMMAND_BLOCK")
                || n.equals("CHAIN_COMMAND_BLOCK") || n.equals("REPEATING_COMMAND_BLOCK")
                || n.equals("STRUCTURE_BLOCK") || n.equals("STRUCTURE_VOID") || n.equals("JIGSAW")
                || n.equals("SPAWNER") || n.equals("CHEST") || n.equals("TRAPPED_CHEST")
                || n.equals("ENDER_CHEST") || n.equals("END_PORTAL_FRAME")
                || n.equals("END_PORTAL") || n.equals("NETHER_PORTAL")) {
            return true;
        }
        return n.endsWith("_BED") || n.endsWith("SHULKER_BOX");
    }

    /** WorldGuard 保护占位 Hook：第一版不做实际区域查询，始终返回 false。 */
    private boolean isWorldGuardProtected(Block b) {
        if (!worldGuardPresent) return false;
        // TODO: 接入 WorldGuard API，查询该方块是否处于禁止破坏的保护区域。
        return false;
    }

    public boolean isWorldGuardPresent() {
        return worldGuardPresent;
    }
}
