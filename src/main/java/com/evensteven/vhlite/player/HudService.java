package com.evensteven.vhlite.player;

import com.evensteven.vhlite.skills.StatType;
import com.evensteven.vhlite.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The always-on character HUD: a sidebar with vault level, XP, unspent
 * points, and stat spread — the at-a-glance sheet that can't live inside
 * the vanilla inventory screen (that GUI is client-rendered and closed to
 * servers). Team-prefix updates, so lines change without flicker.
 * Toggle per player with /vh hud.
 */
public final class HudService extends BukkitRunnable {

    private static final int LINES = 8;

    private final ProfileStore profiles;
    private final LevelService levels;
    private final CurrencyService currency;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public HudService(ProfileStore profiles, LevelService levels, CurrencyService currency) {
        this.profiles = profiles;
        this.levels = levels;
        this.currency = currency;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerProfile profile = profiles.get(player);
            if (!profile.hudEnabled) {
                Scoreboard old = boards.remove(player.getUniqueId());
                if (old != null && player.getScoreboard() == old) {
                    player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                }
                continue;
            }
            update(player, profile);
        }
    }

    public void toggle(Player player) {
        PlayerProfile profile = profiles.get(player);
        profile.hudEnabled = !profile.hudEnabled;
        profiles.save(profile);
        player.sendMessage(Text.c(profile.hudEnabled
                ? "§aHUD on." : "§7HUD off. §8(/vh hud to bring it back)"));
        run(); // apply immediately
    }

    private void update(Player player, PlayerProfile profile) {
        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(), id -> {
            Scoreboard fresh = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective objective = fresh.registerNewObjective("vhlite", Criteria.DUMMY,
                    Text.c("§5§l VAULT "));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            for (int line = 0; line < LINES; line++) {
                Team team = fresh.registerNewTeam("vh_line_" + line);
                String entry = "§" + Integer.toHexString(line) + "§r";
                team.addEntry(entry);
                objective.getScore(entry).setScore(LINES - line);
            }
            return fresh;
        });
        String[] lines = {
                "§7Level §d" + profile.vaultLevel
                        + " §8(" + profile.vaultXp + "/" + levels.xpForLevel(profile.vaultLevel) + ")",
                "§7Skill pts §e" + profile.skillPoints
                        + " §7Knowl. §b" + profile.knowledgePoints,
                "§3Ess §f" + profile.vaultEssence
                        + "  §6Au §f" + currency.formatGoldShort(profile.vaultGoldCopper),
                "§8" + "─".repeat(16),
                "§cStr §f" + profile.stat(StatType.STRENGTH)
                        + "  §6Vit §f" + profile.stat(StatType.VITALITY),
                "§bSwf §f" + profile.stat(StatType.SWIFTNESS)
                        + "  §aFor §f" + profile.stat(StatType.FORTUNE),
                "§9Res §f" + profile.stat(StatType.RESILIENCE),
                "§8/vh for menus",
        };
        for (int line = 0; line < LINES; line++) {
            Team team = board.getTeam("vh_line_" + line);
            if (team != null) {
                team.prefix(Text.c(lines[line]));
            }
        }
        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
    }
}
