package io.lbynb.islandstorm.damage;

import org.bukkit.Material;

import java.util.Set;

/**
 * 破坏等级分级工具。按需求把方块归入 1~5 级（数字越大破坏面越广），用 {@link #tierOf(Material)}
 * 返回某方块所属的最低可破坏等级；返回 0 表示任何等级都不破坏该方块。
 *
 * <p>判定基于 Material 名称特征，尽量跨版本稳定（1.20.6）。等级越高包含越多自然方块，
 * 但具体「黑名单 / 白名单 / 受保护方块」的最终裁决在 {@link BlockDamageManager} 中完成。</p>
 *
 * <ul>
 *   <li>1 级：树叶、草、花、火把等极脆弱植被。</li>
 *   <li>2 级：玻璃、木板、栅栏、作物、地毯、旗帜。</li>
 *   <li>3 级：泥土类、沙子、羊毛、砂砾、雪、干草。</li>
 *   <li>4 级：原木、菌柄、仙人掌、竹子、陶瓦、黏土、砂岩。</li>
 *   <li>5 级：石头、圆石、花岗岩等硬质方块（不建议默认开启）。</li>
 * </ul>
 */
public final class BlockDamageLevel {

    private BlockDamageLevel() {
    }

    private static final Set<String> SMALL_FLOWERS = Set.of(
            "DANDELION", "POPPY", "BLUE_ORCHID", "ALLIUM", "AZURE_BLUET",
            "RED_TULIP", "ORANGE_TULIP", "WHITE_TULIP", "PINK_TULIP",
            "OXEYE_DAISY", "CORNFLOWER", "LILY_OF_THE_VALLEY", "WITHER_ROSE",
            "TORCHFLOWER", "SUNFLOWER", "LILAC", "ROSE_BUSH", "PEONY");

    private static final Set<String> CROPS = Set.of(
            "WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART",
            "PUMPKIN_STEM", "MELON_STEM", "TORCHFLOWER_CROP", "PITCHER_CROP", "SWEET_BERRY_BUSH");

    private static final Set<String> DIRT_FAMILY = Set.of(
            "DIRT", "COARSE_DIRT", "GRASS_BLOCK", "PODZOL", "ROOTED_DIRT",
            "MUD", "FARMLAND", "DIRT_PATH", "MYCELIUM");

    /** 返回破坏该方块所需的最低等级（1~5）；0 表示永不在分级内。 */
    public static int tierOf(Material m) {
        String n = m.name();

        // 1 级：极脆弱植被
        if (n.endsWith("_LEAVES") || n.equals("GRASS") || n.equals("SHORT_GRASS")
                || n.equals("TALL_GRASS") || n.equals("FERN") || n.equals("LARGE_FERN")
                || n.equals("DEAD_BUSH") || n.endsWith("_SAPLING") || n.equals("VINE")
                || n.equals("SUGAR_CANE") || n.endsWith("TORCH") || n.equals("SEAGRASS")
                || n.equals("TALL_SEAGRASS") || n.endsWith("_MUSHROOM") || SMALL_FLOWERS.contains(n)) {
            return 1;
        }
        // 2 级：玻璃 / 木板 / 栅栏 / 作物 / 地毯 / 旗帜
        if (n.contains("GLASS") || n.endsWith("_PLANKS") || n.endsWith("_FENCE")
                || n.endsWith("_FENCE_GATE") || n.endsWith("_CARPET") || n.endsWith("_BANNER")
                || n.equals("LADDER") || CROPS.contains(n)) {
            return 2;
        }
        // 3 级：泥土类 / 沙 / 羊毛 / 砂砾 / 雪 / 干草
        if (DIRT_FAMILY.contains(n) || n.equals("SAND") || n.equals("RED_SAND")
                || n.endsWith("_WOOL") || n.equals("GRAVEL") || n.equals("SNOW")
                || n.equals("SNOW_BLOCK") || n.equals("HAY_BLOCK")) {
            return 3;
        }
        // 4 级：原木 / 菌柄 / 仙人掌 / 竹子 / 陶瓦 / 黏土 / 砂岩
        if (n.endsWith("_LOG") || n.endsWith("_WOOD") || n.endsWith("_STEM") || n.endsWith("_HYPHAE")
                || n.equals("CACTUS") || n.equals("BAMBOO") || n.equals("PUMPKIN") || n.equals("MELON")
                || n.equals("CLAY") || n.endsWith("TERRACOTTA") || n.contains("SANDSTONE")) {
            return 4;
        }
        // 5 级：硬质方块（不建议默认开启）
        if (n.equals("STONE") || n.equals("COBBLESTONE") || n.equals("GRANITE") || n.equals("DIORITE")
                || n.equals("ANDESITE") || n.equals("TUFF") || n.equals("NETHERRACK")
                || n.equals("BASALT") || n.contains("DRIPSTONE")) {
            return 5;
        }
        return 0;
    }
}
