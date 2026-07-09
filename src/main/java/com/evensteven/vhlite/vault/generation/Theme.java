package com.evensteven.vhlite.vault.generation;

import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;

import java.util.Random;

/**
 * One visual+ecological identity for a vault: weighted block palettes (a
 * material appearing twice is twice as likely), a light block, the mobs that
 * hunt there, and the theme's boss. Palettes are resolved BlockData so the
 * async generator can sample them freely.
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
    public final EntityType[] mobs;
    public final EntityType bossType;
    public final String bossName;

    Theme(String name, String displayName, int minLevel, BlockData[] floor, BlockData[] wall,
            BlockData[] ceiling, BlockData[] pillar, BlockData[] accent, BlockData light,
            EntityType[] mobs, EntityType bossType, String bossName) {
        this.name = name;
        this.displayName = displayName;
        this.minLevel = minLevel;
        this.floor = floor;
        this.wall = wall;
        this.ceiling = ceiling;
        this.pillar = pillar;
        this.accent = accent;
        this.light = light;
        this.mobs = mobs;
        this.bossType = bossType;
        this.bossName = bossName;
    }

    public BlockData pick(BlockData[] palette, Random rng) {
        return palette[rng.nextInt(palette.length)];
    }

    public EntityType pickMob(Random rng) {
        return mobs[rng.nextInt(mobs.length)];
    }
}
