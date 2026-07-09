package com.evensteven.vhlite.storage;

import com.evensteven.vhlite.item.VhItemType;
import com.evensteven.vhlite.item.VhItems;
import com.evensteven.vhlite.menu.Menu;
import com.evensteven.vhlite.util.Text;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Backpacks: a right-click opens the stash bound to that item's id. The
 * contents live in storage.yml, so the pack works anywhere and survives
 * death (the item itself can still be lost to a spirit — that's the trade).
 * Backpacks refuse to nest.
 */
public final class BackpackService {

    private final StorageStore store;
    private final FileConfiguration config;
    /** Backpack ids currently open, to stop two viewers clobbering each other. */
    private final Set<UUID> openIds = new HashSet<>();

    public BackpackService(StorageStore store, FileConfiguration config) {
        this.store = store;
        this.config = config;
    }

    public void open(Player player, ItemStack backpackItem) {
        UUID id = VhItems.backpackId(backpackItem);
        if (id == null) {
            player.sendMessage(Text.c("§cThis backpack is broken (no id). Craft a new one."));
            return;
        }
        if (!openIds.add(id)) {
            player.sendMessage(Text.c("§7That backpack is already open."));
            return;
        }
        int size = Math.max(9, Math.min(54, config.getInt("storage.backpack-size", 27)));
        new BackpackMenu(player, id, size / 9).open(player);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.6f, 1.3f);
    }

    /** Interactive menu: items move freely, except backpacks themselves. */
    private final class BackpackMenu extends Menu {

        private final UUID backpackId;

        BackpackMenu(Player viewer, UUID backpackId, int rows) {
            super(viewer, rows, "§6Backpack");
            this.backpackId = backpackId;
        }

        @Override
        protected boolean interactive() {
            return true;
        }

        @Override
        protected void build() {
            List<ItemStack> items = store.backpackItems(backpackId, inventory().getSize());
            for (int i = 0; i < inventory().getSize() && i < items.size(); i++) {
                inventory().setItem(i, items.get(i));
            }
        }

        @Override
        protected void onUnhandledClick(InventoryClickEvent event) {
            // A backpack on the cursor (or swapped from the hotbar) must not
            // enter the top inventory.
            if (isBackpack(event.getCursor())) {
                event.setCancelled(true);
                return;
            }
            if (event.getAction() == InventoryAction.HOTBAR_SWAP && event.getHotbarButton() >= 0
                    && isBackpack(viewer.getInventory().getItem(event.getHotbarButton()))) {
                event.setCancelled(true);
            }
        }

        @Override
        protected void onLowerClick(InventoryClickEvent event) {
            // Shift-clicking a backpack up from the player inventory.
            if (event.isShiftClick() && isBackpack(event.getCurrentItem())) {
                event.setCancelled(true);
            }
        }

        @Override
        protected void onClose(Player player) {
            store.saveBackpack(backpackId, inventory().getContents());
            openIds.remove(backpackId);
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.6f, 1.3f);
        }

        private boolean isBackpack(ItemStack item) {
            return VhItems.typeOf(item) == VhItemType.BACKPACK;
        }
    }
}
