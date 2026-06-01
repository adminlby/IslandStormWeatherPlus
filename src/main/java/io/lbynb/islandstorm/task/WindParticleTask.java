package io.lbynb.islandstorm.task;

import io.lbynb.islandstorm.config.ConfigManager;
import io.lbynb.islandstorm.wind.WindManager;
import io.lbynb.islandstorm.wind.WindState;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 风粒子任务：让玩家「看得到风」。
 *
 * <p>性能与体验考量：</p>
 * <ul>
 *   <li>用 {@link Player#spawnParticle} 逐玩家下发——只有该玩家客户端收到，几乎不占带宽。</li>
 *   <li>每玩家粒子数封顶（config: particles.count），仅在本地风速 ≥ min-speed 时生成。</li>
 *   <li>每个粒子以风向为初速横掠玩家四周，形成可见的「被风吹动」条纹。</li>
 * </ul>
 */
public class WindParticleTask extends BukkitRunnable {

    private final ConfigManager config;
    private final WindManager wind;

    public WindParticleTask(ConfigManager config, WindManager wind) {
        this.config = config;
        this.wind = wind;
    }

    @Override
    public void run() {
        if (!config.particlesEnabled()) return;
        final int count = config.particlesCount();
        if (count <= 0) return;
        final Particle particle = resolveParticle(config.particlesType());
        final double minSpeed = config.particlesMinSpeed();
        final double radius = config.particlesRadius();
        final ThreadLocalRandom rnd = ThreadLocalRandom.current();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            WindState ws = wind.effectiveWindAt(p.getLocation());
            double speed = ws.speed();
            if (speed < minSpeed) continue;

            Vector dir = ws.direction().toVector();
            double pSpeed = Math.min(1.0, 0.15 + speed / 200.0); // 风越大粒子飘得越快
            Location base = p.getLocation();
            for (int i = 0; i < count; i++) {
                double rx = (rnd.nextDouble() - 0.5) * radius * 2.0;
                double ry = rnd.nextDouble() * 3.0;
                double rz = (rnd.nextDouble() - 0.5) * radius * 2.0;
                // 略微偏向上风处生成，使粒子穿过玩家
                Location pos = base.clone().add(
                        rx - dir.getX() * radius * 0.5, ry, rz - dir.getZ() * radius * 0.5);
                try {
                    // count=0：offset 作为运动向量、extra 作为速度——形成沿风向的横掠条纹（仅该玩家可见）
                    p.spawnParticle(particle, pos, 0, dir.getX(), 0.02, dir.getZ(), pSpeed);
                } catch (Throwable ignored) {
                    // 个别粒子类型需要附加数据，忽略即可
                }
            }
        }
    }

    /** 解析粒子类型；无效或需要附加数据的类型回落到 CLOUD（无需 data）。 */
    private static Particle resolveParticle(String name) {
        Particle particle = Particle.CLOUD;
        if (name != null) {
            try {
                particle = Particle.valueOf(name.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                particle = Particle.CLOUD;
            }
        }
        if (particle.getDataType() != Void.class) {
            particle = Particle.CLOUD;
        }
        return particle;
    }
}
