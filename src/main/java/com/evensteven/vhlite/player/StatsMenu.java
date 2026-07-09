package com.evensteven.vhlite.player;

import com.evensteven.vhlite.knowledge.ResearchNode;
import com.evensteven.vhlite.menu.Menu;
import com.evensteven.vhlite.skills.StatType;
import com.evensteven.vhlite.spirit.SpiritStore;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * The full character sheet: level and XP, every stat with its computed
 * bonus, research progress, and spirits — one glanceable read-only screen.
 */
public final class StatsMenu extends Menu {

    private final ProfileStore profiles;
    private final LevelService levels;
    private final PartyService parties;
    private final SpiritStore spirits;
    private final FileConfiguration config;
    private final CurrencyService currency;

    public StatsMenu(Player viewer, ProfileStore profiles, LevelService levels,
            PartyService parties, SpiritStore spirits, FileConfiguration config,
            CurrencyService currency) {
        super(viewer, 4, "§dVault Profile");
        this.profiles = profiles;
        this.levels = levels;
        this.parties = parties;
        this.spirits = spirits;
        this.config = config;
        this.currency = currency;
    }

    @Override
    protected void build() {
        PlayerProfile profile = profiles.get(viewer);

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(meta -> {
            ((SkullMeta) meta).setOwningPlayer(viewer);
            meta.displayName(com.evensteven.vhlite.util.Text.item(
                    "§d" + viewer.getName() + " §7— Vault Level §d" + profile.vaultLevel));
            meta.lore(com.evensteven.vhlite.util.Text.lore(
                    "§7XP: §e" + profile.vaultXp + "§7/§e" + levels.xpForLevel(profile.vaultLevel),
                    "§7Skill points to spend: §e" + profile.skillPoints,
                    "§7Knowledge points to spend: §b" + profile.knowledgePoints,
                    parties.describe(viewer)));
        });
        icon(4, head);

        int[] statSlots = {10, 11, 12, 13, 14};
        StatType[] types = StatType.values();
        for (int i = 0; i < types.length; i++) {
            StatType stat = types[i];
            int invested = profile.stat(stat);
            icon(statSlots[i], named(stat.icon, stat.displayName + " §7" + invested,
                    stat.description,
                    "§8Current bonus: " + bonusLine(stat, invested)));
        }

        List<String> researched = new ArrayList<>();
        for (ResearchNode node : ResearchNode.values()) {
            researched.add((profile.has(node) ? "§a✔ " : "§8✘ ") + node.displayName);
        }
        icon(21, named(Material.BOOK, "§bResearch §7("
                        + profile.research.size() + "/" + ResearchNode.values().length + ")",
                researched.toArray(String[]::new)));

        int spiritCount = spirits.spiritsOf(viewer.getUniqueId()).size();
        icon(23, named(Material.SOUL_LANTERN,
                spiritCount > 0 ? "§3Spirits waiting: §b" + spiritCount : "§3No trapped spirits",
                spiritCount > 0 ? "§7Revive them at a Vault Altar." : "§7Keep it that way."));

        icon(19, named(Material.ECHO_SHARD, "§3Vault Essence: §b" + currency.essenceOf(viewer),
                "§7Earned from vault kills.", "§7Spent reviving spirits at the altar."));
        icon(25, named(Material.GOLD_NUGGET, "§6Vault Gold",
                "§7" + currency.formatGold(currency.goldOf(viewer)),
                "§7Found in vault loot. §89 copper = 1 silver,",
                "§89 silver = 1 gold, 9 gold = 1 platinum."));

        fillRow(0, Material.MAGENTA_STAINED_GLASS_PANE);
        fillRow(3, Material.MAGENTA_STAINED_GLASS_PANE);
    }

    private String bonusLine(StatType stat, int invested) {
        return switch (stat) {
            case STRENGTH -> "+" + Math.round(invested
                    * config.getDouble("skills.strength-damage-per-point", 0.02) * 100) + "% melee damage";
            case VITALITY -> "+" + (invested
                    * config.getDouble("skills.vitality-hp-per-point", 1.0)) / 2.0 + " hearts";
            case SWIFTNESS -> "+" + Math.round(invested
                    * config.getDouble("skills.swiftness-speed-per-point", 0.015) * 100) + "% speed";
            case FORTUNE -> "+" + Math.round(invested
                    * config.getDouble("skills.fortune-loot-per-point", 0.04) * 100) + "% vault loot";
            case RESILIENCE -> "+" + (invested
                    * config.getDouble("skills.resilience-armor-per-point", 0.5)) + " armor";
        };
    }
}
