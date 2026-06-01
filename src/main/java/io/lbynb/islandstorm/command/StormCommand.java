package io.lbynb.islandstorm.command;

import io.lbynb.islandstorm.IslandStormPlugin;
import io.lbynb.islandstorm.forecast.HourlyForecastEntry;
import io.lbynb.islandstorm.region.WeatherRegion;
import io.lbynb.islandstorm.storm.StormPath;
import io.lbynb.islandstorm.storm.StormPathPoint;
import io.lbynb.islandstorm.time.GameTimeUtil;
import io.lbynb.islandstorm.time.ParsedDuration;
import io.lbynb.islandstorm.time.TimeMode;
import io.lbynb.islandstorm.util.MessageUtil;
import io.lbynb.islandstorm.weather.DangerLevel;
import io.lbynb.islandstorm.weather.ForecastEntry;
import io.lbynb.islandstorm.weather.WeatherState;
import io.lbynb.islandstorm.weather.WeatherType;
import io.lbynb.islandstorm.wind.WindDirection;
import io.lbynb.islandstorm.wind.WindState;
import io.lbynb.islandstorm.web.WebPermission;
import io.lbynb.islandstorm.web.WebUser;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 主指令 {@code /islandstorm}（别名 /storm /weatherplus /isweather）的执行器与补全器。
 *
 * <p>所有子指令都做权限校验、参数校验与错误提示；TabComplete 覆盖各级子指令与枚举值。
 * 天气为纯人工控制，本类是导演/管理员的主要操作入口。</p>
 */
public class StormCommand implements CommandExecutor, TabCompleter {

    private final IslandStormPlugin plugin;

    public StormCommand(IslandStormPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command command, String label, String[] a) {
        if (a.length == 0) {
            return help(s);
        }
        switch (a[0].toLowerCase()) {
            case "help":
                return help(s);
            case "reload":
                return reload(s);
            case "status":
                return status(s);
            case "setweather":
                return setWeather(s, a);
            case "setwind":
                return setWind(s, a);
            case "forecast":
                return forecast(s, a);
            case "html":
                return html(s, a);
            case "debug":
                return debug(s);
            case "bypass":
                return bypass(s, a);
            case "region":
                return region(s, a);
            case "path":
                return path(s, a);
            case "webuser":
                return webuser(s, a);
            default:
                MessageUtil.send(s, MessageUtil.msg("unknown-subcommand",
                        "&c未知子指令，使用 &f/storm help &c查看帮助。"));
                return true;
        }
    }

    // ============================ 基础子指令 ============================

    private boolean help(CommandSender s) {
        MessageUtil.raw(s, "&b===== IslandStorm 指令帮助 =====");
        MessageUtil.raw(s, "&f/storm status &7- 查看当前天气/风");
        MessageUtil.raw(s, "&f/storm setweather <类型> [时长] [REAL_TIME|MC_TIME] &7- 设置天气");
        MessageUtil.raw(s, "&f/storm setwind <风速> <风向> &7- 设置风速风向");
        MessageUtil.raw(s, "&f/storm forecast [hourly] &7- 查看预报/小时预报");
        MessageUtil.raw(s, "&f/storm forecast add <类型> <时长> [模式] &7- 追加排期");
        MessageUtil.raw(s, "&f/storm forecast clear &7- 清空排期");
        MessageUtil.raw(s, "&f/storm html [preview|forecast|all] &7- 生成 HTML 卡片");
        MessageUtil.raw(s, "&f/storm bypass <玩家> &7- 切换玩家是否受风影响");
        MessageUtil.raw(s, "&f/storm region ... &7- 区域天气管理");
        MessageUtil.raw(s, "&f/storm path ... &7- 风暴路径管理");
        MessageUtil.raw(s, "&f/storm webuser ... &7- 网页控制台用户管理");
        MessageUtil.raw(s, "&f/storm reload &7- 重载配置");
        return true;
    }

    private boolean reload(CommandSender s) {
        if (noPerm(s, "islandstorm.reload")) return true;
        plugin.reloadAll();
        MessageUtil.send(s, MessageUtil.msg("reloaded", "&a配置已重载。"));
        return true;
    }

