package com.evensteven.vhlite.vault.generation;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The six built-in themes. Constructed once on the main thread (BlockData
 * resolution), then read-only. Higher-level themes unlock as players climb,
 * so early vaults don't spoil the whole wardrobe.
 */
public final class ThemeRegistry {

    private final List<Theme> themes = new ArrayList<>();

    public void init() {
        GenBlocks.init();

        themes.add(new Theme("stone_keep", "§7Stone Keep", 0,
                palette(Material.DEEPSLATE_TILES, Material.DEEPSLATE_BRICKS, Material.CRACKED_DEEPSLATE_BRICKS),
                palette(Material.STONE_BRICKS, Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.MOSSY_STONE_BRICKS),
                palette(Material.STONE_BRICKS, Material.DEEPSLATE_BRICKS),
                palette(Material.POLISHED_DEEPSLATE),
                palette(Material.CHISELED_STONE_BRICKS, Material.COBBLESTONE),
                data(Material.OCHRE_FROGLIGHT),
                mobs(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER),
                EntityType.RAVAGER, "§cKeep Warden"));

        themes.add(new Theme("overgrown", "§aOvergrown Hollow", 0,
                palette(Material.MOSS_BLOCK, Material.MOSS_BLOCK, Material.MUD_BRICKS, Material.ROOTED_DIRT),
                palette(Material.MUD_BRICKS, Material.MOSSY_STONE_BRICKS, Material.MOSSY_COBBLESTONE),
                palette(Material.MOSSY_STONE_BRICKS, Material.MUD_BRICKS),
                palette(Material.OAK_LOG),
                palette(Material.AZALEA_LEAVES, Material.FLOWERING_AZALEA_LEAVES, Material.MOSS_BLOCK),
                data(Material.SHROOMLIGHT),
                mobs(EntityType.SPIDER, EntityType.CAVE_SPIDER, EntityType.DROWNED, EntityType.ZOMBIE),
                EntityType.EVOKER, "§aGrove Witch"));

        themes.add(new Theme("frozen_depths", "§bFrozen Depths", 5,
                palette(Material.PACKED_ICE, Material.PACKED_ICE, Material.SNOW_BLOCK, Material.BLUE_ICE),
                palette(Material.PACKED_ICE, Material.SNOW_BLOCK, Material.ICE),
                palette(Material.PACKED_ICE, Material.SNOW_BLOCK),
                palette(Material.BLUE_ICE),
                palette(Material.ICE, Material.SNOW_BLOCK),
                data(Material.SEA_LANTERN),
                mobs(EntityType.STRAY, EntityType.SKELETON, EntityType.SPIDER),
                EntityType.STRAY, "§bFrost King"));

        themes.add(new Theme("desert_tomb", "§6Desert Tomb", 8,
                palette(Material.SANDSTONE, Material.SMOOTH_SANDSTONE, Material.CUT_SANDSTONE),
                palette(Material.SANDSTONE, Material.SMOOTH_SANDSTONE, Material.CHISELED_SANDSTONE),
                palette(Material.SMOOTH_SANDSTONE, Material.SANDSTONE),
                palette(Material.CUT_SANDSTONE),
                palette(Material.CHISELED_SANDSTONE, Material.GOLD_BLOCK, Material.SANDSTONE),
                data(Material.OCHRE_FROGLIGHT),
                mobs(EntityType.HUSK, EntityType.SKELETON, EntityType.SPIDER),
                EntityType.HUSK, "§6Tomb Pharaoh"));

        themes.add(new Theme("nether_forge", "§4Nether Forge", 12,
                palette(Material.BLACKSTONE, Material.POLISHED_BLACKSTONE_BRICKS, Material.BASALT),
                palette(Material.BLACKSTONE, Material.POLISHED_BLACKSTONE, Material.CHISELED_POLISHED_BLACKSTONE),
                palette(Material.POLISHED_BLACKSTONE_BRICKS, Material.BLACKSTONE),
                palette(Material.POLISHED_BASALT),
                palette(Material.MAGMA_BLOCK, Material.GILDED_BLACKSTONE, Material.BLACKSTONE),
                data(Material.GLOWSTONE),
                mobs(EntityType.BLAZE, EntityType.MAGMA_CUBE, EntityType.WITHER_SKELETON),
                EntityType.BLAZE, "§4Forge Master"));

        themes.add(new Theme("end_rift", "§dEnd Rift", 16,
                palette(Material.END_STONE, Material.END_STONE_BRICKS, Material.PURPUR_BLOCK),
                palette(Material.END_STONE_BRICKS, Material.PURPUR_BLOCK, Material.PURPUR_PILLAR),
                palette(Material.END_STONE_BRICKS, Material.PURPUR_BLOCK),
                palette(Material.PURPUR_PILLAR),
                palette(Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.END_STONE),
                data(Material.PEARLESCENT_FROGLIGHT),
                mobs(EntityType.ENDERMAN, EntityType.SHULKER, EntityType.PHANTOM),
                EntityType.ENDERMAN, "§dRift Stalker"));
    }

    /** A random theme the given level has unlocked. */
    public Theme roll(int level, Random rng) {
        List<Theme> eligible = themes.stream().filter(t -> t.minLevel <= level).toList();
        return eligible.get(rng.nextInt(eligible.size()));
    }

    public Theme byName(String name) {
        return themes.stream().filter(t -> t.name.equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public List<Theme> all() {
        return themes;
    }

    private static BlockData data(Material material) {
        return Bukkit.createBlockData(material);
    }

    private static BlockData[] palette(Material... materials) {
        BlockData[] out = new BlockData[materials.length];
        for (int i = 0; i < materials.length; i++) {
            out[i] = Bukkit.createBlockData(materials[i]);
        }
        return out;
    }

    private static EntityType[] mobs(EntityType... types) {
        return types;
    }
}
