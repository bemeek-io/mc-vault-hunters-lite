package com.evensteven.vhlite.altar;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Where the crafted Vault Altars stand (altars.yml). Only registered
 * locations act as altars — a plain lodestone is just a lodestone, so the
 * altar is genuinely something you build.
 */
public final class AltarStore {

    private final Plugin plugin;
    private final File file;
    private final Set<String> altars = new HashSet<>();

    public AltarStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "altars.yml");
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        altars.addAll(data.getStringList("altars"));
    }

    private String key(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + ","
                + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public boolean isAltar(Location loc) {
        return altars.contains(key(loc));
    }

    public void add(Location loc) {
        if (altars.add(key(loc))) {
            save();
        }
    }

    public void remove(Location loc) {
        if (altars.remove(key(loc))) {
            save();
        }
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("altars", new ArrayList<>(altars));
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save altars.yml: " + e.getMessage());
        }
    }
}
