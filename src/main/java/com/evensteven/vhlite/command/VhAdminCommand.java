package com.evensteven.vhlite.command;

import com.evensteven.vhlite.item.VhItemType;
import com.evensteven.vhlite.item.VhItems;
import com.evensteven.vhlite.player.PlayerProfile;
import com.evensteven.vhlite.player.ProfileStore;
import com.evensteven.vhlite.skills.StatService;
import com.evensteven.vhlite.util.Text;
import com.evensteven.vhlite.vault.InstanceStore;
import com.evensteven.vhlite.vault.VaultInstance;
import com.evensteven.vhlite.vault.VaultInstanceManager;
import com.evensteven.vhlite.vault.modifier.VaultModifier;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /vhadmin — testing and rescue tooling. testgen is the generator's regression harness. */
public final class VhAdminCommand implements TabExecutor {

    private final JavaPlugin plugin;
    private final ProfileStore profiles;
    private final StatService stats;
    private final VaultInstanceManager vaults;
    private final InstanceStore store;

    public VhAdminCommand(JavaPlugin plugin, ProfileStore profiles, StatService stats,
            VaultInstanceManager vaults, InstanceStore store) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.stats = stats;
        this.vaults = vaults;
        this.store = store;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Text.c("§7/vhadmin <give|setlevel|addknowledge|testgen|endvault|purgeslots|reload>"));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, args);
            case "setlevel" -> setLevel(sender, args);
            case "addknowledge" -> addKnowledge(sender, args);
            case "testgen" -> testgen(sender, args);
            case "benchgen" -> vaults.benchGenerate(sender,
                    args.length > 1 ? Math.min(100, parseInt(args[1], 10)) : 10);
            case "endvault" -> endVault(sender, args);
            case "purgeslots" -> sender.sendMessage(Text.c("§7Retired slots pending purge: §e"
                    + store.retiredSlots().size() + "§7. Region files are deleted on next restart."));
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(Text.c("§aConfig reloaded. §7Some values (world, slots, recipes)"
                        + " only apply after a restart."));
            }
            default -> sender.sendMessage(Text.c("§cUnknown subcommand."));
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.c("§7/vhadmin give <crystal|essence|star|backpack|wand|"
                    + "catalyst_<mod>|heal|dash|warcry> [level/amount]"));
            return;
        }
        int amount = args.length > 2 ? parseInt(args[2], 1) : 1;
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "crystal" -> VhItems.give(player, VhItems.crystal(Math.max(1, amount), List.of()));
            case "essence" -> {
                var item = VhItems.create(VhItemType.VAULT_ESSENCE);
                item.setAmount(Math.max(1, Math.min(64, amount)));
                VhItems.give(player, item);
            }
            case "star" -> VhItems.give(player, VhItems.create(VhItemType.KNOWLEDGE_STAR));
            case "backpack" -> VhItems.give(player, VhItems.backpack());
            case "wand" -> VhItems.give(player, VhItems.create(VhItemType.LINK_WAND));
            case "altar" -> VhItems.give(player, VhItems.create(VhItemType.VAULT_ALTAR));
            case "gear" -> VhItems.give(player, com.evensteven.vhlite.item.VaultGear.unidentified(
                    Math.max(1, amount), new java.util.Random(), 0.5));
            case "heal" -> VhItems.give(player, VhItems.create(VhItemType.ABILITY_HEAL));
            case "dash" -> VhItems.give(player, VhItems.create(VhItemType.ABILITY_DASH));
            case "warcry" -> VhItems.give(player, VhItems.create(VhItemType.ABILITY_WARCRY));
            default -> {
                if (args[1].toLowerCase(Locale.ROOT).startsWith("catalyst_")) {
                    try {
                        VaultModifier modifier = VaultModifier.valueOf(
                                args[1].substring("catalyst_".length()).toUpperCase(Locale.ROOT));
                        VhItems.give(player, VhItems.catalyst(modifier));
                        return;
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                sender.sendMessage(Text.c("§cUnknown item."));
                return;
            }
        }
        sender.sendMessage(Text.c("§aGiven."));
    }

    private void setLevel(CommandSender sender, String[] args) {
        Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
        if (target == null || args.length < 3) {
            sender.sendMessage(Text.c("§7/vhadmin setlevel <player> <level>"));
            return;
        }
        PlayerProfile profile = profiles.get(target);
        profile.vaultLevel = Math.max(0, parseInt(args[2], 0));
        profile.vaultXp = 0;
        profiles.save(profile);
        stats.apply(target);
        sender.sendMessage(Text.c("§a" + target.getName() + " is now vault level " + profile.vaultLevel + "."));
    }

    private void addKnowledge(CommandSender sender, String[] args) {
        Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
        if (target == null || args.length < 3) {
            sender.sendMessage(Text.c("§7/vhadmin addknowledge <player> <points>"));
            return;
        }
        PlayerProfile profile = profiles.get(target);
        profile.knowledgePoints += parseInt(args[2], 1);
        profiles.save(profile);
        sender.sendMessage(Text.c("§a" + target.getName() + " now has "
                + profile.knowledgePoints + " knowledge points."));
    }

    /**
     * /vhadmin testgen [seed] [level] — full run pipeline with a fixed seed:
     * same seed, same vault. The regression tool for generation work.
     */
    private void testgen(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return;
        }
        Long seed = args.length > 1 ? parseLong(args[1]) : null;
        int level = args.length > 2 ? parseInt(args[2], 1) : Math.max(1, profiles.get(player).vaultLevel);
        sender.sendMessage(Text.c("§7Generating test vault (seed "
                + (seed != null ? seed : "random") + ", level " + level + ")..."));
        vaults.startRun(player, level, List.of(), seed);
    }

    private void endVault(CommandSender sender, String[] args) {
        Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1])
                : sender instanceof Player p ? p : null;
        if (target == null) {
            sender.sendMessage(Text.c("§7/vhadmin endvault <player>"));
            return;
        }
        VaultInstance instance = vaults.instanceOf(target);
        if (instance == null) {
            sender.sendMessage(Text.c("§7" + target.getName() + " is not in a vault."));
            return;
        }
        vaults.close(instance);
        sender.sendMessage(Text.c("§aVault closed."));
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private Long parseLong(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return (long) raw.hashCode(); // word seeds, like vanilla
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("give", "setlevel", "addknowledge", "testgen", "benchgen", "endvault", "purgeslots", "reload")
                    .stream().filter(o -> o.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> items = new java.util.ArrayList<>(List.of("crystal", "essence", "star",
                    "backpack", "wand", "altar", "gear", "heal", "dash", "warcry"));
            Arrays.stream(VaultModifier.values())
                    .forEach(m -> items.add("catalyst_" + m.name().toLowerCase(Locale.ROOT)));
            return items.stream().filter(o -> o.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
