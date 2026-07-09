package com.evensteven.vhlite.spirit;

import com.evensteven.vhlite.menu.PagedMenu;
import com.evensteven.vhlite.player.CurrencyService;
import com.evensteven.vhlite.util.Text;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * The altar's spirit tab: every spirit you've lost, its essence price, and
 * one click to buy it back (if your balance covers it).
 */
public final class RevivalMenu extends PagedMenu<SpiritStore.Spirit> {

    private final SpiritStore spirits;
    private final FileConfiguration config;
    private final CurrencyService currency;

    public RevivalMenu(Player viewer, SpiritStore spirits, FileConfiguration config, CurrencyService currency) {
        super(viewer, "§3Spirit Revival");
        this.spirits = spirits;
        this.config = config;
        this.currency = currency;
    }

    private int costOf(SpiritStore.Spirit spirit) {
        return config.getInt("spirit.essence-cost-base", 8)
                + config.getInt("spirit.essence-cost-per-level", 2) * spirit.level();
    }

    @Override
    protected List<SpiritStore.Spirit> entries() {
        return spirits.spiritsOf(viewer.getUniqueId());
    }

    @Override
    protected ItemStack iconFor(SpiritStore.Spirit spirit) {
        long itemCount = spirit.items().stream().filter(java.util.Objects::nonNull).count();
        int cost = costOf(spirit);
        long carrying = currency.essenceOf(viewer);
        ItemStack icon = new ItemStack(Material.SOUL_LANTERN);
        icon.editMeta(meta -> {
            meta.displayName(Text.item("§3Spirit §7— vault level " + spirit.level()));
            meta.lore(Text.lore(
                    "§7Holding §e" + itemCount + "§7 item stacks and §e" + spirit.xpLevels() + "§7 XP levels",
                    "§7Price: §3" + cost + " Vault Essence §7(you have " + carrying + ")",
                    carrying >= cost ? "§a✔ Click to revive" : "§c✘ Not enough essence"));
        });
        return icon;
    }

    @Override
    protected void onEntryClick(SpiritStore.Spirit spirit, InventoryClickEvent event) {
        int cost = costOf(spirit);
        if (!currency.spendEssence(viewer, cost)) {
            viewer.sendMessage(Text.c("§cYou need §3" + cost + " Vault Essence§c."));
            viewer.playSound(viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
            return;
        }
        spirits.revive(viewer, spirit);
        viewer.sendMessage(Text.c("§bYour spirit returns — gear and experience restored."));
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1f, 1.4f);
        refresh();
    }

    @Override
    protected String infoLine() {
        return "§7Essence comes from vault kills. Gold is found in loot.";
    }
}
