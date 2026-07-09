package com.evensteven.vhlite.spirit;

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
 * Spirits: what a post-grace death (or collapse) in a vault costs you. The
 * dead player's inventory and XP are frozen here until they pay Vault
 * Essence at an altar to call them back. Persisted in spirits.yml.
 */
public final class SpiritStore {

    public record Spirit(String id, int level, int xpLevels, List<ItemStack> items) {
    }

    private final Plugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public SpiritStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "spirits.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    /** Freezes the player's carried items + XP into a new spirit. */
    public void capture(Player player, int vaultLevel) {
        List<String> encoded = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            encoded.add(item == null || item.getType().isAir()
                    ? "" : Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        }
        String spiritId = UUID.randomUUID().toString();
        String base = "spirits." + player.getUniqueId() + "." + spiritId;
        data.set(base + ".level", vaultLevel);
        data.set(base + ".xp-levels", player.getLevel());
        data.set(base + ".items", encoded);
        save();

        player.getInventory().clear();
        player.setLevel(0);
        player.setExp(0f);
        player.setTotalExperience(0);
    }

    public List<Spirit> spiritsOf(UUID playerId) {
        List<Spirit> out = new ArrayList<>();
        ConfigurationSection section = data.getConfigurationSection("spirits." + playerId);
        if (section == null) {
            return out;
        }
        for (String spiritId : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(spiritId);
            if (s == null) {
                continue;
            }
            List<ItemStack> items = new ArrayList<>();
            for (String encoded : s.getStringList("items")) {
                if (encoded.isEmpty()) {
                    items.add(null);
                    continue;
                }
                try {
                    items.add(ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded)));
                } catch (Exception ex) {
                    plugin.getLogger().warning("Dropping unreadable spirit item for " + playerId);
                }
            }
            out.add(new Spirit(spiritId, s.getInt("level", 1), s.getInt("xp-levels", 0), items));
        }
        return out;
    }

    /** Removes the spirit and hands everything back. */
    public void revive(Player player, Spirit spirit) {
        data.set("spirits." + player.getUniqueId() + "." + spirit.id(), null);
        save();
        for (ItemStack item : spirit.items()) {
            if (item == null) {
                continue;
            }
            for (ItemStack rest : player.getInventory().addItem(item).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
        player.giveExpLevels(spirit.xpLevels());
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save spirits.yml: " + e.getMessage());
        }
    }
}
