package com.evensteven.vhlite.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.Plugin;

/**
 * The single listener that routes inventory events to whichever {@link Menu}
 * owns the top inventory. Menus are recognized by their {@link MenuHolder}.
 */
public final class MenuManager implements Listener {

    private static Plugin pluginInstance;

    public MenuManager(Plugin plugin) {
        pluginInstance = plugin;
    }

    static Plugin plugin() {
        return pluginInstance;
    }

    private static Menu menuOf(org.bukkit.inventory.Inventory top) {
        return top != null && top.getHolder() instanceof MenuHolder holder ? holder.menu() : null;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Menu menu = menuOf(event.getView().getTopInventory());
        if (menu != null) {
            menu.handleClick(event);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Menu menu = menuOf(event.getView().getTopInventory());
        if (menu == null) {
            return;
        }
        if (!menu.interactive()) {
            event.setCancelled(true);
            return;
        }
        // Interactive menus still refuse drags that touch the top inventory;
        // single clicks are enough and are far easier to validate.
        int topSize = event.getView().getTopInventory().getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Menu menu = menuOf(event.getInventory());
        if (menu != null && event.getPlayer() instanceof Player p) {
            menu.onClose(p);
        }
    }
}
