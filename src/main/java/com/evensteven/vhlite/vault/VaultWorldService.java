package com.evensteven.vhlite.vault;

import com.evensteven.vhlite.vault.generation.VoidChunkGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.List;

/**
 * Owns the persistent void world all vaults are pasted into. Also handles
 * the free-cleanup trick: retired instance slots are spaced on whole region
 * files, so their .mca files are deleted here on startup BEFORE the world
 * loads — no block-clearing loops, ever.
 */
public final class VaultWorldService {

    private final Plugin plugin;
    private final String worldName;
    private final int slotSpacing;
    private World world;

    public VaultWorldService(Plugin plugin, String worldName, int slotSpacing) {
        this.plugin = plugin;
        this.worldName = worldName;
        this.slotSpacing = slotSpacing;
    }

    /** Must run before {@link #createWorld()}. */
    public void purgeRetiredSlots(List<Integer> retiredSlots) {
        if (retiredSlots.isEmpty()) {
            return;
        }
        File worldFolder = resolveWorldFolder();
        if (worldFolder == null) {
            return; // first boot, nothing to purge
        }
        int regionsPerSlot = Math.max(1, slotSpacing / 512);
        int deleted = 0;
        for (int slot : retiredSlots) {
            int baseRX = (InstanceAllocator.slotX(slot) * slotSpacing) >> 9;
            int baseRZ = (InstanceAllocator.slotZ(slot) * slotSpacing) >> 9;
            for (int rx = baseRX; rx < baseRX + regionsPerSlot; rx++) {
                for (int rz = baseRZ; rz < baseRZ + regionsPerSlot; rz++) {
                    for (String dir : new String[] {"region", "entities", "poi"}) {
                        File mca = new File(worldFolder, dir + "/r." + rx + "." + rz + ".mca");
                        if (mca.exists() && mca.delete()) {
                            deleted++;
                        }
                    }
                }
            }
        }
        plugin.getLogger().info("Purged " + retiredSlots.size() + " retired vault slot(s) ("
                + deleted + " region files).");
    }

    /**
     * Classic Bukkit puts custom worlds at &lt;container&gt;/&lt;name&gt;;
     * Paper 26.x nests them under the primary world as
     * &lt;container&gt;/&lt;level-name&gt;/dimensions/minecraft/&lt;name&gt;.
     * Runs before createWorld(), so only the primary world is loaded.
     */
    private File resolveWorldFolder() {
        File classic = new File(Bukkit.getWorldContainer(), worldName);
        if (classic.isDirectory()) {
            return classic;
        }
        String levelName = Bukkit.getWorlds().isEmpty()
                ? "world" : Bukkit.getWorlds().get(0).getName();
        File nested = new File(Bukkit.getWorldContainer(),
                levelName + "/dimensions/minecraft/" + worldName);
        return nested.isDirectory() ? nested : null;
    }

    public void createWorld() {
        world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = new WorldCreator(worldName)
                    .generator(new VoidChunkGenerator())
                    .biomeProvider(new VoidChunkGenerator.VoidBiomes())
                    .createWorld();
        }
        if (world == null) {
            throw new IllegalStateException("Could not create vault world " + worldName);
        }
        world.setDifficulty(Difficulty.HARD);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SPAWN_MONSTERS, false);
        world.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        world.setGameRule(GameRules.SPAWN_PATROLS, false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
        world.setGameRule(GameRules.MOB_DROPS, true);
        world.setGameRule(GameRules.KEEP_INVENTORY, false);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        world.setTime(18000L); // permanent midnight: vaults read as dungeons
    }

    public World world() {
        return world;
    }

    public boolean isVaultWorld(World other) {
        return world != null && world.equals(other);
    }
}
