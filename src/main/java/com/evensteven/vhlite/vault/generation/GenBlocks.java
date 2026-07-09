package com.evensteven.vhlite.vault.generation;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Ladder;

/**
 * BlockData shared by every theme, resolved once on the main thread at
 * startup. Generation runs async and must never call Bukkit.createBlockData
 * itself; it only reads these (and the theme palettes) as immutable values.
 */
public final class GenBlocks {

    public static BlockData AIR;
    public static BlockData CHEST;
    public static BlockData TRAPPED_CHEST;
    public static BlockData OBSIDIAN;
    public static BlockData CRYING_OBSIDIAN;
    public static BlockData AMETHYST_CLUSTER;
    public static BlockData AMETHYST_BLOCK;
    public static BlockData BEACON;
    public static BlockData IRON_BLOCK;
    public static BlockData LADDER_SOUTH;
    public static BlockData BEDROCK;

    private GenBlocks() {
    }

    public static void init() {
        AIR = Bukkit.createBlockData(Material.AIR);
        CHEST = Bukkit.createBlockData(Material.CHEST);
        TRAPPED_CHEST = Bukkit.createBlockData(Material.TRAPPED_CHEST);
        OBSIDIAN = Bukkit.createBlockData(Material.OBSIDIAN);
        CRYING_OBSIDIAN = Bukkit.createBlockData(Material.CRYING_OBSIDIAN);
        AMETHYST_CLUSTER = Bukkit.createBlockData(Material.AMETHYST_CLUSTER);
        AMETHYST_BLOCK = Bukkit.createBlockData(Material.AMETHYST_BLOCK);
        BEACON = Bukkit.createBlockData(Material.BEACON);
        IRON_BLOCK = Bukkit.createBlockData(Material.IRON_BLOCK);
        BEDROCK = Bukkit.createBlockData(Material.BEDROCK);
        Ladder ladder = (Ladder) Bukkit.createBlockData(Material.LADDER);
        ladder.setFacing(BlockFace.SOUTH); // mounted on the block to its north
        LADDER_SOUTH = ladder;
    }
}
