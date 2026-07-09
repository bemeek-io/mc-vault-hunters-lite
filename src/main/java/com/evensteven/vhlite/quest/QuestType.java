package com.evensteven.vhlite.quest;

import org.bukkit.Material;

/**
 * The quest chain: a guided tour of every mechanic, auto-tracked (no
 * accepting needed). Quests unlock in a rolling window of three — finishing
 * one reveals the next — so the chain teaches, then challenges. Declaration
 * order IS chain order.
 */
public enum QuestType {

    BUILD_ALTAR(Material.LODESTONE, "Foundations",
            "Craft and place a Vault Altar.", 1, "4 Vault Essence"),
    FORGE_CRYSTAL(Material.AMETHYST_SHARD, "The First Key",
            "Infuse a Vault Crystal at an altar.", 1, "+1 knowledge point"),
    FIRST_DELVE(Material.COMPASS, "Into the Vault",
            "Complete a vault objective.", 1, "a Knowledge Star"),
    LOOTER(Material.CHEST, "Looter",
            "Open 10 vault chests.", 10, "8 Vault Essence"),
    CULLER(Material.IRON_SWORD, "Culler",
            "Slay 25 vault monsters.", 25, "a random Catalyst"),
    APPRENTICE(Material.EXPERIENCE_BOTTLE, "Apprentice",
            "Reach vault level 5.", 5, "+2 skill points"),
    SCHOLAR(Material.BOOK, "Scholar",
            "Research any knowledge node.", 1, "a Knowledge Star"),
    VAULTFORGED(Material.NETHERITE_SWORD, "Vaultforged",
            "Claim a piece of Vaultforged gear.", 1, "a bonus gear roll"),
    JOURNEYMAN(Material.EXPERIENCE_BOTTLE, "Journeyman",
            "Reach vault level 10.", 10, "2 Knowledge Stars"),
    SPECIALIST(Material.BEACON, "Specialist",
            "Complete every objective type once.", 5, "an epic gear roll"),
    GUARDIAN_BANE(Material.WITHER_SKELETON_SKULL, "Guardian's Bane",
            "Slay 5 vault guardians.", 5, "16 Vault Essence and a Catalyst"),
    MASTER(Material.NETHER_STAR, "Vault Master",
            "Reach vault level 20.", 20, "3 Knowledge Stars and an epic gear roll");

    public final Material icon;
    public final String displayName;
    public final String description;
    public final int target;
    public final String rewardText;

    QuestType(Material icon, String displayName, String description, int target, String rewardText) {
        this.icon = icon;
        this.displayName = displayName;
        this.description = description;
        this.target = target;
        this.rewardText = rewardText;
    }
}
