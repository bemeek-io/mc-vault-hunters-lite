package com.evensteven.vhlite.player;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Shows the player's VAULT level and progress on the vanilla XP bar. The
 * update is client-side only (sendExperienceChange), so real XP — the stuff
 * enchanting and anvils spend — is untouched on the server; while an XP-
 * spending screen is open the bar flips back to the real values so costs
 * read correctly.
 */
public final class XpBarTask extends BukkitRunnable {

    private final ProfileStore profiles;
    private final LevelService levels;

    public XpBarTask(ProfileStore profiles, LevelService levels) {
        this.profiles = profiles;
        this.levels = levels;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryType open = player.getOpenInventory().getType();
            if (open == InventoryType.ENCHANTING || open == InventoryType.ANVIL
                    || open == InventoryType.GRINDSTONE) {
                // Real XP matters here; put the truth back on the bar.
                player.sendExperienceChange(player.getExp(), player.getLevel());
                continue;
            }
            PlayerProfile profile = profiles.get(player);
            float progress = Math.min(0.999f,
                    profile.vaultXp / (float) Math.max(1, levels.xpForLevel(profile.vaultLevel)));
            player.sendExperienceChange(progress, profile.vaultLevel);
        }
    }
}
