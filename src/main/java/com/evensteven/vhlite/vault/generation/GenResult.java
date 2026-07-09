package com.evensteven.vhlite.vault.generation;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the generator hands back: the block buffer plus every position
 * the run lifecycle cares about, all relative to the instance origin.
 */
public final class GenResult {

    public record ChestSpot(Vec3 pos, boolean treasure) {
    }

    public final BlockBuffer buffer = new BlockBuffer();
    /** The abstract room layout — kept for the minimap and cell tracking. */
    public RoomGraph graph;
    /** Where players appear (feet position). */
    public Vec3 startPad;
    /** Center of the 3x3 exit pad; stepping onto it (when open) extracts. */
    public Vec3 exitCenter;
    public final List<Vec3> mobSpawns = new ArrayList<>();
    public final List<ChestSpot> chests = new ArrayList<>();
    /** Candidate floor spots where artifact pedestals may be raised. */
    public final List<Vec3> pedestalCandidates = new ArrayList<>();
    /** Filled only for the objective the blueprint rolled. */
    public Vec3 bossSpawn;
    public Vec3 defendPoint;
    public final List<Vec3> artifacts = new ArrayList<>();
}
