package com.evensteven.vhlite.player;

import com.evensteven.vhlite.util.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Vault XP and levels. XP trickles in from kills and chests during runs and
 * arrives in a burst on completion; levels grant skill points and, every few
 * levels, a knowledge point.
 */
public final class LevelService {

    private final ProfileStore profiles;
    private final FileConfiguration config;

    public LevelService(ProfileStore profiles, FileConfiguration config) {
        this.profiles = profiles;
        this.config = config;
    }

    public int xpForLevel(int level) {
        return config.getInt("xp.base", 100) + config.getInt("xp.per-level", 35) * level;
    }

    public void addXp(Player player, int amount) {
        if (amount <= 0) {
            return;
        }
        PlayerProfile profile = profiles.get(player);
        profile.vaultXp += amount;
        boolean leveled = false;
        while (profile.vaultXp >= xpForLevel(profile.vaultLevel)) {
            profile.vaultXp -= xpForLevel(profile.vaultLevel);
            profile.vaultLevel++;
            profile.skillPoints += config.getInt("skills.points-per-level", 1);
            int interval = Math.max(1, config.getInt("knowledge.levels-per-point", 3));
            if (profile.vaultLevel % interval == 0) {
                profile.knowledgePoints++;
            }
            leveled = true;
        }
        if (leveled) {
            profiles.save(profile);
            player.showTitle(Title.title(Text.c("§dVault Level " + profile.vaultLevel),
                    Text.c("§7+" + config.getInt("skills.points-per-level", 1) + " skill point")));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            Bukkit.getServer().sendMessage(Text.c("§d" + player.getName()
                    + " §7reached vault level §d" + profile.vaultLevel + "§7!"));
        }
    }
}
