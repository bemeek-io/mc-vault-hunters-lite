package com.evensteven.vhlite.knowledge;

import org.bukkit.Material;

/**
 * The knowledge tree: features that start locked and are opened with
 * knowledge points, VH-style "unlock the mod" progression. Cost defaults
 * live in config under knowledge.costs.&lt;config-key&gt;.
 */
public enum ResearchNode {

    BACKPACK("backpack", Material.CHEST, "§6Backpacks",
            "§7Craft portable inventories that keep", "§7their contents wherever they go."),
    CHEST_LINKING("chest-linking", Material.ENDER_CHEST, "§dChest Linking",
            "§7Craft a Link Wand and merge your chests", "§7into one searchable interface."),
    ABILITY_HEAL("ability-heal", Material.GHAST_TEAR, "§aAbility: Vault Balm",
            "§7An item that mends your wounds.", "§8Claimed from this menu once researched."),
    ABILITY_DASH("ability-dash", Material.FEATHER, "§bAbility: Windstep",
            "§7An item that hurls you forward.", "§8Claimed from this menu once researched."),
    ABILITY_WARCRY("ability-warcry", Material.BLAZE_POWDER, "§cAbility: Warcry",
            "§7An item that emboldens nearby allies.", "§8Claimed from this menu once researched."),
    CATALYSTS("catalysts", Material.PRISMARINE_SHARD, "§5Catalysts",
            "§7Buy catalysts with Vault Essence at the", "§7altar to force modifiers onto crystals.");

    public final String configKey;
    public final Material icon;
    public final String displayName;
    public final String[] description;

    ResearchNode(String configKey, Material icon, String displayName, String... description) {
        this.configKey = configKey;
        this.icon = icon;
        this.displayName = displayName;
        this.description = description;
    }
}