    private boolean status(CommandSender s) {
        if (noPerm(s, "islandstorm.status")) return true;
        WeatherState cur = plugin.weatherManager().current();
        WindState wind = plugin.windManager().global();
        if (cur == null) {
            MessageUtil.send(s, "&7当前没有天气状态。");
            return true;
        }
        WeatherType t = cur.type();
        MessageUtil.raw(s, "&b===== 当前天气 =====");
        MessageUtil.raw(s, "&f天气：&b" + t.icon() + " " + t.displayName() + " &7(" + t.name() + ")");
        MessageUtil.raw(s, "&f风：&b" + (int) wind.speed() + " km/h " + wind.direction().name()
                + " &7(" + wind.levelLabel() + ")");
        MessageUtil.raw(s, "&f危险等级：" + t.dangerLevel().chatColor() + t.dangerLevel().display());
        MessageUtil.raw(s, "&f能见度：&b" + t.visibility());
        long now = System.currentTimeMillis();
        if (cur.isInfinite()) {
            MessageUtil.raw(s, "&f剩余时长：&b永久");
        } else {
            MessageUtil.raw(s, "&f剩余时长：&b" + GameTimeUtil.bothDurationLabel(cur.remainingMillis(now)));
        }
        MessageUtil.raw(s, "&f原版同步：&b" + plugin.configManager().vanillaSyncMode());
        MessageUtil.raw(s, "&f在线人数：&b" + Bukkit.getOnlinePlayers().size());
        return true;
    }

    private boolean setWeather(CommandSender s, String[] a) {
        if (noPerm(s, "islandstorm.setweather")) return true;
        if (a.length < 2) {
            MessageUtil.send(s, "&c用法：/storm setweather <类型> [时长] [REAL_TIME|MC_TIME]");
            return true;
        }
        WeatherType type = WeatherType.fromString(a[1], null);
        if (type == null) {
            MessageUtil.send(s, MessageUtil.format(MessageUtil.msg("unknown-weather",
                    "&c未知天气类型：&f{input}"), "input", a[1]));
            return true;
        }
        long durationMillis = type.defaultDurationMinutes() * 60_000L;
        if (a.length >= 3) {
            TimeMode hint = a.length >= 4 ? TimeMode.fromString(a[3], null) : null;
            ParsedDuration pd = GameTimeUtil.parse(a[2], hint);
            if (pd == null) {
                MessageUtil.send(s, "&c无效的时长：&f" + a[2] + " &7(示例 30m / 6h / 1200ticks / 1mcday)");
                return true;
            }
            durationMillis = pd.realMillis();
        }
        plugin.weatherManager().setWeather(type, durationMillis, "command:" + s.getName());
        MessageUtil.send(s, "&a已设置天气为 &b" + type.displayName() + " &a，时长 &b"
                + (durationMillis <= 0 ? "永久" : GameTimeUtil.bothDurationLabel(durationMillis)));
        return true;
    }

    private boolean setWind(CommandSender s, String[] a) {
        if (noPerm(s, "islandstorm.setwind")) return true;
        if (a.length < 3) {
            MessageUtil.send(s, "&c用法：/storm setwind <风速km/h> <风向 N/NE/E/SE/S/SW/W/NW>");
            return true;
        }
        Double speed = parseDouble(a[1]);
        if (speed == null || speed < 0) {
            MessageUtil.send(s, MessageUtil.format(MessageUtil.msg("invalid-number",
                    "&c无效的数字：&f{input}"), "input", a[1]));
            return true;
        }
        WindDirection dir = WindDirection.fromString(a[2], null);
        if (dir == null) {
            MessageUtil.send(s, MessageUtil.format(MessageUtil.msg("unknown-direction",
                    "&c未知风向：&f{input}（可用 N NE E SE S SW W NW）"), "input", a[2]));
            return true;
        }
        plugin.windManager().set(speed, dir);
        MessageUtil.send(s, "&a已设置风：&b" + (int) (double) speed + " km/h " + dir.name());
        return true;
    }

