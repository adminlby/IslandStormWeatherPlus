package io.lbynb.islandstorm.task;

import io.lbynb.islandstorm.config.ConfigManager;
import io.lbynb.islandstorm.wind.WindManager;
import io.lbynb.islandstorm.wind.WindState;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * 风效任务：按 performance.update-interval-ticks 周期对在线玩家施加风力影响。
 *
 * <p>性能与体验考量：</p>
 * <ul>
 *   <li>只遍历在线玩家；旁观者永远跳过；按配置可只影响生存/冒险模式。</li>
 *   <li>拥有 bypass 权限的玩家不受影响。</li>
 *   <li>风速 ≤10 km/h（无影响档）直接跳过。</li>
 *   <li>用「叠加速度 + 限幅」而非每 tick 猛推，鞘翅顺/逆/侧风通过向量叠加自然成立，
 *       尽量减少客户端回弹抖动。</li>
 * </ul>
 */
public class WindEffectTask extends BukkitRunnable {

    private final ConfigManager config;
    private final WindManager wind;

    public WindEffectTask(ConfigManager config, WindManager wind) {
        this.config = config;
        this.wind = wind;
    }

    @Override
    public void run() {
        if (!config.windEnabled()) return;

        final String bypass = config.windBypassPermission();
        final boolean onlySurvival = config.onlyAffectSurvival();
        final boolean groundOn = config.groundEffectEnabled();
        final boolean elytraOn = config.elytraEffectEnabled();
        final double groundStrength = config.windGroundStrength();
        final double elytraStrength = config.windElytraStrength();
        final double extreme = Math.max(1.0, config.extremeSpeed());
        final boolean blowAway = config.allowBlowAway();

        for (Player p : Bukkit.getOnlinePlayers()) {
            GameMode gm = p.getGameMode();
            if (gm == GameMode.SPECTATOR) continue;
            if (onlySurvival && gm != GameMode.SURVIVAL && gm != GameMode.ADVENTURE) continue;
            if (bypass != null && !bypass.isEmpty() && p.hasPermission(bypass)) continue;
            if (wind.isBypassed(p.getUniqueId())) continue; // 运行时 /storm bypass

            WindState ws = wind.effectiveWindAt(p.getLocation());
            double speed = ws.speed();
            if (speed <= 10) continue; // 无影响档

            // 归一化强度：speed 达到 extreme 即为 1.0，最高 1.5（超强台风眼壁）
            double s = Math.min(1.5, speed / extreme);
            Vector dir = ws.direction().toVector();
            if (p.isGliding()) {
                if (!elytraOn) continue;
                // 鞘翅：把风向量叠加到当前速度——顺风加速、逆风减速、侧风弯折飞行路径
                Vector add = dir.clone().multiply(elytraStrength * s);
                p.setVelocity(p.getVelocity().add(cap(add, 0.6)));
            } else {
                if (!groundOn) continue;
                // 地面：明显推搡/漂移；极端风额外增幅并略微吹起
                Vector add = dir.clone().multiply(groundStrength * s);
                if (blowAway && speed >= extreme) {
                    add.multiply(1.6);
                    if (add.getY() < 0.25) add.setY(0.25);
                }
                p.setVelocity(p.getVelocity().add(cap(add, 0.9)));
            }
        }
    }

    /** 把向量长度限制到 max 以内（保持方向）。 */
    private static Vector cap(Vector v, double max) {
        double len = v.length();
        if (len > max && len > 0) {
            v.multiply(max / len);
        }
        return v;
    }
}
