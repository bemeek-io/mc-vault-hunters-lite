package com.evensteven.vhlite.vault.objective;

import java.util.Random;

/**
 * What a run demands before the exit opens. Rolled per run and only revealed
 * on entry, so no two crystals promise the same trip. The target count for
 * counted objectives scales gently with vault level.
 */
public enum VaultObjective {

    BOSS("§cSlay the Guardian", "Hunt down and kill the vault's guardian."),
    ARTIFACTS("§dGather the Artifacts", "Break the glowing artifact clusters scattered through the rooms."),
    DEFEND("§bHold the Beacon", "Find the beacon and survive three waves defending it."),
    TREASURE("§6Treasure Hunt", "Find and open the marked trapped chests."),
    ESCAPE("§eEscape the Collapse", "The vault is failing. Grab what you can and reach the exit alive.");

    public final String displayName;
    public final String description;

    VaultObjective(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /** How many artifacts/chests/waves this run wants. */
    public int targetCount(int level, Random rng) {
        return switch (this) {
            case ARTIFACTS -> 4 + Math.min(4, level / 6) + rng.nextInt(2);
            case TREASURE -> 3 + Math.min(3, level / 8);
            case DEFEND -> 3;
            case BOSS, ESCAPE -> 1;
        };
    }

    public static VaultObjective roll(Random rng) {
        // Escape runs are a spice, not a staple.
        VaultObjective[] weighted = {BOSS, BOSS, ARTIFACTS, ARTIFACTS, DEFEND, DEFEND, TREASURE, TREASURE, ESCAPE};
        return weighted[rng.nextInt(weighted.length)];
    }
}
