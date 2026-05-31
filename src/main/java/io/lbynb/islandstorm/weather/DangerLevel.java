package io.lbynb.islandstorm.weather;

/** 危险等级（用于天气、区域、风暴与 HTML 卡片着色）。 */
public enum DangerLevel {
    NONE("无", "&a", "#3fb950"),
    LOW("低", "&a", "#58a6ff"),
    MEDIUM("中", "&e", "#d29922"),
    HIGH("高", "&6", "#f0883e"),
    EXTREME("极高", "&c", "#f85149");

    private final String display;
    private final String chatColor;
    private final String hexColor;

    DangerLevel(String display, String chatColor, String hexColor) {
        this.display = display;
        this.chatColor = chatColor;
        this.hexColor = hexColor;
    }

    public String display() {
        return display;
    }

    public String chatColor() {
        return chatColor;
    }

    /** 供 HTML 卡片使用的十六进制颜色。 */
    public String hexColor() {
        return hexColor;
    }

    public static DangerLevel fromString(String s, DangerLevel def) {
        if (s == null) return def;
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
