package com.evensteven.vhlite.altar;

import com.evensteven.vhlite.item.VhItemType;
import com.evensteven.vhlite.item.VhItems;
import com.evensteven.vhlite.player.ProfileStore;
import com.evensteven.vhlite.spirit.SpiritStore;
import com.evensteven.vhlite.util.Text;
import com.evensteven.vhlite.vault.VaultInstanceManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The Vault Altar is a CRAFTED custom item (a tagged lodestone): placing it
 * registers the block as an altar; only registered blocks respond. Breaking
 * one hands the altar item back. Right-click empty-handed (or with
 * resources) to see the crystal bill; right-click holding a crystal to open
 * the vault.
 */
public final class AltarListener implements Listener {

    private final FileConfiguration config;
    private final ProfileStore profiles;
    private final CrystalRecipeService recipes;
    private final SpiritStore spirits;
    private final VaultInstanceManager vaults;
    private final AltarStore altars;

    public AltarListener(FileConfiguration config, ProfileStore profiles,
            CrystalRecipeService recipes, SpiritStore spirits, VaultInstanceManager vaults,
            AltarStore altars) {
        this.config = config;
        this.profiles = profiles;
        this.recipes = recipes;
        this.spirits = spirits;
        this.vaults = vaults;
        this.altars = altars;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        if (VhItems.typeOf(event.getItemInHand()) != VhItemType.VAULT_ALTAR) {
            return;
        }
        altars.add(event.getBlock().getLocation());
        event.getPlayer().sendMessage(Text.c("§5The altar settles into the ground."
                + " §7Right-click it to begin."));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(org.bukkit.event.block.BlockBreakEvent event) {
        if (!altars.isAltar(event.getBlock().getLocation())) {
            return;
        }
        altars.remove(event.getBlock().getLocation());
        event.setDropItems(false); // no plain lodestone; the altar comes back whole
        event.getBlock().getWorld().dropItemNaturally(
                event.getBlock().getLocation().add(0.5, 0.5, 0.5),
                VhItems.create(VhItemType.VAULT_ALTAR));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null
                || !altars.isAltar(event.getClickedBlock().getLocation())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();

        if (VhItems.typeOf(held) == VhItemType.VAULT_CRYSTAL) {
            int level = VhItems.crystalLevel(held);
            var forced = VhItems.crystalModifiers(held);
            if (vaults.startRun(player, level, forced, null)) {
                held.setAmount(held.getAmount() - 1);
                player.sendMessage(Text.c("§dThe crystal shatters against the altar..."));
            }
            return;
        }
        new AltarMenu(player, profiles, recipes, spirits, config).open(player);
    }
}
