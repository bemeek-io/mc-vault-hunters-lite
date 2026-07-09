package com.evensteven.vhlite.vault.generation;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/**
 * The vault world's generator: nothing but void. Vault structures are pasted
 * in by the plugin; the generator's only job is to produce empty chunks fast
 * and keep every vanilla feature (caves, ores, structures) out.
 */
public final class VoidChunkGenerator extends ChunkGenerator {

    public static final class VoidBiomes extends BiomeProvider {
        @Override
        public org.bukkit.block.Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
            return org.bukkit.block.Biome.THE_VOID;
        }

        @Override
        public List<org.bukkit.block.Biome> getBiomes(WorldInfo worldInfo) {
            return List.of(org.bukkit.block.Biome.THE_VOID);
        }
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new VoidBiomes();
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, 65, 0.5);
    }
}
