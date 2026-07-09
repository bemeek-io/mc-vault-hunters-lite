package com.evensteven.vhlite.knowledge;

import com.evensteven.vhlite.item.VhItemType;
import com.evensteven.vhlite.item.VhItems;
import com.evensteven.vhlite.player.PlayerProfile;
import com.evensteven.vhlite.player.ProfileStore;
import com.evensteven.vhlite.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * Knowledge progression: points arrive every few vault levels (and from
 * Knowledge Stars); spending them unlocks whole features — backpacks, chest
 * linking, abilities, catalysts — the "unlock the mod" loop the user loved.
 */
public final class KnowledgeService {

    private final ProfileStore profiles;
    private final FileConfiguration config;
    /** Recipes to reveal in the recipe book when a node is researched. */
    private final Map<ResearchNode, List<NamespacedKey>> recipeKeys;
    private final com.evensteven.vhlite.quest.QuestService quests;

    public KnowledgeService(ProfileStore profiles, FileConfiguration config,
            Map<ResearchNode, List<NamespacedKey>> recipeKeys,
            com.evensteven.vhlite.quest.QuestService quests) {
        this.profiles = profiles;
        this.config = config;
        this.recipeKeys = recipeKeys;
        this.quests = quests;
    }

    public int cost(ResearchNode node) {
        return Math.max(1, config.getInt("knowledge.costs." + node.configKey, 2));
    }

    public boolean research(Player player, ResearchNode node) {
        PlayerProfile profile = profiles.get(player);
        if (profile.has(node)) {
            return false;
        }
        int cost = cost(node);
        if (profile.knowledgePoints < cost) {
            player.sendMessage(Text.c("§cYou need §b" + cost + "§c knowledge points. §7("
                    + profile.knowledgePoints + " available)"));
            return false;
        }
        profile.knowledgePoints -= cost;
        profile.research.add(node);
        profiles.save(profile);
        for (NamespacedKey key : recipeKeys.getOrDefault(node, List.of())) {
            player.discoverRecipe(key);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
        player.sendMessage(Text.c("§bResearched: " + node.displayName));
        quests.progress(player, com.evensteven.vhlite.quest.QuestType.SCHOLAR, 1);
        Bukkit.getServer().sendMessage(Text.c("§b" + player.getName()
                + " §7unlocked " + node.displayName + "§7!"));
        return true;
    }

    /** Ability nodes hand out their item on demand once researched. */
    public boolean claimAbilityItem(Player player, ResearchNode node) {
        VhItemType item = switch (node) {
            case ABILITY_HEAL -> VhItemType.ABILITY_HEAL;
            case ABILITY_DASH -> VhItemType.ABILITY_DASH;
            case ABILITY_WARCRY -> VhItemType.ABILITY_WARCRY;
            default -> null;
        };
        if (item == null || !profiles.get(player).has(node)) {
            return false;
        }
        if (VhItems.count(player, item) > 0) {
            player.sendMessage(Text.c("§7You already carry that ability item."));
            return false;
        }
        VhItems.give(player, VhItems.create(item));
        player.sendMessage(Text.c("§aAbility item claimed."));
        return true;
    }

    /** Knowledge Star right-click. */
    public void consumeStar(Player player) {
        PlayerProfile profile = profiles.get(player);
        profile.knowledgePoints++;
        profiles.save(profile);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.6f);
        player.sendMessage(Text.c("§bThe star's knowledge floods in. §7(+1 point, "
                + profile.knowledgePoints + " total)"));
    }
}
