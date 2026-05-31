package io.lbynb.islandstorm.time;

import io.lbynb.islandstorm.config.ConfigManager;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 时间工具：双轨（现实时间 / MC 时间）解析与换算。
 *
 * <p>换算约定（与原版一致）：1 MC 天 = {@code minecraft-day-ticks}（默认 24000）ticks，
 * 1 MC 小时 = 天/24，1 MC 分钟 = 天/1440；按 20 TPS，1 tick = 50ms。
 * MC 世界时间 tick 0 对应游戏内 06:00。</p>
 */
public final class GameTimeUtil {

    private static final long TICK_MS = 50L; // 20 TPS

    private static long mcDayTicks = 24000L;
    private static ZoneId zone = ZoneId.systemDefault();
    private static DateTimeFormatter realTimeFmt = DateTimeFormatter.ofPattern("HH:mm");
    private static TimeMode defaultMode = TimeMode.REAL_TIME;

    private static final Pattern P = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*([a-zA-Z]*)\\s*$");

    private GameTimeUtil() {
    }

    /** 从配置加载换算参数（在 onEnable / reload 时调用）。 */
    public static void configure(ConfigManager cfg) {
        mcDayTicks = Math.max(24L, cfg.mcDayTicks());
        defaultMode = cfg.timeDefaultMode();
        try {
            zone = ZoneId.of(cfg.realTimeZone());
        } catch (Exception e) {
            zone = ZoneId.systemDefault();
        }
        try {
            realTimeFmt = DateTimeFormatter.ofPattern(cfg.displayRealTimeFormat());
        } catch (Exception e) {
            realTimeFmt = DateTimeFormatter.ofPattern("HH:mm");
        }
    }

    public static long mcDayTicks() {
        return mcDayTicks;
    }

    public static long mcHourTicks() {
        return mcDayTicks / 24;
    }

    public static double mcMinuteTicks() {
        return mcDayTicks / 1440.0;
    }

    public static TimeMode defaultMode() {
        return defaultMode;
    }

    /**
     * 解析时长字符串：30s / 30m / 1h / 1d / 1200ticks / 1mcminute / 6mchour / 1mcday。
     * 单位本身即可区分现实/ MC；纯数字时用 modeHint（为空再用配置默认模式）判别。失败返回 null。
     */
    public static ParsedDuration parse(String amount, TimeMode modeHint) {
        if (amount == null) return null;
        Matcher m = P.matcher(amount);
        if (!m.matches()) return null;
        double value;
        try {
            value = Double.parseDouble(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
        String unit = m.group(2) == null ? "" : m.group(2).toLowerCase(Locale.ROOT);

        switch (unit) {
            case "s": case "sec": case "secs":
                return realDuration((long) (value * 1000L));
            case "m": case "min": case "mins":
                return realDuration((long) (value * 60_000L));
            case "h": case "hr": case "hrs":
                return realDuration((long) (value * 3_600_000L));
            case "d": case "day": case "days":
                return realDuration((long) (value * 86_400_000L));
            case "tick": case "ticks": case "t":
                return mcDuration((long) value);
            case "mcminute": case "mcminutes": case "mcmin":
                return mcDuration(Math.round(value * mcMinuteTicks()));
            case "mchour": case "mchours":
                return mcDuration(Math.round(value * mcHourTicks()));
            case "mcday": case "mcdays":
                return mcDuration(Math.round(value * mcDayTicks));
            case "":
                TimeMode mh = (modeHint == null) ? defaultMode : modeHint;
                return (mh == TimeMode.MC_TIME)
                        ? mcDuration((long) value)
                        : realDuration((long) (value * 60_000L));
            default:
                return null;
        }
    }

    private static ParsedDuration realDuration(long realMillis) {
        return new ParsedDuration(TimeMode.REAL_TIME, realMillis / TICK_MS, realMillis, null);
    }

    private static ParsedDuration mcDuration(long ticks) {
        return new ParsedDuration(TimeMode.MC_TIME, ticks, ticks * TICK_MS, null);
    }

    /** MC 世界绝对时间（fullTime ticks）→ 当天 HH:mm（tick0 = 06:00）。 */
    public static String formatWorldTimeHHmm(long fullTimeTicks) {
        long tod = ((fullTimeTicks % mcDayTicks) + mcDayTicks) % mcDayTicks;
        double hourFloat = (tod / (double) mcHourTicks()) + 6.0; // tick0 = 06:00
        int hours = ((int) Math.floor(hourFloat)) % 24;
        int minutes = (int) Math.floor((hourFloat - Math.floor(hourFloat)) * 60);
        return String.format("%02d:%02d", hours, minutes);
    }

    /** MC 第几天（从 1 开始）。 */
    public static long mcDayNumber(long fullTimeTicks) {
        return fullTimeTicks / mcDayTicks + 1;
    }

    /** ticks 时长 → 现实等价时长（如 "5 分 0 秒"）。 */
    public static String formatTicksAsRealDuration(long ticks) {
        long totalSec = (ticks * TICK_MS) / 1000L;
        long h = totalSec / 3600, mnt = (totalSec % 3600) / 60, s = totalSec % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append(" 时 ");
        if (h > 0 || mnt > 0) sb.append(mnt).append(" 分 ");
        sb.append(s).append(" 秒");
        return sb.toString();
    }

    /** ticks 时长 → MC 等价时长（如 "6 MC 小时" / "1.5 MC 天"）。 */
    public static String formatTicksAsMcDuration(long ticks) {
        if (ticks >= mcDayTicks) {
            return trim(ticks / (double) mcDayTicks) + " MC 天";
        }
        return trim(ticks / (double) mcHourTicks()) + " MC 小时";
    }

    private static String trim(double d) {
        return (d == Math.floor(d)) ? String.valueOf((long) d) : String.format("%.1f", d);
    }

    /** 当前现实时间（按配置时区与格式）。 */
    public static String nowRealFormatted() {
        return ZonedDateTime.now(zone).format(realTimeFmt);
    }

    /** 现实毫秒时间戳 → HH:mm（配置时区/格式）。 */
    public static String formatRealMillis(long epochMillis) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone).format(realTimeFmt);
    }

    /** 是否到期（按现实时钟）。 */
    public static boolean isDue(long endEpochMillis) {
        return endEpochMillis > 0 && System.currentTimeMillis() >= endEpochMillis;
    }

    /** 双时间时长标签（现实 + MC），用于 HTML/聊天。 */
    public static String bothDurationLabel(long realMillis) {
        long ticks = realMillis / TICK_MS;
        return "现实 " + formatTicksAsRealDuration(ticks) + " / " + formatTicksAsMcDuration(ticks);
    }
}
