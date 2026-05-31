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
    }
}
