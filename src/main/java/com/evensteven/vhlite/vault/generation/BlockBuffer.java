package com.evensteven.vhlite.vault.generation;

import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.Map;

/**
 * The staging area a vault is computed into before it touches the world.
 * Keys pack relative (x, y, z) into a long; values are pre-resolved
 * BlockData, so filling the buffer is pure computation and safe off the
 * main thread. Later writes win, which lets decorators carve into rooms.
 */
public final class BlockBuffer {

    private static final int BITS = 21;
    private static final int BIAS = 1 << (BITS - 1);
    private static final long MASK = (1L << BITS) - 1;

    private final Map<Long, BlockData> blocks = new HashMap<>(1 << 17);
    private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

    public static long pack(int x, int y, int z) {
        return ((long) (x + BIAS) << (2 * BITS)) | ((long) (y + BIAS) << BITS) | (z + BIAS);
    }

    public static int unpackX(long key) {
        return (int) ((key >> (2 * BITS)) & MASK) - BIAS;
    }

    public static int unpackY(long key) {
        return (int) ((key >> BITS) & MASK) - BIAS;
    }

    public static int unpackZ(long key) {
        return (int) (key & MASK) - BIAS;
    }

    public void set(int x, int y, int z, BlockData data) {
        blocks.put(pack(x, y, z), data);
        if (x < minX) minX = x;
        if (y < minY) minY = y;
        if (z < minZ) minZ = z;
        if (x > maxX) maxX = x;
        if (y > maxY) maxY = y;
        if (z > maxZ) maxZ = z;
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockData data) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, data);
                }
            }
        }
    }

    public BlockData get(int x, int y, int z) {
        return blocks.get(pack(x, y, z));
    }

    public Map<Long, BlockData> entries() {
        return blocks;
    }

    public int size() {
        return blocks.size();
    }

    public int minX() { return minX; }
    public int minZ() { return minZ; }
    public int maxX() { return maxX; }
    public int maxZ() { return maxZ; }
}
