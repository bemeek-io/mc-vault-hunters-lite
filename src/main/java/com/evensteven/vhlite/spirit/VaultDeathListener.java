package com.evensteven.vhlite.spirit;

import com.evensteven.vhlite.util.Text;
import com.evensteven.vhlite.vault.VaultInstance;
import com.evensteven.vhlite.vault.VaultInstanceManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Dying in a vault ENDS YOUR RUN — you respawn back where you entered, and
 * a solo vault closes behind you. What death costs depends on the VAULT's
 * level and the server difficulty:
 *
 *   normal:   vaults up to grace-level are free (keep everything);
 *             above, your carried items feed a Spirit (essence buy-back).
 *   hardcore: no free deaths — vaults up to grace-level feed a Spirit;
 *             above, everything you carried is gone for good.
 *
 * Deaths outside vaults are untouched vanilla.
 */
public final class VaultDeathListener implements Listener {

    private final Plugin plugin;
    private final VaultInstanceManager manager;
    private final SpiritStore spirits;
    private final FileConfiguration config;
    /** Players whose next respawn is a death-exit from their vault. */
    private final Set<UUID> pendingExit = new HashSet<>();

    public VaultDeathListener(Plugin plugin, VaultInstanceManager manager,
            SpiritStore spirits, FileConfiguration config) {
        this.plugin = plugin;
        this.manager = manager;
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
        // Nothing hits the floor of a doomed vault either way.
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);

        int vaultLevel = instance.blueprint().level();
        int grace = config.getInt("grace-level", 10);
        boolean hardcore = config.getString("difficulty", "normal").equalsIgnoreCase("hardcore");

        if (!hardcore && vaultLevel <= grace) {
            player.sendMessage(Text.c("§7Beginner's grace: you keep everything,"
                    + " but the vault is over."));
        } else if (!hardcore || vaultLevel <= grace) {
            spirits.capture(player, vaultLevel); // freezes and clears inventory + XP
            player.sendMessage(Text.c("§cYour spirit — and everything you carried — is trapped."
                    + " §7Buy it back at a Vault Altar with §3Vault Essence§7."));
        } else {
            player.getInventory().clear();
            player.setLevel(0);
            player.setExp(0f);
            player.setTotalExperience(0);
            player.sendMessage(Text.c("§4The vault devours what it kills."
                    + " §7(hardcore: no spirit above vault level " + grace + ")"));
        }
        pendingExit.add(player.getUniqueId());
        for (Player member : instance.players()) {
            if (!member.equals(player)) {
                member.sendMessage(Text.c("§c" + player.getName() + " §7fell — their run is over."));
            }
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!pendingExit.remove(player.getUniqueId())) {
            return;
        }
        VaultInstance instance = manager.instanceOf(player);
        if (instance == null) {
            return;
        }
        event.setRespawnLocation(instance.returnLocation(player.getUniqueId()));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && manager.instanceOf(player) == instance) {
                manager.finishDeathExit(player, instance);
            }
        });
    }
}
