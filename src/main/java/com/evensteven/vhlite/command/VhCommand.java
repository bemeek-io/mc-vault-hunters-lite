package com.evensteven.vhlite.command;

import com.evensteven.vhlite.knowledge.KnowledgeMenu;
import com.evensteven.vhlite.knowledge.KnowledgeService;
import com.evensteven.vhlite.knowledge.ResearchNode;
import com.evensteven.vhlite.menu.ChatPrompt;
import com.evensteven.vhlite.player.LevelService;
import com.evensteven.vhlite.player.PartyService;
import com.evensteven.vhlite.player.PlayerProfile;
import com.evensteven.vhlite.player.ProfileStore;
import com.evensteven.vhlite.skills.SkillMenu;
import com.evensteven.vhlite.skills.StatService;
import com.evensteven.vhlite.skills.StatType;
import com.evensteven.vhlite.spirit.SpiritStore;
import com.evensteven.vhlite.storage.ChestLinkService;
import com.evensteven.vhlite.storage.StorageMenu;
import com.evensteven.vhlite.util.Text;
import com.evensteven.vhlite.vault.VaultInstance;
import com.evensteven.vhlite.vault.VaultInstanceManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /vh — the player-facing command. Bare /vh opens the hub menu. */
public final class VhCommand implements TabExecutor {

    private final ProfileStore profiles;
    private final StatService stats;
    private final KnowledgeService knowledge;
    private final ChestLinkService links;
    private final ChatPrompt prompts;
    private final SpiritStore spirits;
    private final LevelService levels;
    private final PartyService parties;
    private final VaultInstanceManager vaults;

    public VhCommand(ProfileStore profiles, StatService stats, KnowledgeService knowledge,
            ChestLinkService links, ChatPrompt prompts, SpiritStore spirits, LevelService levels,
            PartyService parties, VaultInstanceManager vaults) {
        this.profiles = profiles;
        this.stats = stats;
        this.knowledge = knowledge;
        this.links = links;
        this.prompts = prompts;
        this.spirits = spirits;
        this.levels = levels;
        this.parties = parties;
        this.vaults = vaults;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length == 0) {
            new HubMenu(player, profiles, stats, knowledge, links, prompts, spirits, levels).open(player);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "stats" -> stats(player);
            case "skills" -> new SkillMenu(player, profiles, stats).open(player);
            case "knowledge" -> new KnowledgeMenu(player, profiles, knowledge).open(player);
            case "storage" -> {
                if (profiles.get(player).has(ResearchNode.CHEST_LINKING)) {
                    new StorageMenu(player, links, prompts, "").open(player);
                } else {
                    player.sendMessage(Text.c("§cResearch §dChest Linking§c first. §7(/vh knowledge)"));
                }
            }
            case "spirit", "spirits" -> {
                int count = spirits.spiritsOf(player.getUniqueId()).size();
                player.sendMessage(Text.c(count > 0
                        ? "§3" + count + " spirit(s) waiting. §7Revive them at a Vault Altar."
                        : "§7You have no trapped spirits."));
            }
            case "party" -> party(player, args);
            case "leave" -> {
                VaultInstance instance = vaults.instanceOf(player);
                if (instance == null) {
                    player.sendMessage(Text.c("§7You are not in a vault."));
                } else {
                    vaults.abandon(player, instance, "§7You abandoned the vault.");
                }
            }
            default -> player.sendMessage(Text.c(
                    "§7/vh §8[§7stats§8|§7skills§8|§7knowledge§8|§7storage§8|§7spirit§8|§7party§8|§7leave§8]"));
        }
        return true;
    }

    private void stats(Player player) {
        PlayerProfile profile = profiles.get(player);
        player.sendMessage(Text.c("§5— Vault Profile —"));
        player.sendMessage(Text.c("§7Level §d" + profile.vaultLevel + " §8(§7"
                + profile.vaultXp + "/" + levels.xpForLevel(profile.vaultLevel) + " xp§8)"));
        player.sendMessage(Text.c("§7Skill points: §e" + profile.skillPoints
                + " §8| §7Knowledge: §b" + profile.knowledgePoints));
        StringBuilder line = new StringBuilder("§7Stats: ");
        for (StatType stat : StatType.values()) {
            line.append(stat.displayName).append(" §e").append(profile.stat(stat)).append("§7  ");
        }
        player.sendMessage(Text.c(line.toString().trim()));
        player.sendMessage(Text.c(parties.describe(player)));
    }

    private void party(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Text.c(parties.describe(player)));
            player.sendMessage(Text.c("§7/vh party §8<§7invite§8|§7accept§8|§7decline§8|§7leave§8|§7kick§8>"));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "invite" -> {
                Player target = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
                if (target == null) {
                    player.sendMessage(Text.c("§c/vh party invite <online player>"));
                } else {
                    parties.invite(player, target);
                }
            }
            case "accept" -> parties.accept(player);
            case "decline" -> parties.decline(player);
            case "leave" -> parties.leave(player);
            case "kick" -> {
                Player target = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
                if (target == null) {
                    player.sendMessage(Text.c("§c/vh party kick <online player>"));
                } else {
                    parties.kick(player, target);
                }
            }
            default -> player.sendMessage(Text.c("§7/vh party §8<§7invite§8|§7accept§8|§7decline§8|§7leave§8|§7kick§8>"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("stats", "skills", "knowledge", "storage", "spirit", "party", "leave"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("party")) {
            return filter(List.of("invite", "accept", "decline", "leave", "kick"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("party")
                && (args[1].equalsIgnoreCase("invite") || args[1].equalsIgnoreCase("kick"))) {
            return null; // player names
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        return options.stream().filter(o -> o.startsWith(prefix.toLowerCase(Locale.ROOT))).toList();
    }
}