    private boolean forecast(CommandSender s, String[] a) {
        if (noPerm(s, "islandstorm.forecast")) return true;
        if (a.length >= 2 && a[1].equalsIgnoreCase("hourly")) {
            List<HourlyForecastEntry> list = plugin.hourlyForecastManager().generate();
            MessageUtil.raw(s, "&b===== 小时级预报（前 " + Math.min(8, list.size()) + " 小时）=====");
            for (int i = 0; i < Math.min(8, list.size()); i++) {
                HourlyForecastEntry e = list.get(i);
                MessageUtil.raw(s, "&7" + GameTimeUtil.formatRealMillis(e.realStartMillis())
                        + " / MC " + GameTimeUtil.formatWorldTimeHHmm(e.mcStartTick())
                        + " &f" + e.icon() + " " + e.displayName()
                        + " &b" + (int) e.windSpeed() + "km/h " + e.windDirection().name()
                        + " " + e.dangerLevel().chatColor() + e.dangerLevel().display());
            }
            return true;
        }
        if (a.length >= 2 && a[1].equalsIgnoreCase("add")) {
            if (noPerm(s, "islandstorm.setweather")) return true;
            if (a.length < 4) {
                MessageUtil.send(s, "&c用法：/storm forecast add <类型> <时长> [REAL_TIME|MC_TIME]");
                return true;
            }
            WeatherType type = WeatherType.fromString(a[2], null);
            if (type == null) {
                MessageUtil.send(s, MessageUtil.format(MessageUtil.msg("unknown-weather",
                        "&c未知天气类型：&f{input}"), "input", a[2]));
                return true;
            }
            TimeMode hint = a.length >= 5 ? TimeMode.fromString(a[4], null) : null;
            ParsedDuration pd = GameTimeUtil.parse(a[3], hint);
            if (pd == null) {
                MessageUtil.send(s, "&c无效的时长：&f" + a[3]);
                return true;
            }
            plugin.forecastManager().add(ForecastEntry.of(type, pd.realMillis()));
            MessageUtil.send(s, "&a已追加排期：&b" + type.displayName() + " &a时长 &b"
                    + GameTimeUtil.bothDurationLabel(pd.realMillis()) + " &7(当前排期 "
                    + plugin.forecastManager().size() + " 条)");
            return true;
        }
        if (a.length >= 2 && a[1].equalsIgnoreCase("clear")) {
            if (noPerm(s, "islandstorm.setweather")) return true;
            plugin.forecastManager().clear();
            MessageUtil.send(s, "&a已清空天气排期。");
            return true;
        }
        // 默认：显示排期列表
        List<ForecastEntry> list = plugin.forecastManager().all();
        MessageUtil.raw(s, "&b===== 天气排期（共 " + list.size() + " 条）=====");
        if (list.isEmpty()) {
            MessageUtil.raw(s, "&7（空）当前天气到期后将回落默认天气。");
        }
        int i = 1;
        for (ForecastEntry e : list) {
            MessageUtil.raw(s, "&7#" + (i++) + " &f" + e.type().icon() + " " + e.type().displayName()
                    + " &b" + (int) e.windSpeed() + "km/h " + e.windDirection().name()
                    + " &7时长 " + GameTimeUtil.bothDurationLabel(e.durationMillis()));
        }
        return true;
    }

    private boolean html(CommandSender s, String[] a) {
        if (noPerm(s, "islandstorm.html")) return true;
        String which = a.length >= 2 ? a[1].toLowerCase() : "preview";
        MessageUtil.send(s, "&7" + plugin.generateHtml(which));
        return true;
    }

    private boolean debug(CommandSender s) {
        if (noPerm(s, "islandstorm.debug")) return true;
        MessageUtil.raw(s, "&b===== IslandStorm Debug =====");
        MessageUtil.raw(s, "&f区域数：&b" + plugin.regionManager().all().size());
        MessageUtil.raw(s, "&f风暴路径数：&b" + plugin.stormPathManager().all().size());
        MessageUtil.raw(s, "&f网页用户数：&b" + plugin.webAuthManager().all().size());
        MessageUtil.raw(s, "&f方块破坏：&b" + (plugin.configManager().blockDamageEnabled() ? "开启" : "关闭"));
        MessageUtil.raw(s, "&fWorldGuard：&b" + (plugin.blockDamageManager().isWorldGuardPresent() ? "存在" : "不存在"));
        MessageUtil.raw(s, "&f在线人数：&b" + Bukkit.getOnlinePlayers().size());
        return true;
    }

