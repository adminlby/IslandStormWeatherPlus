package io.lbynb.islandstorm;

import io.lbynb.islandstorm.command.StormCommand;
import io.lbynb.islandstorm.config.ConfigManager;
import io.lbynb.islandstorm.damage.BlockDamageManager;
import io.lbynb.islandstorm.forecast.ForecastManager;
import io.lbynb.islandstorm.forecast.HourlyForecastManager;
import io.lbynb.islandstorm.html.HtmlWeatherCardGenerator;
import io.lbynb.islandstorm.region.RegionManager;
import io.lbynb.islandstorm.storm.StormPathManager;
import io.lbynb.islandstorm.task.BlockDamageTask;
import io.lbynb.islandstorm.task.StormMovementTask;
import io.lbynb.islandstorm.task.WeatherCycleTask;
import io.lbynb.islandstorm.task.WindEffectTask;
import io.lbynb.islandstorm.time.GameTimeUtil;
import io.lbynb.islandstorm.util.MessageUtil;
import io.lbynb.islandstorm.weather.WeatherManager;
import io.lbynb.islandstorm.web.WebAuthManager;
import io.lbynb.islandstorm.web.WebServerManager;
import io.lbynb.islandstorm.wind.WindManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * IslandStorm 主类 —— 插件入口与各管理器的装配点。
 *
 * <p>天气系统为「纯人工控制」：天气只来自指令 / 后台 / 网页控制台 / 配置预设，
 * 不接入外部天气 API、不做现实天气换算、不做随机生成算法。</p>
 *
 * <p>当前为 P4：在 P3 基础上接入 指令系统 与 网页用户数据层（WebAuthManager）。
 * 后续阶段会在此接入 网页 HTTP 控制台 / HTML 卡片。</p>
 */
public final class IslandStormPlugin extends JavaPlugin {

    private static IslandStormPlugin instance;

    private ConfigManager configManager;
    private WindManager windManager;
    private WeatherManager weatherManager;
    private ForecastManager forecastManager;
    private HourlyForecastManager hourlyForecastManager;
    private RegionManager regionManager;
    private StormPathManager stormPathManager;
    private BlockDamageManager blockDamageManager;
    private WebAuthManager webAuthManager;
    private WebServerManager webServerManager;
    private HtmlWeatherCardGenerator htmlGenerator;

    private final List<BukkitTask> tasks = new ArrayList<>();

    public static IslandStormPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultResources();

        this.configManager = new ConfigManager(this);
        this.configManager.load();
        MessageUtil.init(this);
        GameTimeUtil.configure(configManager);

        this.windManager = new WindManager(configManager);
        this.weatherManager = new WeatherManager(this, configManager, windManager);
        this.forecastManager = new ForecastManager();
        this.weatherManager.setScheduleSupplier(forecastManager::pollNext);
        this.hourlyForecastManager =
                new HourlyForecastManager(configManager, weatherManager, windManager, forecastManager);

        this.regionManager = new RegionManager(this);
        this.regionManager.load();
        this.stormPathManager = new StormPathManager(this);
        this.stormPathManager.load();
        windManager.setRegionManager(regionManager);
        windManager.setStormPathManager(stormPathManager);

        this.blockDamageManager =
                new BlockDamageManager(this, configManager, regionManager, stormPathManager, weatherManager);

        this.htmlGenerator = new HtmlWeatherCardGenerator(this, configManager);
        // 天气变化自动生成 HTML（若开启），由 WeatherManager 在广播后回调
        this.weatherManager.setOnWeatherChange(() -> {
            if (configManager.raw().getBoolean("html.auto-generate-on-weather-change", true)
                    && configManager.raw().getBoolean("html.enabled", true)) {
                try {
                    htmlGenerator.generateAll();
                } catch (Exception e) {
                    getLogger().warning("自动生成 HTML 失败：" + e.getMessage());
                }
            }
        });

        this.webAuthManager = new WebAuthManager(this);
        this.webAuthManager.load();

        this.weatherManager.initDefault();

        registerCommands();
        startTasks();

        this.webServerManager = new WebServerManager(this);
        this.webServerManager.start();

        getLogger().info("IslandStorm 已启用（P5）。天气模式：纯人工控制。");
    }

    @Override
    public void onDisable() {
        stopTasks();
        if (webServerManager != null) webServerManager.stop();
        if (regionManager != null) regionManager.save();
        if (stormPathManager != null) stormPathManager.save();
        getLogger().info("IslandStorm 已卸载。");
        instance = null;
    }

    /** 重载整套配置并重启任务，保证不会出现任务重复运行。 */
    public void reloadAll() {
        stopTasks();
        configManager.load();
        MessageUtil.init(this);
        GameTimeUtil.configure(configManager);
        regionManager.load();
        stormPathManager.load();
        webAuthManager.load();
        startTasks();
        getLogger().info("IslandStorm 配置已重载。");
    }

    private void registerCommands() {
        StormCommand executor = new StormCommand(this);
        PluginCommand cmd = getCommand("islandstorm");
        if (cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        } else {
            getLogger().warning("未能注册 /islandstorm 指令（plugin.yml 缺少定义？）");
        }
    }

    private void startTasks() {
        stopTasks(); // 双保险：避免重复调度
        int windInterval = Math.max(1, configManager.updateIntervalTicks());
        tasks.add(new WindEffectTask(configManager, windManager).runTaskTimer(this, windInterval, windInterval));
        tasks.add(new WeatherCycleTask(weatherManager).runTaskTimer(this, 20L, 20L));
        tasks.add(new StormMovementTask(stormPathManager).runTaskTimer(this, 20L, 20L));
        int dmgInterval = Math.max(1, configManager.blockDamageIntervalTicks());
        tasks.add(new BlockDamageTask(blockDamageManager).runTaskTimer(this, dmgInterval, dmgInterval));
    }

    private void stopTasks() {
        for (BukkitTask t : tasks) {
            if (t != null) t.cancel();
        }
        tasks.clear();
    }

    /** 生成 HTML 卡片。which ∈ {preview, open, forecast, all}；返回提示信息。 */
    public String generateHtml(String which) {
        if (htmlGenerator == null) return "HTML 生成器未就绪。";
        return htmlGenerator.generate(which);
    }

    public HtmlWeatherCardGenerator htmlGenerator() {
        return htmlGenerator;
    }

    private void saveDefaultResources() {
        saveDefaultConfig();                 // config.yml
        saveResourceIfAbsent("messages.yml");
        saveResourceIfAbsent("regions.yml");
        saveResourceIfAbsent("storm-paths.yml");
        saveResourceIfAbsent("web-users.yml");
    }

    public void saveResourceIfAbsent(String resourcePath) {
        File target = new File(getDataFolder(), resourcePath);
        if (!target.exists()) {
            saveResource(resourcePath, false);
        }
    }

    public ConfigManager configManager() {
        return configManager;
    }

    public WindManager windManager() {
        return windManager;
    }

    public WeatherManager weatherManager() {
        return weatherManager;
    }

    public ForecastManager forecastManager() {
        return forecastManager;
    }

    public HourlyForecastManager hourlyForecastManager() {
        return hourlyForecastManager;
    }

    public RegionManager regionManager() {
        return regionManager;
    }

    public StormPathManager stormPathManager() {
        return stormPathManager;
    }

    public BlockDamageManager blockDamageManager() {
        return blockDamageManager;
    }

    public WebAuthManager webAuthManager() {
        return webAuthManager;
    }

    public WebServerManager webServerManager() {
        return webServerManager;
    }
}
