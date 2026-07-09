package com.evensteven.vhlite.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Every NamespacedKey the plugin writes, in one place, so PDC tags and
 * attribute modifiers never drift out of sync between writer and reader.
 */
public final class Keys {

    /** PDC on items: which custom item this is (VhItemType name). */
    public static NamespacedKey ITEM_TYPE;
    /** PDC on crystals: the vault level the crystal was infused at. */
    public static NamespacedKey CRYSTAL_LEVEL;
    /** PDC on crystals: comma-separated forced modifier names. */
    public static NamespacedKey CRYSTAL_MODIFIERS;
    /** PDC on catalysts: the modifier this catalyst forces. */
    public static NamespacedKey CATALYST_MODIFIER;
    /** PDC on backpacks: which stored inventory this backpack opens. */
    public static NamespacedKey BACKPACK_ID;
    /** PDC on entities: UUID of the vault instance that owns the mob. */
    public static NamespacedKey INSTANCE_ID;
    /** PDC on entities: set on defend-objective wave mobs. */
    public static NamespacedKey WAVE_MOB;
    /** PDC on entities: nameplate base label ("Lv.7 Skeleton"). */
    public static NamespacedKey MOB_BASE;
    /** Attribute modifier key for elite encounter mobs' bonus health. */
    public static NamespacedKey MOB_ELITE;

    /** Attribute modifier keys for vault mob scaling. */
    public static NamespacedKey MOB_HEALTH;
    public static NamespacedKey MOB_DAMAGE;
    public static NamespacedKey MOB_SPEED;

    /** Attribute modifier keys for player stat investments. */
    public static NamespacedKey STAT_STRENGTH;
    public static NamespacedKey STAT_VITALITY;
    public static NamespacedKey STAT_SWIFTNESS;
    public static NamespacedKey STAT_RESILIENCE;
    /** Attribute modifier key for the Rush modifier's speed bonus. */
    public static NamespacedKey RUSH_SPEED;

    private Keys() {
    }

    public static void init(Plugin plugin) {
        ITEM_TYPE = new NamespacedKey(plugin, "item");
        CRYSTAL_LEVEL = new NamespacedKey(plugin, "crystal_level");
        CRYSTAL_MODIFIERS = new NamespacedKey(plugin, "crystal_modifiers");
        CATALYST_MODIFIER = new NamespacedKey(plugin, "catalyst_modifier");
        BACKPACK_ID = new NamespacedKey(plugin, "backpack_id");
        INSTANCE_ID = new NamespacedKey(plugin, "instance_id");
        WAVE_MOB = new NamespacedKey(plugin, "wave_mob");
        MOB_BASE = new NamespacedKey(plugin, "mob_base");
        MOB_ELITE = new NamespacedKey(plugin, "mob_elite");
        MOB_HEALTH = new NamespacedKey(plugin, "vault_mob_health");
        MOB_DAMAGE = new NamespacedKey(plugin, "vault_mob_damage");
        MOB_SPEED = new NamespacedKey(plugin, "vault_mob_speed");
        STAT_STRENGTH = new NamespacedKey(plugin, "stat_strength");
        STAT_VITALITY = new NamespacedKey(plugin, "stat_vitality");
        STAT_SWIFTNESS = new NamespacedKey(plugin, "stat_swiftness");
        STAT_RESILIENCE = new NamespacedKey(plugin, "stat_resilience");
        RUSH_SPEED = new NamespacedKey(plugin, "rush_speed");
    }
}
