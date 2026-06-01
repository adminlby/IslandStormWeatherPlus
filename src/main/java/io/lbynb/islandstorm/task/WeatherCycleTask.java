package io.lbynb.islandstorm.task;

import io.lbynb.islandstorm.weather.WeatherManager;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 天气推进任务：周期检查当前天气是否到期，到期则由 WeatherManager 推进到排期下一条。
 * 注意：这不是「随机天气循环」，纯人工模型下只按导演排好的时间线推进。
 */
public class WeatherCycleTask extends BukkitRunnable {

    private final WeatherManager weather;

    public WeatherCycleTask(WeatherManager weather) {
        this.weather = weather;
    }

    @Override
    public void run() {
        weather.tickCheck();
        // 持续把「当前天气 + 活动风暴」同步到原版天气：台风/极端风暴在其活动期间持续下雨打雷，
        // 结束后自动恢复。该调用是幂等的，仅在状态变化时才真正改变原版天气。
        weather.applyVanilla();
    }
}
