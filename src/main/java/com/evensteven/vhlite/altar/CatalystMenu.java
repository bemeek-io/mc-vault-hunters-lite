package com.evensteven.vhlite.altar;

import com.evensteven.vhlite.item.VhItems;
import com.evensteven.vhlite.menu.Menu;
import com.evensteven.vhlite.player.CurrencyService;
import com.evensteven.vhlite.util.Text;
import com.evensteven.vhlite.vault.modifier.VaultModifier;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * The altar's catalyst shop: spend Vault Essence for a catalyst that forces
 * a chosen run modifier onto a crystal. Replaces the old echo-shard crafting
 * recipe now that essence is a currency, not a physical ingredient.
 */
public final class CatalystMenu extends Menu {

    private final CurrencyService currency;
    private final FileConfiguration config;

    public CatalystMenu(Player viewer, CurrencyService currency, FileConfiguration config) {
        super(viewer, 3, "§5Catalysts");
        this.currency = currency;
        this.config = config;
    }

    private int cost() {
        return config.getInt("altar.catalyst-essence-cost", 15);
    }

    @Override
    protected void build() {
        int cost = cost();
        long carrying = currency.essenceOf(viewer);
        icon(4, named(Material.PRISMARINE_SHARD, "§5Catalysts",
                "§7Spend Vault Essence for a catalyst", "§7that forces a chosen run modifier.",
                "§7Cost each: §3" + cost + " §7(you have §3" + carrying + "§7)"));

        int[] slots = {10, 11, 12, 14, 15, 16};
        VaultModifier[] mods = VaultModifier.values();
        for (int i = 0; i < mods.length && i < slots.length; i++) {
            VaultModifier mod = mods[i];
            boolean afford = carrying >= cost;
            button(slots[i], named(Material.PRISMARINE_SHARD, "§5" + mod.displayName,
                            "§8" + mod.description,
                            "§7Cost: §3" + cost,
                            afford ? "§aClick to buy" : "§cNot enough essence"),
                    event -> {
                        if (currency.spendEssence(viewer, cost)) {
                            VhItems.give(viewer, VhItems.catalyst(mod));
                            viewer.playSound(viewer.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
                            viewer.sendMessage(Text.c("§5Catalyst forged: " + mod.displayName));
                            refresh();
                        } else {
                            viewer.playSound(viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
                            viewer.sendMessage(Text.c("§cYou need §3" + cost + " Vault Essence§c."));
                        }
                    });
        }
        fillRow(0, Material.PURPLE_STAINED_GLASS_PANE);
        fillRow(2, Material.PURPLE_STAINED_GLASS_PANE);
    }
}
