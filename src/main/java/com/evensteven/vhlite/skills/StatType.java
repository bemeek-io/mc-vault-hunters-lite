package com.evensteven.vhlite.skills;

import org.bukkit.Material;

/**
 * The investable RPG stats. Each point is applied as a vanilla attribute
 * modifier (except Fortune, which the loot roller reads directly), so
 * vanilla clients see the effects with zero client support.
 */
public enum StatType {

    STRENGTH(Material.IRON_SWORD, "§cStrength", "§7+2% melee damage per point"),
    VITALITY(Material.GOLDEN_APPLE, "§6Vitality", "§7+1 max health (half a heart) per point"),
    SWIFTNESS(Material.SUGAR, "§bSwiftness", "§7+1.5% movement speed per point"),
    FORTUNE(Material.RABBIT_FOOT, "§aFortune", "§7+4% vault loot per point"),
    RESILIENCE(Material.SHIELD, "§9Resilience", "§7+0.5 armor per point");

    public final Material icon;
    public final String displayName;
    public final String description;

    StatType(Material icon, String displayName, String description) {
        this.icon = icon;
        this.displayName = displayName;
        this.description = description;
    }
}
