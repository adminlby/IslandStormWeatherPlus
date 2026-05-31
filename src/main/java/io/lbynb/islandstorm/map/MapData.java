package io.lbynb.islandstorm.map;

/**
 * 简化地图数据：一个矩形范围内按网格采样得到的格子类型数组。
 *
 * <p>{@code cells} 长度为 {@code cols*rows}，行优先（先 Z 后 X）。
 * 取值：0=未知（灰）、1=海洋（深蓝）、2=陆地（绿）。前端据此绘制简化地图。</p>
 */
public class MapData {

    public String world;
    public int minX;
    public int minZ;
    public int maxX;
    public int maxZ;
    public int cols;
    public int rows;
    public int[] cells;
    /** 提供者类型：vanilla / bluemap / dynmap。 */
    public String provider;
    /** 当使用外部地图提供者时给前端的提示/跳转链接（占位实现填说明文字）。 */
    public String note;

    public MapData() {
    }
}
