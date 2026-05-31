package io.lbynb.islandstorm.damage;

/** 方块被破坏时的表现方式。 */
public enum BlockDamageMode {
    /** 直接变空气（不掉落）。 */
    AIR,
    /** 自然破坏并掉落物品。 */
    DROP,
    /** 变成下落的方块实体（FallingBlock），更有“被吹飞”观感。 */
    FALLING_BLOCK;

    public static BlockDamageMode fromString(String s, BlockDamageMode def) {
        if (s == null) return def;
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
