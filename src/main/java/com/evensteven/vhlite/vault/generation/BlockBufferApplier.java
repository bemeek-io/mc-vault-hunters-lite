package com.evensteven.vhlite.vault.generation;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Writes a computed BlockBuffer into the world without stalling it: chunks
 * are pre-loaded async and pinned with plugin tickets, then blocks are
 * placed in paced batches (physics off). A typical vault lands in one or
 * two seconds — hidden behind the "Entering the Vault" countdown.
 */
public final class BlockBufferApplier {

    private final Plugin plugin;
    private final int blocksPerTick;

    public BlockBufferApplier(Plugin plugin, int blocksPerTick) {
        this.plugin = plugin;
        this.blocksPerTick = Math.max(1000, blocksPerTick);
    }

    /**
     * @param originX/originZ world position the buffer's (0,?,0) maps to.
     * @param onDone          runs on the main thread once every block is down.
     * @return the chunks pinned for this instance; unpin them on close.
     */
    public CompletableFuture<List<Chunk>> apply(World world, BlockBuffer buffer,
            int originX, int originZ, Runnable onDone) {
        List<CompletableFuture<Chunk>> loads = new ArrayList<>();
        int minCX = (originX + buffer.minX()) >> 4;
        int maxCX = (originX + buffer.maxX()) >> 4;
        int minCZ = (originZ + buffer.minZ()) >> 4;
        int maxCZ = (originZ + buffer.maxZ()) >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                loads.add(world.getChunkAtAsync(cx, cz));
            }
        }
        CompletableFuture<List<Chunk>> pinned = new CompletableFuture<>();
        CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).thenRun(() ->
                // getChunkAtAsync completes on the main thread on Paper, but
                // hop explicitly so a behavior change can't bite us.
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    List<Chunk> chunks = new ArrayList<>(loads.size());
                    for (CompletableFuture<Chunk> load : loads) {
                        Chunk chunk = load.join();
                        chunk.addPluginChunkTicket(plugin);
                        chunks.add(chunk);
                    }
                    place(world, buffer, originX, originZ, () -> {
                        onDone.run();
                        pinned.complete(chunks);
                    });
                }));
        return pinned;
    }

    private void place(World world, BlockBuffer buffer, int originX, int originZ, Runnable onDone) {
        Iterator<Map.Entry<Long, BlockData>> it = buffer.entries().entrySet().iterator();
        new BukkitRunnable() {
            @Override
            public void run() {
                int placed = 0;
                while (it.hasNext() && placed < blocksPerTick) {
                    Map.Entry<Long, BlockData> entry = it.next();
                    long key = entry.getKey();
                    world.getBlockAt(originX + BlockBuffer.unpackX(key),
                                    BlockBuffer.unpackY(key),
                                    originZ + BlockBuffer.unpackZ(key))
                            .setBlockData(entry.getValue(), false);
                    placed++;
                }
                if (!it.hasNext()) {
                    cancel();
                    onDone.run();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
