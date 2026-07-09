package com.evensteven.vhlite.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * The InventoryHolder attached to every plugin menu. Identifying menus by
 * holder (instead of tracking open inventories in a map) survives reopnings
 * and can never confuse a menu with a chest that happens to share a title.
 */
public final class MenuHolder implements InventoryHolder {

    private final Menu menu;
    private Inventory inventory;

    MenuHolder(Menu menu) {
        this.menu = menu;
    }

    public Menu menu() {
        return menu;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