    private boolean bypass(CommandSender s, String[] a) {
        // 切换他人风影响绕过是「管理操作」，用专门的 manage 节点（默认 op），
        // 与「自身免疫风」的 islandstorm.bypass（默认 false）分开，避免把免疫权当成了指令权。
        if (noPerm(s, "islandstorm.bypass.manage")) return true;
        if (a.length < 2) {
            MessageUtil.send(s, "&c用法：/storm bypass <玩家>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(a[1]);
        if (target == null) {
            MessageUtil.send(s, "&c玩家不在线：&f" + a[1]);
            return true;
        }
        boolean now = plugin.windManager().toggleBypass(target.getUniqueId());
        MessageUtil.send(s, "&a玩家 &b" + target.getName() + " &a风影响绕过：&b" + (now ? "开启" : "关闭"));
        return true;
    }

    // ============================ 区域 ============================

    private boolean region(CommandSender s, String[] a) {
        if (noPerm(s, "islandstorm.region")) return true;
        if (a.length < 2) {
            MessageUtil.send(s, "&c用法：/storm region <list|create|delete|setweather|setwind> ...");
            return true;
        }
        switch (a[1].toLowerCase()) {
            case "list": {
                MessageUtil.raw(s, "&b===== 区域天气（共 " + plugin.regionManager().all().size() + "）=====");
                for (WeatherRegion r : plugin.regionManager().all()) {
                    MessageUtil.raw(s, "&f" + r.name() + " &7@" + r.world() + " [" + r.minX() + "," + r.minZ()
                            + " ~ " + r.maxX() + "," + r.maxZ() + "] &b" + r.weather().displayName()
                            + " &7风 " + (int) r.windSpeed() + " " + r.windDirection().name());
                }
                return true;
            }
            case "create": {
                // /storm region create <name> <world> <minX> <minZ> <maxX> <maxZ> <weatherType>
                if (a.length < 9) {
                    MessageUtil.send(s, "&c用法：/storm region create <名称> <世界> <minX> <minZ> <maxX> <maxZ> <天气类型>");
                    return true;
                }
                if (plugin.regionManager().exists(a[2])) {
                    MessageUtil.send(s, "&c区域已存在：&f" + a[2]);
                    return true;
                }
                Integer minX = parseInt(a[4]), minZ = parseInt(a[5]), maxX = parseInt(a[6]), maxZ = parseInt(a[7]);
                if (minX == null || minZ == null || maxX == null || maxZ == null) {
                    MessageUtil.send(s, "&c坐标必须为整数。");
                    return true;
                }
                WeatherType type = WeatherType.fromString(a[8], null);
                if (type == null) {
                    MessageUtil.send(s, MessageUtil.format(MessageUtil.msg("unknown-weather",
                            "&c未知天气类型：&f{input}"), "input", a[8]));
                    return true;
                }
                if (type == WeatherType.TYPHOON || type == WeatherType.EXTREME_STORM) {
                    MessageUtil.send(s, "&c区域不能使用台风/极端风暴天气，请用 /storm path 创建风暴路径。");
                    return true;
                }
                WeatherRegion r = new WeatherRegion(a[2], a[3], minX, minZ, maxX, maxZ, type,
                        type.defaultWindSpeed(), type.defaultWindDirection(), type.dangerLevel(), -1);
                plugin.regionManager().add(r);
                MessageUtil.send(s, "&a已创建区域 &b" + a[2] + " &a天气 &b" + type.displayName());
                return true;
            }
            case "delete": {
                if (a.length < 3) {
                    MessageUtil.send(s, "&c用法：/storm region delete <名称>");
                    return true;
                }
                if (plugin.regionManager().remove(a[2])) {
                    MessageUtil.send(s, "&a已删除区域 &b" + a[2]);
                } else {
                    MessageUtil.send(s, "&c区域不存在：&f" + a[2]);
                }
                return true;
            }
            case "setweather": {
                if (a.length < 4) {
                    MessageUtil.send(s, "&c用法：/storm region setweather <名称> <天气类型>");
                    return true;
                }
                WeatherRegion r = plugin.regionManager().get(a[2]);
                if (r == null) {
                    MessageUtil.send(s, "&c区域不存在：&f" + a[2]);
                    return true;
                }
                WeatherType type = WeatherType.fromString(a[3], null);
                if (type == null) {
                    MessageUtil.send(s, MessageUtil.format(MessageUtil.msg("unknown-weather",
                            "&c未知天气类型：&f{input}"), "input", a[3]));
                    return true;
                }
                if (type == WeatherType.TYPHOON || type == WeatherType.EXTREME_STORM) {
                    MessageUtil.send(s, "&c区域不能使用台风/极端风暴天气，请用 /storm path 创建风暴路径。");
                    return true;
                }
                r.setWeather(type);
                plugin.regionManager().save();
                MessageUtil.send(s, "&a区域 &b" + r.name() + " &a天气已设为 &b" + type.displayName());
                return true;
            }
            case "setwind": {
                if (a.length < 5) {
                    MessageUtil.send(s, "&c用法：/storm region setwind <名称> <风速> <风向>");
                    return true;
                }
                WeatherRegion r = plugin.regionManager().get(a[2]);
                if (r == null) {
                    MessageUtil.send(s, "&c区域不存在：&f" + a[2]);
                    return true;
                }
                Double sp = parseDouble(a[3]);
                WindDirection dir = WindDirection.fromString(a[4], null);
                if (sp == null || sp < 0 || dir == null) {
                    MessageUtil.send(s, "&c风速须为非负数，风向须为 N/NE/E/SE/S/SW/W/NW。");
                    return true;
                }
                r.setWindSpeed(sp);
                r.setWindDirection(dir);
                plugin.regionManager().save();
                MessageUtil.send(s, "&a区域 &b" + r.name() + " &a风已设为 &b" + (int) (double) sp + " " + dir.name());
                return true;
            }
            default:
                MessageUtil.send(s, "&c未知区域子指令。");
                return true;
        }
    }

    // ============================ 风暴路径 ============================

    private boolean path(CommandSender s, String[] a) {
        if (noPerm(s, "islandstorm.path")) return true;
        if (a.length < 2) {
            MessageUtil.send(s, "&c用法：/storm path <list|create|addpoint|start|pause|resume|stop|delete> ...");
            return true;
        }
        switch (a[1].toLowerCase()) {
            case "list": {
                MessageUtil.raw(s, "&b===== 风暴路径（共 " + plugin.stormPathManager().all().size() + "）=====");
                long now = System.currentTimeMillis();
                for (StormPath p : plugin.stormPathManager().all()) {
                    String center = "";
                    double[] c = p.centerAt(now);
                    if (c != null) center = " 中心(" + (int) c[0] + "," + (int) c[1] + ")";
                    MessageUtil.raw(s, "&f" + p.id() + " &7" + p.type().name() + " @" + p.world()
                            + " R" + (int) p.radius() + " 点" + p.points().size()
                            + (p.active() ? " &a[运行]" : " &7[停止]") + "&7" + center);
                }
                return true;
            }
            case "create": {
                // /storm path create <id> <TYPHOON|EXTREME_STORM> <world> <radius>
                if (a.length < 6) {
                    MessageUtil.send(s, "&c用法：/storm path create <id> <TYPHOON|EXTREME_STORM> <世界> <半径>");
                    return true;
                }
                if (plugin.stormPathManager().exists(a[2])) {
                    MessageUtil.send(s, "&c路径已存在：&f" + a[2]);
                    return true;
                }
                WeatherType type = WeatherType.fromString(a[3], null);
                if (type != WeatherType.TYPHOON && type != WeatherType.EXTREME_STORM) {
                    MessageUtil.send(s, "&c风暴类型只能是 TYPHOON 或 EXTREME_STORM。");
                    return true;
                }
                Double radius = parseDouble(a[5]);
                if (radius == null || radius <= 0) {
                    MessageUtil.send(s, "&c半径须为正数。");
                    return true;
                }
                StormPath p = new StormPath(a[2], type, a[4], radius);
                plugin.stormPathManager().add(p);
                MessageUtil.send(s, "&a已创建风暴路径 &b" + a[2] + " &7(" + type.name() + ", R" + (int) (double) radius + ")");
                return true;
            }
            case "addpoint": {
                // /storm path addpoint <id> <x> <z> <arriveAfter> [REAL_TIME|MC_TIME]
                if (a.length < 6) {
                    MessageUtil.send(s, "&c用法：/storm path addpoint <id> <x> <z> <到达时间如10m/6h> [REAL_TIME|MC_TIME]");
                    return true;
                }
                StormPath p = plugin.stormPathManager().get(a[2]);
                if (p == null) {
                    MessageUtil.send(s, "&c路径不存在：&f" + a[2]);
                    return true;
                }
                Double x = parseDouble(a[3]), z = parseDouble(a[4]);
                if (x == null || z == null) {
                    MessageUtil.send(s, "&c坐标必须为数字。");
                    return true;
                }
                TimeMode hint = a.length >= 7 ? TimeMode.fromString(a[6], null) : null;
                ParsedDuration pd = GameTimeUtil.parse(a[5], hint);
                if (pd == null) {
                    MessageUtil.send(s, "&c无效的到达时间：&f" + a[5]);
                    return true;
                }
                p.addPoint(new StormPathPoint(x, z, pd.realMillis()));
                plugin.stormPathManager().save();
                MessageUtil.send(s, "&a已为 &b" + p.id() + " &a添加点 (" + (int) (double) x + "," + (int) (double) z
                        + ") 到达 " + GameTimeUtil.bothDurationLabel(pd.realMillis()));
                return true;
            }
            case "start": {
                if (a.length < 3) {
                    MessageUtil.send(s, "&c用法：/storm path start <id>");
                    return true;
                }
                StormPath p = plugin.stormPathManager().get(a[2]);
                if (p == null) {
                    MessageUtil.send(s, "&c路径不存在：&f" + a[2]);
                    return true;
                }
                if (p.points().isEmpty()) {
                    MessageUtil.send(s, "&c该路径还没有任何点，无法启动。");
                    return true;
                }
                p.start(System.currentTimeMillis());
                plugin.stormPathManager().save();
                MessageUtil.send(s, "&a风暴 &b" + p.id() + " &a已启动。");
                return true;
            }
            case "pause": {
                if (a.length < 3) {
                    MessageUtil.send(s, "&c用法：/storm path pause <id>");
                    return true;
                }
                StormPath p = plugin.stormPathManager().get(a[2]);
                if (p == null) {
                    MessageUtil.send(s, "&c路径不存在：&f" + a[2]);
                    return true;
                }
                if (!p.active() || p.paused()) {
                    MessageUtil.send(s, "&c该风暴未在运行或已暂停。");
                    return true;
                }
                p.pause(System.currentTimeMillis());
                plugin.stormPathManager().save();
                MessageUtil.send(s, "&e风暴 &b" + p.id() + " &e已暂停。");
                return true;
            }
            case "resume": {
                if (a.length < 3) {
                    MessageUtil.send(s, "&c用法：/storm path resume <id>");
                    return true;
                }
                StormPath p = plugin.stormPathManager().get(a[2]);
                if (p == null) {
                    MessageUtil.send(s, "&c路径不存在：&f" + a[2]);
                    return true;
                }
                if (!p.paused()) {
                    MessageUtil.send(s, "&c该风暴未处于暂停状态。");
                    return true;
                }
                p.resume(System.currentTimeMillis());
                plugin.stormPathManager().save();
                MessageUtil.send(s, "&a风暴 &b" + p.id() + " &a已继续。");
                return true;
            }
            case "stop": {
                if (a.length < 3) {
                    MessageUtil.send(s, "&c用法：/storm path stop <id>");
                    return true;
                }
                StormPath p = plugin.stormPathManager().get(a[2]);
                if (p == null) {
                    MessageUtil.send(s, "&c路径不存在：&f" + a[2]);
                    return true;
                }
                p.stop();
                plugin.stormPathManager().save();
                MessageUtil.send(s, "&a风暴 &b" + p.id() + " &a已停止。");
                return true;
            }
            case "delete": {
                if (a.length < 3) {
                    MessageUtil.send(s, "&c用法：/storm path delete <id>");
                    return true;
                }
                if (plugin.stormPathManager().remove(a[2])) {
                    MessageUtil.send(s, "&a已删除风暴路径 &b" + a[2]);
                } else {
                    MessageUtil.send(s, "&c路径不存在：&f" + a[2]);
                }
                return true;
            }
            default:
                MessageUtil.send(s, "&c未知风暴子指令。");
                return true;
        }
    }

    // ============================ 网页用户 ============================

    private boolean webuser(CommandSender s, String[] a) {
        if (noPerm(s, "islandstorm.webuser")) return true;
        if (a.length < 2) {
            MessageUtil.send(s, "&c用法：/storm webuser <create|passwd|delete|list|grant|revoke> ...");
            return true;
        }
        switch (a[1].toLowerCase()) {
            case "list": {
                MessageUtil.raw(s, "&b===== 网页用户（共 " + plugin.webAuthManager().all().size() + "）=====");
                for (WebUser u : plugin.webAuthManager().all()) {
                    MessageUtil.raw(s, "&f" + u.username() + " &7权限：" + String.join(", ", u.permissions()));
                }
                return true;
            }
            case "create": {
                if (a.length < 4) {
                    MessageUtil.send(s, "&c用法：/storm webuser create <用户名> <密码>");
                    return true;
                }
                if (plugin.webAuthManager().createUser(a[2], a[3], List.of(WebPermission.WEATHER_VIEW))) {
                    MessageUtil.send(s, "&a已创建网页用户 &b" + a[2] + " &7(默认仅 weather.view，可用 grant 赋权)");
                } else {
                    MessageUtil.send(s, "&c用户已存在：&f" + a[2]);
                }
                return true;
            }
            case "passwd": {
                if (a.length < 4) {
                    MessageUtil.send(s, "&c用法：/storm webuser passwd <用户名> <新密码>");
                    return true;
                }
                if (plugin.webAuthManager().setPassword(a[2], a[3])) {
                    MessageUtil.send(s, "&a已修改 &b" + a[2] + " &a的密码。");
                } else {
                    MessageUtil.send(s, "&c用户不存在：&f" + a[2]);
                }
                return true;
            }
            case "delete": {
                if (a.length < 3) {
                    MessageUtil.send(s, "&c用法：/storm webuser delete <用户名>");
                    return true;
                }
                if (plugin.webAuthManager().deleteUser(a[2])) {
                    MessageUtil.send(s, "&a已删除网页用户 &b" + a[2]);
                } else {
                    MessageUtil.send(s, "&c用户不存在：&f" + a[2]);
                }
                return true;
            }
            case "grant": {
                if (a.length < 4) {
                    MessageUtil.send(s, "&c用法：/storm webuser grant <用户名> <权限节点>");
                    return true;
                }
                if (!WebPermission.isValid(a[3])) {
                    MessageUtil.send(s, "&c无效权限节点：&f" + a[3]);
                    return true;
                }
                if (plugin.webAuthManager().grant(a[2], a[3])) {
                    MessageUtil.send(s, "&a已为 &b" + a[2] + " &a赋予 &b" + a[3]);
                } else {
                    MessageUtil.send(s, "&c用户不存在或已拥有该权限。");
                }
                return true;
            }
            case "revoke": {
                if (a.length < 4) {
                    MessageUtil.send(s, "&c用法：/storm webuser revoke <用户名> <权限节点>");
                    return true;
                }
                if (plugin.webAuthManager().revoke(a[2], a[3])) {
                    MessageUtil.send(s, "&a已移除 &b" + a[2] + " &a的 &b" + a[3]);
                } else {
                    MessageUtil.send(s, "&c用户不存在或未拥有该权限。");
                }
                return true;
            }
            default:
                MessageUtil.send(s, "&c未知网页用户子指令。");
                return true;
        }
    }

    // ============================ TabComplete ============================

    @Override
    public List<String> onTabComplete(CommandSender s, Command command, String alias, String[] a) {
        if (a.length == 1) {
            return filter(Arrays.asList("help", "reload", "status", "setweather", "setwind",
                    "forecast", "html", "debug", "bypass", "region", "path", "webuser"), a[0]);
        }
        String sub = a[0].toLowerCase();
        switch (sub) {
            case "setweather":
                if (a.length == 2) return filter(weatherNames(), a[1]);
                if (a.length == 4) return filter(timeModes(), a[3]);
                break;
            case "setwind":
                if (a.length == 3) return filter(directionNames(), a[2]);
                break;
            case "forecast":
                if (a.length == 2) return filter(Arrays.asList("hourly", "add", "clear"), a[1]);
                if (a.length == 3 && a[1].equalsIgnoreCase("add")) return filter(weatherNames(), a[2]);
                if (a.length == 5 && a[1].equalsIgnoreCase("add")) return filter(timeModes(), a[4]);
                break;
            case "html":
                if (a.length == 2) return filter(Arrays.asList("preview", "forecast", "all", "open"), a[1]);
                break;
            case "bypass":
                if (a.length == 2) return filter(onlinePlayerNames(), a[1]);
                break;
            case "region":
                return regionTab(a);
            case "path":
                return pathTab(a);
            case "webuser":
                return webuserTab(a);
            default:
                break;
        }
        return new ArrayList<>();
    }

    private List<String> regionTab(String[] a) {
        if (a.length == 2) {
            return filter(Arrays.asList("list", "create", "delete", "setweather", "setwind"), a[1]);
        }
        String op = a[1].toLowerCase();
        if (a.length == 3 && !op.equals("create") && !op.equals("list")) {
            return filter(regionNames(), a[2]);
        }
        if (op.equals("setweather") && a.length == 4) return filter(regionWeatherNames(), a[3]);
        if (op.equals("setwind") && a.length == 5) return filter(directionNames(), a[4]);
        if (op.equals("create") && a.length == 9) return filter(regionWeatherNames(), a[8]);
        return new ArrayList<>();
    }

    private List<String> pathTab(String[] a) {
        if (a.length == 2) {
            return filter(Arrays.asList("list", "create", "addpoint", "start", "pause", "resume", "stop", "delete"), a[1]);
        }
        String op = a[1].toLowerCase();
        if (a.length == 3 && !op.equals("create") && !op.equals("list")) {
            return filter(pathIds(), a[2]);
        }
        if (op.equals("create") && a.length == 4) {
            return filter(Arrays.asList("TYPHOON", "EXTREME_STORM"), a[3]);
        }
        if (op.equals("addpoint") && a.length == 7) return filter(timeModes(), a[6]);
        return new ArrayList<>();
    }

    private List<String> webuserTab(String[] a) {
        if (a.length == 2) {
            return filter(Arrays.asList("create", "passwd", "delete", "list", "grant", "revoke"), a[1]);
        }
        String op = a[1].toLowerCase();
        if (a.length == 3 && (op.equals("passwd") || op.equals("delete")
                || op.equals("grant") || op.equals("revoke"))) {
            return filter(webUserNames(), a[2]);
        }
        if ((op.equals("grant") || op.equals("revoke")) && a.length == 4) {
            List<String> nodes = new ArrayList<>(WebPermission.ALL);
            nodes.add("*");
            return filter(nodes, a[3]);
        }
        return new ArrayList<>();
    }

    // ============================ 工具方法 ============================

    private boolean noPerm(CommandSender s, String perm) {
        if (s.hasPermission(perm) || s.hasPermission("islandstorm.admin")) return false;
        MessageUtil.send(s, MessageUtil.msg("no-permission", "&c你没有权限执行该操作。"));
        return true;
    }

    private static Double parseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(p)).collect(Collectors.toList());
    }

