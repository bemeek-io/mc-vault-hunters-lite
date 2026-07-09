package com.evensteven.vhlite.vault.generation;

import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;

import java.util.Random;

/**
 * One visual+ecological identity for a vault: weighted block palettes (a
 * material appearing twice is twice as likely), a light block, the mobs that
 * hunt there, and the theme's boss. Palettes are resolved BlockData so the
 * async generator can sample them freely.
 *
 * Mobs are split into melee and ranged pools so encounter composition can be
 * controlled explicitly (most groups melee-only; a minority get one ranged
 * member) instead of every spawn roll being able to land on a bow user.
 */
public final class Theme {

    public final String name;
    public final String displayName;
    public final int minLevel;
    public final BlockData[] floor;
    public final BlockData[] wall;
    public final BlockData[] ceiling;
    public final BlockData[] pillar;
    public final BlockData[] accent;
    public final BlockData light;
    public final EntityType[] melee;
    public final EntityType[] ranged;
    public final EntityType bossType;
    public final String bossName;

    Theme(String name, String displayName, int minLevel, BlockData[] floor, BlockData[] wall,
            BlockData[] ceiling, BlockData[] pillar, BlockData[] accent, BlockData light,
            EntityType[] melee, EntityType[] ranged, EntityType bossType, String bossName) {
        this.name = name;
        this.displayName = displayName;
        this.minLevel = minLevel;
        this.floor = floor;
        this.wall = wall;
        this.ceiling = ceiling;
        this.pillar = pillar;
        this.accent = accent;
        this.light = light;
        this.melee = melee;
        this.ranged = ranged;
        this.bossType = bossType;
        this.bossName = bossName;
    }

    public BlockData pick(BlockData[] palette, Random rng) {
        return palette[rng.nextInt(palette.length)];
    }

    public EntityType pickMelee(Random rng) {
        EntityType[] pool = melee.length > 0 ? melee : ranged;
        return pool[rng.nextInt(pool.length)];
    }

    public EntityType pickRanged(Random rng) {
        EntityType[] pool = ranged.length > 0 ? ranged : melee;
        return pool[rng.nextInt(pool.length)];
    }
}
