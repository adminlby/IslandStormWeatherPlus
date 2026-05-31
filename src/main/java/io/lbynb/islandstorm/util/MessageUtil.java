package io.lbynb.islandstorm.util;

import io.lbynb.islandstorm.IslandStormPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * 消息与颜色工具：统一处理 {@code &} 颜色代码、前缀、广播，以及 messages.yml 文案模板。
 * 占位符形如 {@code {key}}，用 {@link #format(String, Object...)} 以 key/value 交替参数替换。
 */
public final class MessageUtil {

    private static String prefix = "&b[IslandStorm]&r ";
    private static YamlConfiguration messages;

    private MessageUtil() {
    }

    /** 从 messages.yml 加载文案与前缀。 */
    public static void init(IslandStormPlugin plugin) {
        File f = new File(plugin.getDataFolder(), "messages.yml");
        if (f.exists()) {
            messages = YamlConfiguration.loadConfiguration(f);
            prefix = messages.getString("prefix", prefix);
        } else {
            messages = new YamlConfiguration();
        }
    }

    public static String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    public static String prefixed(String s) {
        return color(prefix + (s == null ? "" : s));
    }

    public static void send(CommandSender to, String s) {
        to.sendMessage(prefixed(s));
    }

    public static void raw(CommandSender to, String s) {
        to.sendMessage(color(s));
    }

    @SuppressWarnings("deprecation")
    public static void broadcast(String s) {
        Bukkit.broadcastMessage(prefixed(s));
    }

    public static void console(String s) {
        Bukkit.getConsoleSender().sendMessage(prefixed(s));
    }

    /** 读取 messages.yml 文案；缺失时返回 def。 */
    public static String msg(String key, String def) {
        return messages == null ? def : messages.getString(key, def);
    }

    /** 用 key/value 交替参数替换模板里的 {key} 占位符。 */
    public static String format(String template, Object... kv) {
        if (template == null) return "";
        String out = template;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            out = out.replace("{" + kv[i] + "}", String.valueOf(kv[i + 1]));
        }
        return out;
    }
}
