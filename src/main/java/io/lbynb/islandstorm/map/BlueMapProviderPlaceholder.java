package io.lbynb.islandstorm.map;

import org.bukkit.World;

/**
 * BlueMap 接入占位提供者。第一版不渲染真实瓦片，仅返回提示，
 * 预留未来通过 BlueMap API/瓦片服务集成的位置。
 */
public class BlueMapProviderPlaceholder implements MapProvider {

    @Override
    public String id() {
        return "bluemap";
    }

    @Override
    public MapData getMap(World world, int minX, int minZ, int maxX, int maxZ, int cols) {
        MapData data = new MapData();
        data.provider = "bluemap";
        data.world = world == null ? null : world.getName();
        data.minX = Math.min(minX, maxX);
        data.maxX = Math.max(minX, maxX);
        data.minZ = Math.min(minZ, maxZ);
        data.maxZ = Math.max(minZ, maxZ);
        data.cols = 0;
        data.rows = 0;
        data.cells = new int[0];
        data.note = "BlueMap 集成为占位实现：请在前端嵌入 BlueMap 页面，或在此提供者中接入 BlueMap 瓦片。";
        return data;
    }
}
