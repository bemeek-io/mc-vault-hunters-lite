package com.evensteven.vhlite.altar;

import com.evensteven.vhlite.item.VhItemType;
import com.evensteven.vhlite.item.VhItems;
import com.evensteven.vhlite.player.ProfileStore;
import com.evensteven.vhlite.spirit.SpiritStore;
import com.evensteven.vhlite.util.Text;
import com.evensteven.vhlite.vault.VaultInstanceManager;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Any placed block of the configured altar material (default LODESTONE) is
 * a Vault Altar — stateless on purpose: nothing to register, nothing to
 * corrupt. Right-click empty-handed (or with resources) to see the crystal
 * bill; right-click holding a crystal to open the vault.
 */
public final class AltarListener implements Listener {

    private final FileConfiguration config;
    private final ProfileStore profiles;
    private final CrystalRecipeService recipes;
    private final SpiritStore spirits;
    private final VaultInstanceManager vaults;

    public AltarListener(FileConfiguration config, ProfileStore profiles,
            CrystalRecipeService recipes, SpiritStore spirits, VaultInstanceManager vaults) {
        this.config = config;
        this.profiles = profiles;
        this.recipes = recipes;
        this.spirits = spirits;
        this.vaults = vaults;
    }

    private Material altarMaterial() {
        Material material = Material.matchMaterial(config.getString("altar.block", "LODESTONE"));
        return material != null ? material : Material.LODESTONE;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null
                || event.getClickedBlock().getType() != altarMaterial()) {
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
