package com.evensteven.vhlite.item;

import org.bukkit.Material;

/**
 * Every custom item, expressed as a renamed+tagged vanilla item so vanilla
 * clients render it natively. Identification is ONLY by the PDC tag
 * (Keys.ITEM_TYPE), never by name or material, so anvil renames can't forge
 * anything.
 */
public enum VhItemType {

    VAULT_CRYSTAL(Material.AMETHYST_SHARD, "§dVault Crystal",
            "§7Right-click a Vault Altar to open a vault."),
    VAULT_ESSENCE(Material.ECHO_SHARD, "§3Vault Essence",
            "§7Torn from vault creatures. Powers spirit revival."),
    VAULT_MAP(Material.FILLED_MAP, "§dVault Map",
            "§7Charts the rooms your party has explored.", "§8Fades when you leave the vault."),
    KNOWLEDGE_STAR(Material.NETHER_STAR, "§bKnowledge Star",
            "§7Right-click to gain a knowledge point."),
    CATALYST(Material.PRISMARINE_SHARD, "§5Catalyst",
            "§7Craft together with a Vault Crystal", "§7to force a vault modifier."),
    BACKPACK(Material.CHEST, "§6Backpack",
            "§7Right-click to open. Its contents", "§7follow the pack, not the chest."),
    LINK_WAND(Material.BLAZE_ROD, "§eLink Wand",
            "§7Right-click chests to link them into", "§7your storage network. §e/vh storage"),
    ABILITY_HEAL(Material.GHAST_TEAR, "§aVault Balm §7(Ability)",
            "§7Right-click to mend your wounds.", "§860s cooldown"),
    ABILITY_DASH(Material.FEATHER, "§bWindstep §7(Ability)",
            "§7Right-click to dash forward.", "§815s cooldown"),
    ABILITY_WARCRY(Material.BLAZE_POWDER, "§cWarcry §7(Ability)",
            "§7Right-click to embolden nearby allies.", "§890s cooldown");

    public final Material material;
    public final String displayName;
    public final String[] lore;

    VhItemType(Material material, String displayName, String... lore) {
        this.material = material;
        this.displayName = displayName;
        this.lore = lore;
    }
}
