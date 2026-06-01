package io.lbynb.islandstorm.map;

import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.LineMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Line;
import de.bluecolored.bluemap.api.math.Shape;
import io.lbynb.islandstorm.IslandStormPlugin;
import io.lbynb.islandstorm.region.WeatherRegion;
import io.lbynb.islandstorm.storm.StormPath;
import io.lbynb.islandstorm.storm.StormPathPoint;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * BlueMap 接入钩子：把天气区域、台风/极端风暴路径作为标记推送到 BlueMap 的真实地图上。
 *
 * <p><b>软依赖隔离</b>：本类引用了 {@code de.bluecolored.bluemap.api.*}，只有在服务端确实安装了
 * BlueMap 插件时，{@link IslandStormPlugin} 才会实例化它（否则类根本不加载，不会触发
 * {@code NoClassDefFoundError}）。</p>
 *
 * <p><b>标记非持久化</b>：BlueMap 卸载/重载会清空程序化创建的标记，因此插件必须在每次
 * {@link BlueMapAPI#onEnable} 时重建，并由定时任务（{@code BlueMapSyncTask}）周期性刷新——
 * 这样台风中心随时间移动也能在地图上实时更新。</p>
 */
public class BlueMapHook {

    /** 每个世界一个标记集，便于整体显示/隐藏；id 在所有地图间保持一致以便覆盖更新。 */
    private static final String MARKERSET_ID = "islandstorm.weather";
    private static final String MARKERSET_LABEL = "IslandStorm 天气";

    private final IslandStormPlugin plugin;

    /** 持有 onEnable/onDisable 监听器引用，便于插件卸载时反注册，避免静态引用泄漏。 */
    private final Consumer<BlueMapAPI> onEnableListener;
    private final Consumer<BlueMapAPI> onDisableListener;

    /** 当前可用的 BlueMap API 实例；BlueMap 未就绪时为 null。 */
    private volatile BlueMapAPI api;

    public BlueMapHook(IslandStormPlugin plugin) {
        this.plugin = plugin;
        this.onEnableListener = api -> {
            this.api = api;
            plugin.getLogger().info("已连接 BlueMap API，开始同步天气标记。");
            // onEnable 回调可能不在主线程；同步要读 Bukkit 世界/管理器数据，调度回主线程执行。
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    sync();
                } catch (Throwable t) {
                    plugin.getLogger().warning("BlueMap 初次标记同步失败：" + t.getMessage());
                }
            });
        };
        this.onDisableListener = api -> this.api = null;
    }

    /** 注册 BlueMap 生命周期监听。只应在插件 onEnable 调用一次（不要在 reload 时重复注册）。 */
    public void register() {
        BlueMapAPI.onEnable(onEnableListener);
        BlueMapAPI.onDisable(onDisableListener);
    }

    /** 插件卸载时清理：反注册监听并移除本插件推送的标记集。 */
    public void shutdown() {
        try {
            BlueMapAPI.unregisterListener(onEnableListener);
            BlueMapAPI.unregisterListener(onDisableListener);
        } catch (Throwable ignored) {
            // 反注册失败不致命
        }
        BlueMapAPI current = this.api;
        if (current != null) {
            try {
                clearAll(current);
            } catch (Throwable ignored) {
                // 关服阶段清理失败可忽略
            }
        }
        this.api = null;
    }

    /**
     * 重建并推送全部标记。须在主线程调用（读取 Bukkit 世界与各管理器数据）。
     * BlueMap 未就绪或被关闭时静默跳过。
     */
    public void sync() {
        BlueMapAPI a = this.api;
        if (a == null) return;

        if (!plugin.configManager().bluemapEnabled()) {
            clearAll(a);
            return;
        }

        int markerY = plugin.configManager().bluemapMarkerY();
        long now = System.currentTimeMillis();

        // 按世界名分组构建标记集
        Map<String, MarkerSet> byWorld = new HashMap<>();
        for (WeatherRegion r : plugin.regionManager().all()) {
            MarkerSet set = byWorld.computeIfAbsent(r.world(),
                    w -> MarkerSet.builder().label(MARKERSET_LABEL).build());
            set.getMarkers().put("region:" + r.name(), buildRegionMarker(r, markerY));
        }
        for (StormPath p : plugin.stormPathManager().all()) {
            MarkerSet set = byWorld.computeIfAbsent(p.world(),
                    w -> MarkerSet.builder().label(MARKERSET_LABEL).build());
            addStormMarkers(set, p, markerY, now);
        }

        // 推送到各世界的所有地图；无内容的世界则移除我们的标记集（清除残留）
        for (World bw : Bukkit.getWorlds()) {
            Optional<BlueMapWorld> ow = a.getWorld(bw);
            if (ow.isEmpty()) continue;
            MarkerSet set = byWorld.get(bw.getName());
            for (BlueMapMap map : ow.get().getMaps()) {
                if (set != null) {
                    map.getMarkerSets().put(MARKERSET_ID, set);
                } else {
                    map.getMarkerSets().remove(MARKERSET_ID);
                }
            }
        }
    }

    /** 从所有地图移除本插件的标记集。 */
    private void clearAll(BlueMapAPI a) {
        for (World bw : Bukkit.getWorlds()) {
            a.getWorld(bw).ifPresent(world -> {
                for (BlueMapMap map : world.getMaps()) {
                    map.getMarkerSets().remove(MARKERSET_ID);
                }
            });
        }
    }

    /** 区域 → 矩形 ShapeMarker，按危险等级着色。 */
    private ShapeMarker buildRegionMarker(WeatherRegion r, int markerY) {
        // createRect 的第二/第四参数是 xz 平面的 z；+1 让框覆盖到 max 方块的整格
        Shape shape = Shape.createRect(r.minX(), r.minZ(), r.maxX() + 1, r.maxZ() + 1);
        Color line = hexColor(r.dangerLevel().hexColor(), 1.0f);
        Color fill = hexColor(r.dangerLevel().hexColor(), 0.25f);
        return ShapeMarker.builder()
                .label("区域 " + r.name() + " · " + r.weather().displayName())
                .detail(buildRegionDetail(r))
                .shape(shape, (float) markerY)
                .lineColor(line)
                .fillColor(fill)
                .lineWidth(2)
                .depthTestEnabled(false)
                .build();
    }

    /** 台风/极端风暴 → 路径线 + 当前中心 POI + 影响半径圆。 */
    private void addStormMarkers(MarkerSet set, StormPath p, int markerY, long now) {
        Color orange = new Color(240, 136, 62, 1.0f);

        // 跑完/停止的风暴（曾启动但当前非 active）整体不画——到达终点即「取消显示」。
        boolean ended = p.startEpochMillis() > 0 && !p.active();
        if (ended) return;

        // 预排路径线：未启动（预览）或正在运行时才画。
        if (p.points().size() >= 2) {
            List<Vector3d> pts = new ArrayList<>();
            for (StormPathPoint pt : p.points()) {
                pts.add(new Vector3d(pt.x(), markerY, pt.z()));
            }
            LineMarker lineMarker = LineMarker.builder()
                    .label("风暴路径 " + p.id())
                    .line(new Line(pts))
                    .lineColor(orange)
                    .lineWidth(3)
                    .depthTestEnabled(false)
                    .build();
            set.getMarkers().put("storm-line:" + p.id(), lineMarker);
        }

        // 当前中心 + 影响半径（仅在已启动、能算出中心时）
        double[] c = p.centerAt(now);
        if (c != null) {
            Color red = new Color(248, 81, 73, 1.0f);
            Color redFill = new Color(248, 81, 73, 0.18f);
            Shape circle = Shape.createCircle(c[0], c[1], Math.max(1.0, p.radiusAt(now)), 48);
            ShapeMarker radius = ShapeMarker.builder()
                    .label("台风 " + p.id() + " 影响范围")
                    .shape(circle, (float) markerY)
                    .lineColor(red)
                    .fillColor(redFill)
                    .lineWidth(2)
                    .depthTestEnabled(false)
                    .build();
            set.getMarkers().put("storm-radius:" + p.id(), radius);

            POIMarker center = POIMarker.builder()
                    .label("台风中心 " + p.id() + "（" + p.type().displayName() + "）")
                    .position(new Vector3d(c[0], markerY, c[1]))
                    .maxDistance(1.0e7)
                    .build();
            set.getMarkers().put("storm-center:" + p.id(), center);
        }
    }

    private String buildRegionDetail(WeatherRegion r) {
        return "<div style=\"font-size:13px\">"
                + "<b>" + escape(r.name()) + "</b><br/>"
                + "天气：" + escape(r.weather().displayName()) + "<br/>"
                + "风：" + (int) r.windSpeed() + " km/h " + r.windDirection().name() + "<br/>"
                + "危险等级：" + escape(r.dangerLevel().display())
                + "</div>";
    }

    /** 将 {@code #rrggbb} 文本转为带 alpha 的 BlueMap Color；解析失败回落到蓝色。 */
    private static Color hexColor(String hex, float alpha) {
        int rgb;
        try {
            String h = (hex != null && hex.startsWith("#")) ? hex.substring(1) : hex;
            rgb = Integer.parseInt(h, 16);
        } catch (Exception e) {
            rgb = 0x58a6ff;
        }
        int rr = (rgb >> 16) & 0xFF;
        int gg = (rgb >> 8) & 0xFF;
        int bb = rgb & 0xFF;
        return new Color(rr, gg, bb, alpha);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
