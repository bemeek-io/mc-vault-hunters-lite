package com.evensteven.vhlite.command;

import com.evensteven.vhlite.knowledge.KnowledgeMenu;
import com.evensteven.vhlite.knowledge.KnowledgeService;
import com.evensteven.vhlite.knowledge.ResearchNode;
import com.evensteven.vhlite.menu.ChatPrompt;
import com.evensteven.vhlite.menu.Menu;
import com.evensteven.vhlite.player.PlayerProfile;
import com.evensteven.vhlite.player.ProfileStore;
import com.evensteven.vhlite.skills.SkillMenu;
import com.evensteven.vhlite.skills.StatService;
import com.evensteven.vhlite.spirit.SpiritStore;
import com.evensteven.vhlite.storage.ChestLinkService;
import com.evensteven.vhlite.storage.StorageMenu;
import com.evensteven.vhlite.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * /vh with no arguments: the front door to every screen, plus a glance at
 * level and points.
 */
public final class HubMenu extends Menu {

    private final ProfileStore profiles;
    private final StatService stats;
    private final KnowledgeService knowledge;
    private final ChestLinkService links;
    private final ChatPrompt prompts;
    private final SpiritStore spirits;
    private final com.evensteven.vhlite.player.LevelService levels;
    private final com.evensteven.vhlite.player.PartyService parties;
    private final org.bukkit.configuration.file.FileConfiguration config;
    private final com.evensteven.vhlite.quest.QuestService quests;
    private final com.evensteven.vhlite.player.CurrencyService currency;

    public HubMenu(Player viewer, ProfileStore profiles, StatService stats,
            KnowledgeService knowledge, ChestLinkService links, ChatPrompt prompts,
            SpiritStore spirits, com.evensteven.vhlite.player.LevelService levels,
            com.evensteven.vhlite.player.PartyService parties,
            org.bukkit.configuration.file.FileConfiguration config,
            com.evensteven.vhlite.quest.QuestService quests,
            com.evensteven.vhlite.player.CurrencyService currency) {
        super(viewer, 3, "§5Vault Hunters");
        this.profiles = profiles;
        this.stats = stats;
        this.knowledge = knowledge;
        this.links = links;
        this.prompts = prompts;
        this.spirits = spirits;
        this.levels = levels;
        this.parties = parties;
        this.config = config;
        this.quests = quests;
        this.currency = currency;
    }

    @Override
    protected void build() {
        PlayerProfile profile = profiles.get(viewer);
        icon(4, named(Material.AMETHYST_SHARD, "§d" + viewer.getName()
                        + " §7— Vault Level §d" + profile.vaultLevel,
                "§7XP: §e" + profile.vaultXp + "§7/§e" + levels.xpForLevel(profile.vaultLevel),
                "§7Skill points: §e" + profile.skillPoints,
                "§7Knowledge points: §b" + profile.knowledgePoints,
                "§7Essence: §3" + currency.essenceOf(viewer)
                        + " §7Gold: §6" + currency.formatGold(currency.goldOf(viewer)),
                "§8Forge crystals at a Vault Altar (lodestone)."));

        button(10, named(Material.PLAYER_HEAD, "§dVault Profile",
                        "§7Your full character sheet: level,", "§7stats, research, and spirits."),
                event -> new com.evensteven.vhlite.player.StatsMenu(
                        viewer, profiles, levels, parties, spirits, config, currency).open(viewer));

        button(11, named(Material.IRON_SWORD, "§cSkills",
                        "§7Invest points into Strength, Vitality,", "§7Swiftness, Fortune, Resilience."),
                event -> new SkillMenu(viewer, profiles, stats).open(viewer));

        button(12, named(Material.BOOK, "§bKnowledge",
                        "§7Unlock backpacks, chest linking,", "§7abilities, and catalysts."),
                event -> new KnowledgeMenu(viewer, profiles, knowledge).open(viewer));

        button(14, named(Material.CHEST, "§6Storage",
                        "§7Search everything in your", "§7linked chests at once."),
                event -> {
                    if (profiles.get(viewer).has(ResearchNode.CHEST_LINKING)) {
                        new StorageMenu(viewer, links, prompts, "").open(viewer);
                    } else {
                        viewer.sendMessage(Text.c("§cResearch §dChest Linking§c first. §7(/vh knowledge)"));
                    }
                });

        button(16, named(Material.WRITABLE_BOOK, "§6Quests",
                        "§7Your guided tour of the vaults —", "§7auto-tracked, with rewards."),
                event -> new com.evensteven.vhlite.quest.QuestMenu(viewer, profiles, quests).open(viewer));

        int spiritCount = spirits.spiritsOf(viewer.getUniqueId()).size();
        button(15, named(Material.SOUL_LANTERN,
                        spiritCount > 0 ? "§3Spirits §b(" + spiritCount + ")" : "§3Spirits",
                        spiritCount > 0 ? "§7Visit a Vault Altar to revive them."
                                : "§7Nothing lost. Keep it that way."),
                event -> viewer.sendMessage(Text.c(spiritCount > 0
                        ? "§7Right-click a §5Vault Altar§7 and open the Spirits tab."
                        : "§7You have no trapped spirits.")));

        fillRow(0, Material.PURPLE_STAINED_GLASS_PANE);
        fillRow(2, Material.PURPLE_STAINED_GLASS_PANE);
    }
}