    private static List<String> weatherNames() {
        List<String> l = new ArrayList<>();
        for (WeatherType t : WeatherType.values()) l.add(t.name());
        return l;
    }

    /** 区域可用天气名（排除台风/极端风暴——这两个是风暴路径专属）。 */
    private static List<String> regionWeatherNames() {
        List<String> l = new ArrayList<>();
        for (WeatherType t : WeatherType.values()) {
            if (t == WeatherType.TYPHOON || t == WeatherType.EXTREME_STORM) continue;
            l.add(t.name());
        }
        return l;
    }

    private static List<String> directionNames() {
        List<String> l = new ArrayList<>();
        for (WindDirection d : WindDirection.values()) l.add(d.name());
        return l;
    }

    private static List<String> timeModes() {
        return Arrays.asList("REAL_TIME", "MC_TIME");
    }

    private List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
    }

    private List<String> regionNames() {
        return plugin.regionManager().all().stream().map(WeatherRegion::name).collect(Collectors.toList());
    }

    private List<String> pathIds() {
        return plugin.stormPathManager().all().stream().map(StormPath::id).collect(Collectors.toList());
    }

    private List<String> webUserNames() {
        return plugin.webAuthManager().all().stream().map(WebUser::username).collect(Collectors.toList());
    }
}
