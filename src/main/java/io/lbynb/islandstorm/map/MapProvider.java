package io.lbynb.islandstorm.map;

import org.bukkit.World;

/**
 * 地图提供者接口。第一版提供 {@link VanillaMapProvider} 简化地图；
 * 预留 {@link BlueMapProviderPlaceholder} / {@link DynmapProviderPlaceholder} 以便未来接入。
 *
 * <p>实现可能需要访问 Bukkit 世界数据，调用方需保证在主线程调用（见 ApiRouter 的 runSync）。</p>
 */
public interface MapProvider {

    /**
     * 生成指定世界、指定范围、指定网格列数的简化地图。
     *
     * @param world 目标世界（可为 null，实现需自行处理）
     * @param minX/minZ/maxX/maxZ 世界坐标范围
     * @param cols 网格列数（行数按范围长宽比自动推导）
     */
    MapData getMap(World world, int minX, int minZ, int maxX, int maxZ, int cols);

    /** 提供者标识：vanilla / bluemap / dynmap。 */
    String id();
}
