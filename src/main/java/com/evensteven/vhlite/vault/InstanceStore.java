package com.evensteven.vhlite.vault;

import com.evensteven.vhlite.util.Text;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * instances.yml: the slot allocator cursor, slots awaiting region purge, the
 * runs that were live when the server last stopped, and per-player recovery
 * entries. The crash-recovery contract: whatever you carried INTO a vault
 * comes back, plus a refund crystal — in-vault loot is lost, never the kit.
 */
public final class InstanceStore {

    public record Recovery(List<ItemStack> items, int refundLevel) {
    }

    private final Plugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public InstanceStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "instances.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    // ---------------------------------------------------------------- slots

    public int nextSlot() {
        int slot = data.getInt("next-slot", 0);
        data.set("next-slot", slot + 1);
        save();
        return slot;
    }

    public List<Integer> retiredSlots() {
        return data.getIntegerList("retired-slots");
    }

    public void retireSlot(int slot) {
        List<Integer> retired = data.getIntegerList("retired-slots");
        retired.add(slot);
        data.set("retired-slots", retired);
        save();
    }

    public void clearRetiredSlots() {
        data.set("retired-slots", new ArrayList<Integer>());
        save();
    }

    // --------------------------------------------------------- active runs

    public void recordActive(VaultInstance instance) {
        String base = "active." + instance.id();
        data.set(base + ".slot", instance.blueprint().slot());
        data.set(base + ".level", instance.blueprint().level());
        for (Player player : instance.players()) {
            String pb = base + ".players." + player.getUniqueId();
            List<String> encoded = new ArrayList<>();
            for (ItemStack item : player.getInventory().getContents()) {
                encoded.add(item == null || item.getType().isAir()
                        ? "" : Base64.getEncoder().encodeToString(item.serializeAsBytes()));
            }
            data.set(pb + ".items", encoded);
            data.set(pb + ".return", Text.encodeLocation(instance.returnLocation(player.getUniqueId())));
        }
        save();
    }

    public void clearActive(UUID instanceId) {
        data.set("active." + instanceId, null);
        save();
    }

    /**
     * Server died mid-run: retire every active slot and convert each
     * participant's entry snapshot into a pending recovery. Call on enable,
     * before the world purge.
     */
    public void recoverCrashedRuns() {
        ConfigurationSection active = data.getConfigurationSection("active");
        if (active == null) {
            return;
        }
        for (String id : active.getKeys(false)) {
            ConfigurationSection run = active.getConfigurationSection(id);
            if (run == null) {
                continue;
            }
            retireSlotQuiet(run.getInt("slot"));
            int level = run.getInt("level", 1);
            ConfigurationSection players = run.getConfigurationSection("players");
            if (players != null) {
                for (String uuid : players.getKeys(false)) {
                    String pb = "recovery." + uuid;
                    data.set(pb + ".items", players.getStringList(uuid + ".items"));
                    data.set(pb + ".refund-level", level);
                }
            }
            plugin.getLogger().warning("Recovered crashed vault run " + id
                    + " (slot " + run.getInt("slot") + ").");
        }
        data.set("active", null);
        save();
    }

    private void retireSlotQuiet(int slot) {
        List<Integer> retired = data.getIntegerList("retired-slots");
        retired.add(slot);
        data.set("retired-slots", retired);
    }

    // ------------------------------------------------------------- recovery

    public boolean hasRecovery(UUID player) {
        return data.contains("recovery." + player);
    }

    public Recovery takeRecovery(UUID player) {
        String base = "recovery." + player;
        if (!data.contains(base)) {
            return null;
        }
        List<ItemStack> items = new ArrayList<>();
        for (String encoded : data.getStringList(base + ".items")) {
            if (encoded.isEmpty()) {
                items.add(null); // preserve slot positions
                continue;
            }
            try {
                items.add(ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded)));
            } catch (Exception ex) {
                plugin.getLogger().warning("Dropping unreadable recovery item for " + player);
                items.add(null);
            }
        }
        int level = data.getInt(base + ".refund-level", 1);
        data.set(base, null);
        save();
        return new Recovery(items, level);
    }

    // ------------------------------------------------------------- plumbing

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save instances.yml: " + e.getMessage());
        }
    }
}
