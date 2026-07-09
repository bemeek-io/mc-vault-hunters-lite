package com.evensteven.vhlite.storage;

import com.evensteven.vhlite.util.Text;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * storage.yml: backpack contents (keyed by the backpack item's id, so the
 * stash follows the item, not the player) and each player's linked-chest
 * network locations.
 */
public final class StorageStore {

    private final Plugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public StorageStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "storage.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    // ------------------------------------------------------------ backpacks

    public List<ItemStack> backpackItems(UUID backpackId, int size) {
        List<ItemStack> items = new ArrayList<>();
        for (String encoded : data.getStringList("backpacks." + backpackId + ".items")) {
            if (encoded.isEmpty()) {
                items.add(null);
                continue;
            }
            try {
                items.add(ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded)));
            } catch (Exception ex) {
                plugin.getLogger().warning("Dropping unreadable backpack item in " + backpackId);
                items.add(null);
            }
        }
        while (items.size() < size) {
            items.add(null);
        }
        return items;
    }

    public void saveBackpack(UUID backpackId, ItemStack[] contents) {
        List<String> encoded = new ArrayList<>(contents.length);
        for (ItemStack item : contents) {
            encoded.add(item == null || item.getType().isAir()
                    ? "" : Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        }
        data.set("backpacks." + backpackId + ".items", encoded);
        save();
    }

    // ------------------------------------------------------------- networks

    public List<Location> network(UUID playerId) {
        List<Location> out = new ArrayList<>();
        for (String encoded : data.getStringList("networks." + playerId)) {
            // Chest locations have no yaw/pitch; pad the decoder's format.
            Location loc = Text.decodeLocation(encoded + ",0,0");
            if (loc != null) {
                out.add(loc);
            }
        }
        return out;
    }

    public void saveNetwork(UUID playerId, List<Location> locations) {
        List<String> encoded = new ArrayList<>(locations.size());
        for (Location loc : locations) {
            encoded.add(loc.getWorld().getName() + "," + loc.getBlockX() + ","
                    + loc.getBlockY() + "," + loc.getBlockZ());
        }
        data.set("networks." + playerId, encoded);
        save();
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save storage.yml: " + e.getMessage());
        }
    }
}
