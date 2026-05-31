package io.lbynb.islandstorm.wind;

import io.lbynb.islandstorm.config.ConfigManager;
import io.lbynb.islandstorm.region.RegionManager;
import io.lbynb.islandstorm.region.WeatherRegion;
import io.lbynb.islandstorm.storm.StormPathManager;
import org.bukkit.Location;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 风管理器：持有全局风状态，并对外提供「某位置的有效风」。
 *
 * <p>有效风按优先级叠加：全局风 → 区域风（若该位置在某区域内，用区域的风速风向覆盖）
 * → 风暴风（若在某活动风暴半径内，取更强者，风向为背离风暴中心的方向）。</p>
 *
 * <p>另持有一份运行时 bypass 名单（{@code /storm bypass <player>} 切换），与权限节点并行生效。</p>
 */
public class WindManager {

    private final WindState global;
    private RegionManager regionManager;
    private StormPathManager stormPathManager;
    private final Set<UUID> bypass = ConcurrentHashMap.newKeySet();

    public WindManager(ConfigManager config) {
        this.global = new WindState(config.windDefaultSpeed(), config.windDefaultDirection());
    }

    public void setRegionManager(RegionManager regionManager) {
        this.regionManager = regionManager;
    }

    public void setStormPathManager(StormPathManager stormPathManager) {
        this.stormPathManager = stormPathManager;
    }

    public WindState global() {
        return global;
    }

    public void set(double speed, WindDirection direction) {
        global.setSpeed(speed);
        global.setDirection(direction);
    }

    public void setSpeed(double speed) {
        global.setSpeed(speed);
    }

    public void setDirection(WindDirection direction) {
        global.setDirection(direction);
    }

    // ---- 运行时 bypass ----
    public boolean toggleBypass(UUID id) {
        if (bypass.contains(id)) {
            bypass.remove(id);
            return false;
        }
        bypass.add(id);
        return true;
    }

    public boolean isBypassed(UUID id) {
        return bypass.contains(id);
    }

    /**
     * 某位置的有效风（返回副本，避免调用方误改全局状态）。
     */
    public WindState effectiveWindAt(Location loc) {
        WindState ws = global.copy();
        if (loc == null) return ws;

        if (regionManager != null) {
            WeatherRegion region = regionManager.regionAt(loc);
            if (region != null) {
                ws.setSpeed(region.windSpeed());
                ws.setDirection(region.windDirection());
            }
        }

        if (stormPathManager != null) {
            StormPathManager.StormInfluence inf = stormPathManager.influenceAt(loc);
            if (inf != null) {
                double stormSpeed = inf.storm.type().defaultWindSpeed() * inf.factor;
                if (stormSpeed > ws.speed()) {
                    ws.setSpeed(Math.min(stormSpeed, 400)); // 安全上限
                    ws.setDirection(nearestDirection(loc.getX() - inf.centerX, loc.getZ() - inf.centerZ));
                }
            }
        }
        return ws;
    }

    /** 把 (dx,dz) 向量映射到最接近的 8 方位（用于风暴背离中心的吹风方向）。 */
    private static WindDirection nearestDirection(double dx, double dz) {
        if (dx == 0 && dz == 0) return WindDirection.N;
        // 方位角：N=0 顺时针；+X 东=90，+Z 南=180
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        if (angle < 0) angle += 360;
        int idx = (int) Math.round(angle / 45.0) % 8;
        WindDirection[] order = {
                WindDirection.N, WindDirection.NE, WindDirection.E, WindDirection.SE,
                WindDirection.S, WindDirection.SW, WindDirection.W, WindDirection.NW
        };
        return order[idx];
    }
}
