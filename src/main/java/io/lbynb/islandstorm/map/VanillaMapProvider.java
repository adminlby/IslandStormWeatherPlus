package io.lbynb.islandstorm.map;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * 原版简化地图提供者：在范围内按网格采样每个格子中心的最高方块，
 * 据此分类为海洋 / 陆地 / 未知。
 *
 * <p>性能：采样数 = cols*rows（cols 上限会被限制），仅在地图接口被调用时执行一次，
 * 且由调用方保证在主线程同步执行。不做持续扫描。</p>
 */
public class VanillaMapProvider implements MapProvider {

    @Override
    public String id() {
        return "vanilla";
    }

    @Override
    public MapData getMap(World world, int minX, int minZ, int maxX, int maxZ, int cols) {
        MapData data = new MapData();
        data.provider = "vanilla";
        data.world = world == null ? null : world.getName();
        data.minX = Math.min(minX, maxX);
        data.maxX = Math.max(minX, maxX);
        data.minZ = Math.min(minZ, maxZ);
        data.maxZ = Math.max(minZ, maxZ);

        int c = Math.max(8, Math.min(cols, 96)); // 限制列数，控制采样量
        int spanX = Math.max(1, data.maxX - data.minX);
        int spanZ = Math.max(1, data.maxZ - data.minZ);
        int rows = Math.max(8, Math.min(96, (int) Math.round((double) c * spanZ / spanX)));
        data.cols = c;
        data.rows = rows;
        data.cells = new int[c * rows];

        if (world == null) {
            // 世界不存在：整张图标记未知
            return data;
        }

        int seaLevel = world.getSeaLevel();
        for (int rz = 0; rz < rows; rz++) {
            for (int cx = 0; cx < c; cx++) {
                int x = data.minX + (int) ((cx + 0.5) * spanX / c);
                int z = data.minZ + (int) ((rz + 0.5) * spanZ / rows);
                data.cells[rz * c + cx] = classify(world, x, z, seaLevel);
            }
        }
        return data;
    }

    /** 0=未知 1=海洋 2=陆地。 */
    private int classify(World world, int x, int z, int seaLevel) {
        try {
            int y = world.getHighestBlockYAt(x, z);
            Block top = world.getBlockAt(x, y, z);
            Material m = top.getType();
            String n = m.name();
            if (n.contains("WATER") || n.contains("ICE") || n.contains("KELP") || n.contains("SEAGRASS")) {
                return 1;
            }
            // 最高实心块低于海平面，多半是水域
            if (y <= seaLevel && (m.isAir() || n.contains("WATER"))) {
                return 1;
            }
            return 2;
        } catch (Throwable t) {
            return 0;
        }
    }
}
