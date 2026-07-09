package com.evensteven.vhlite.storage;

import com.evensteven.vhlite.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Link Wand plumbing: right-click a chest to toggle it in/out of your
 * network. Broken or missing chests are pruned lazily whenever the network
 * is read — no background scanning.
 */
public final class ChestLinkService {

    private final StorageStore store;
    private final FileConfiguration config;

    public ChestLinkService(StorageStore store, FileConfiguration config) {
        this.store = store;
        this.config = config;
    }

    public void toggleLink(Player player, Block block) {
        if (block.getType() != Material.CHEST && block.getType() != Material.BARREL) {
            player.sendMessage(Text.c("§7The wand only links chests and barrels."));
            return;
        }
        List<Location> network = store.network(player.getUniqueId());
        Location loc = block.getLocation();
        boolean removed = network.removeIf(l -> sameBlock(l, loc));
        if (removed) {
            store.saveNetwork(player.getUniqueId(), network);
            player.sendMessage(Text.c("§7Chest unlinked. §8(" + network.size() + " linked)"));
            player.playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.6f);
            return;
        }
        int max = config.getInt("storage.max-linked-chests", 30);
        if (network.size() >= max) {
            player.sendMessage(Text.c("§cNetwork full (" + max + " chests)."));
            return;
        }
        network.add(loc);
        store.saveNetwork(player.getUniqueId(), network);
        player.sendMessage(Text.c("§aChest linked! §7Browse with §e/vh storage §8("
                + network.size() + "/" + max + ")"));
        player.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.6f);
    }

    /** The network with dead entries pruned (and persisted if any died). */
    public List<Location> liveNetwork(Player player) {
        List<Location> network = store.network(player.getUniqueId());
        List<Location> live = new ArrayList<>(network.size());
        for (Location loc : network) {
            if (loc.getWorld() == null) {
                continue;
            }
            Material type = loc.getBlock().getType(); // loads the chunk if needed
            if (type == Material.CHEST || type == Material.BARREL) {
                live.add(loc);
            }
        }
        if (live.size() != network.size()) {
            store.saveNetwork(player.getUniqueId(), live);
        }
        return live;
    }

    private boolean sameBlock(Location a, Location b) {
        return a.getWorld().equals(b.getWorld()) && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }
}
