package com.evensteven.vhlite.item;

import org.bukkit.Material;

/**
 * Every custom item, expressed as a renamed+tagged vanilla item so vanilla
 * clients render it natively. Identification is ONLY by the PDC tag
 * (Keys.ITEM_TYPE), never by name or material, so anvil renames can't forge
 * anything.
 *
 * modelData ids are PINNED (never reuse or renumber): a server resource
 * pack can key custom textures off them at any point without code changes.
 */
public enum VhItemType {

    VAULT_CRYSTAL(1001, Material.AMETHYST_SHARD, "§dVault Crystal",
            "§7Right-click a Vault Altar to open a vault."),
    /**
     * Legacy only: essence is now a profile-bound currency (see
     * CurrencyService), never a physical item. This entry stays so
     * pre-update items in old inventories/backpacks can still be detected
     * and converted by the one-time join migration sweep.
     */
    VAULT_ESSENCE(1002, Material.ECHO_SHARD, "§3Vault Essence",
            "§7Torn from vault creatures. Powers spirit revival."),
    KNOWLEDGE_STAR(1003, Material.NETHER_STAR, "§bKnowledge Star",
            "§7Right-click to gain a knowledge point."),
    CATALYST(1004, Material.PRISMARINE_SHARD, "§5Catalyst",
            "§7Craft together with a Vault Crystal", "§7to force a vault modifier."),
    BACKPACK(1005, Material.CHEST, "§6Backpack",
            "§7Right-click to open. Its contents", "§7follow the pack, not the chest."),
    LINK_WAND(1006, Material.BLAZE_ROD, "§eLink Wand",
            "§7Right-click chests to link them into", "§7your storage network. §e/vh storage"),
    ABILITY_HEAL(1007, Material.GHAST_TEAR, "§aVault Balm §7(Ability)",
            "§7Right-click to mend your wounds.", "§860s cooldown"),
    ABILITY_DASH(1008, Material.FEATHER, "§bWindstep §7(Ability)",
            "§7Right-click to dash forward.", "§815s cooldown"),
    ABILITY_WARCRY(1009, Material.BLAZE_POWDER, "§cWarcry §7(Ability)",
            "§7Right-click to embolden nearby allies.", "§890s cooldown"),
    VAULT_MAP(1010, Material.FILLED_MAP, "§dVault Map",
            "§7Charts the rooms your party has explored.",
            "§7\"Swap Item with Offhand\" opens it as a menu.",
            "§8(Options > Controls — try rebinding it to G,",
            "§8unused by default and free of side effects.)",
            "§8Fades when you leave the vault."),
    /** Rolled by VaultGear, never by VhItems.create — material varies. */
    VAULT_GEAR(1011, Material.IRON_SWORD, "§fVaultforged Gear"),
    VAULT_ALTAR(1012, Material.LODESTONE, "§5Vault Altar",
            "§7Place it, then right-click to forge", "§7Vault Crystals and revive spirits."),
    VAULT_CRATE(1013, Material.BARREL, "§6Vault Crate",
            "§7Sealed spoils of a conquered vault.", "§aRight-click to pry it open."),
    /** Created by VaultGear.unidentified — material varies by piece. */
    UNIDENTIFIED_GEAR(1014, Material.IRON_SWORD, "§7Unidentified Gear");

    public final int modelData;
    public final Material material;
    public final String displayName;
    public final String[] lore;

    VhItemType(int modelData, Material material, String displayName, String... lore) {
        this.modelData = modelData;
        this.material = material;
        this.displayName = displayName;
        this.lore = lore;
    }
}
