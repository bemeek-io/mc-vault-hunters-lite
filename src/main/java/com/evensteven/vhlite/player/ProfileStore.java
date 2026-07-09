package com.evensteven.vhlite.player;

import com.evensteven.vhlite.knowledge.ResearchNode;
import com.evensteven.vhlite.skills.StatType;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and saves PlayerProfiles, one YAML file per player under
 * playerdata/. Profiles stay cached while the player is online and are
 * saved on quit, after meaningful changes, and by a 5-minute autosave.
 */
public final class ProfileStore {

    private final Plugin plugin;
    private final File folder;
    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();

    public ProfileStore(Plugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "playerdata");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().severe("Could not create " + folder);
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::saveAll, 5L * 60L * 20L, 5L * 60L * 20L);
    }

    public PlayerProfile get(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(), id -> {
            PlayerProfile profile = load(id);
            profile.name = player.getName();
            return profile;
        });
    }

    public PlayerProfile get(UUID id) {
        return cache.computeIfAbsent(id, this::load);
    }

    public void unload(UUID id) {
        PlayerProfile profile = cache.remove(id);
        if (profile != null) {
            save(profile);
        }
    }

    public void saveAll() {
        for (PlayerProfile profile : cache.values()) {
            save(profile);
        }
    }

    private File fileFor(UUID id) {
        return new File(folder, id + ".yml");
    }

    private PlayerProfile load(UUID id) {
        PlayerProfile profile = new PlayerProfile(id);
        File file = fileFor(id);
        if (!file.exists()) {
            return profile;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        profile.name = data.getString("name", "");
        profile.vaultLevel = data.getInt("vault-level", 0);
        profile.vaultXp = data.getInt("vault-xp", 0);
        profile.skillPoints = data.getInt("skill-points", 0);
        profile.knowledgePoints = data.getInt("knowledge-points", 0);
        profile.vaultEssence = data.getLong("vault-essence", 0);
        profile.vaultGoldCopper = data.getLong("vault-gold-copper", 0);
        for (StatType stat : StatType.values()) {
            profile.stats.put(stat, data.getInt("stats." + stat.name().toLowerCase(), 0));
        }
        for (String node : data.getStringList("research")) {
            try {
                profile.research.add(ResearchNode.valueOf(node));
            } catch (IllegalArgumentException ignored) {
            }
        }
        profile.guideGiven = data.getBoolean("guide-given", false);
        profile.hudEnabled = data.getBoolean("hud-enabled", true);
        org.bukkit.configuration.ConfigurationSection quests =
                data.getConfigurationSection("quest-progress");
        if (quests != null) {
            for (String key : quests.getKeys(false)) {
                profile.questProgress.put(key, quests.getInt(key));
            }
        }
        profile.questsCompleted.addAll(data.getStringList("quests-completed"));
        return profile;
    }

    public void save(PlayerProfile profile) {
        YamlConfiguration data = new YamlConfiguration();
        data.set("name", profile.name);
        data.set("vault-level", profile.vaultLevel);
        data.set("vault-xp", profile.vaultXp);
        data.set("skill-points", profile.skillPoints);
        data.set("knowledge-points", profile.knowledgePoints);
        data.set("vault-essence", profile.vaultEssence);
        data.set("vault-gold-copper", profile.vaultGoldCopper);
        for (StatType stat : StatType.values()) {
            data.set("stats." + stat.name().toLowerCase(), profile.stat(stat));
        }
        List<String> research = new ArrayList<>();
        for (ResearchNode node : profile.research) {
            research.add(node.name());
        }
        data.set("research", research);
        data.set("guide-given", profile.guideGiven);
        data.set("hud-enabled", profile.hudEnabled);
        for (Map.Entry<String, Integer> entry : profile.questProgress.entrySet()) {
            data.set("quest-progress." + entry.getKey(), entry.getValue());
        }
        data.set("quests-completed", new ArrayList<>(profile.questsCompleted));
        try {
            data.save(fileFor(profile.id));
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save profile " + profile.id + ": " + e.getMessage());
        }
    }
}
