package com.evensteven.vhlite.spirit;

import com.evensteven.vhlite.player.ProfileStore;
import com.evensteven.vhlite.util.Text;
import com.evensteven.vhlite.vault.VaultInstance;
import com.evensteven.vhlite.vault.VaultInstanceManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

/**
 * Dying inside a vault. Below the grace level it costs nothing: keep
 * everything, respawn at the vault's start pad, carry on. At or above it,
 * everything carried feeds a Spirit (recoverable at an altar for essence)
 * and the player spectates the rest of the run. Deaths outside vaults are
 * untouched vanilla.
 */
public final class VaultDeathListener implements Listener {

    private final Plugin plugin;
    private final VaultInstanceManager manager;
    private final ProfileStore profiles;
    private final SpiritStore spirits;
    private final FileConfiguration config;

    public VaultDeathListener(Plugin plugin, VaultInstanceManager manager, ProfileStore profiles,
            SpiritStore spirits, FileConfiguration config) {
        this.plugin = plugin;
        this.manager = manager;
        this.profiles = profiles;
        this.spirits = spirits;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        VaultInstance instance = manager.instanceOf(player);
        if (instance == null) {
            return;
        }
        boolean grace = profiles.get(player).vaultLevel < config.getInt("grace-level", 5);
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        if (grace) {
            player.sendMessage(Text.c("§7Beginner's grace: you keep everything. Back to the start pad."));
            return;
        }
        // The spirit freezes (and clears) their inventory and XP.
        spirits.capture(player, instance.blueprint().level());
        instance.deadSpectators.add(player.getUniqueId());
        player.sendMessage(Text.c("§cYour spirit — and everything you carried — is trapped."
                + " §7Revive it at a Vault Altar with §3Vault Essence§7."));
        player.sendMessage(Text.c("§7Spectate the rest of the run, or §e/vh leave§7."));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        VaultInstance instance = manager.instanceOf(player);
        if (instance == null || instance.gen == null
                || instance.state != VaultInstance.State.ACTIVE) {
            return;
        }
        event.setRespawnLocation(instance.worldPos(instance.gen.startPad));
        if (instance.deadSpectators.contains(player.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && manager.instanceOf(player) == instance) {
                    player.setGameMode(GameMode.SPECTATOR);
                }
            });
        }
    }
}
