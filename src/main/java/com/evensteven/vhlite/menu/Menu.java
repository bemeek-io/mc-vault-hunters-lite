package com.evensteven.vhlite.menu;

import com.evensteven.vhlite.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A chest-GUI menu. Modal by default: every click and drag is cancelled and
 * only registered buttons do anything. Subclasses that need real item
 * movement (backpacks) override {@link #interactive()}.
 *
 * Usage: subclass, call {@code button(...)}/{@code icon(...)} in build(),
 * then {@code open(player)}. One MenuManager instance routes all events.
 */
public abstract class Menu {

    private final MenuHolder holder = new MenuHolder(this);
    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> buttons = new HashMap<>();
    protected final Player viewer;

    protected Menu(Player viewer, int rows, String title) {
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(holder, rows * 9, Text.c(title));
        holder.setInventory(inventory);
    }

    /** Populate (or repopulate) the inventory. Called by open() and refresh(). */
    protected abstract void build();

    /** Modal menus (default) cancel every click; interactive ones let items move. */
    protected boolean interactive() {
        return false;
    }

    public final void open(Player player) {
        buttons.clear();
        inventory.clear();
        build();
        player.openInventory(inventory);
    }

    /** Rebuilds in place while the menu is open. */
    public final void refresh() {
        buttons.clear();
        inventory.clear();
        build();
    }

    // ------------------------------------------------------------- building

    protected final void button(int slot, ItemStack icon, Consumer<InventoryClickEvent> onClick) {
        inventory.setItem(slot, icon);
        if (onClick != null) {
            buttons.put(slot, onClick);
        }
    }

    protected final void icon(int slot, ItemStack icon) {
        inventory.setItem(slot, icon);
    }

    protected final void fillRow(int row, Material material) {
        ItemStack filler = new ItemStack(material);
        filler.editMeta(meta -> meta.displayName(Text.item("§7")));
        for (int slot = row * 9; slot < (row + 1) * 9; slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, filler);
            }
        }
    }

    protected final ItemStack named(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Text.item(name));
            if (lore.length > 0) {
                meta.lore(Text.lore(lore));
            }
        });
        return item;
    }

    // -------------------------------------------------------------- routing

    final void handleClick(InventoryClickEvent event) {
        if (!interactive()) {
            event.setCancelled(true);
        }
        if (!inventory.equals(event.getClickedInventory())) {
            onLowerClick(event);
            return;
        }
        Consumer<InventoryClickEvent> handler = buttons.get(event.getSlot());
        if (handler != null) {
            event.setCancelled(true);
            handler.accept(event);
        } else {
            onUnhandledClick(event);
        }
    }

    /** Clicks in the player's own inventory while this menu is open. */
    protected void onLowerClick(InventoryClickEvent event) {
    }

    /** Clicks on menu slots that have no registered button. */
    protected void onUnhandledClick(InventoryClickEvent event) {
    }

    /** Called when the viewer closes the menu (or is switched to another). */
    protected void onClose(Player player) {
    }

    public final Inventory inventory() {
        return inventory;
    }

    protected final void close() {
        // Closing inside a click handler confuses the client; defer one tick.
        Bukkit.getScheduler().runTask(MenuManager.plugin(), (Runnable) viewer::closeInventory);
    }
}
