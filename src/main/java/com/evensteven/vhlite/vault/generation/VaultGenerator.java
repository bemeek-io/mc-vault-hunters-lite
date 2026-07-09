package com.evensteven.vhlite.vault.generation;

import com.evensteven.vhlite.vault.VaultBlueprint;
import com.evensteven.vhlite.vault.generation.RoomGraph.Dir;
import com.evensteven.vhlite.vault.generation.RoomGraph.Role;
import com.evensteven.vhlite.vault.generation.RoomGraph.Room;
import com.evensteven.vhlite.vault.modifier.VaultModifier;
import com.evensteven.vhlite.vault.objective.VaultObjective;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Turns a blueprint into a filled BlockBuffer plus marker positions. Pure
 * computation over pre-resolved BlockData — safe to run off the main thread,
 * which is where the instance manager runs it.
 *
 * Geometry: rooms live on a CELL-sized grid, floors FLOOR_H apart, at
 * BASE_Y. All output coordinates are relative to the instance origin.
 */
public final class VaultGenerator {

    public static final int CELL = 24;
    public static final int FLOOR_H = 20;
    public static final int BASE_Y = 64;

    private final double multiFloorChance;

    public VaultGenerator(double multiFloorChance) {
        this.multiFloorChance = multiFloorChance;
    }

    public GenResult generate(VaultBlueprint bp) {
        Random rng = new Random(bp.seed());
        boolean multiFloor = rng.nextDouble() < multiFloorChance;
        RoomGraph graph = bp.layout().plan(rng, multiFloor);
        if (!graph.fullyConnected()) {
            // Should be impossible; better a loud failure than a sealed room.
            throw new IllegalStateException("Layout not fully connected (seed " + bp.seed()
                    + ", layout " + bp.layout() + ")");
        }
        GenResult out = new GenResult();
        out.graph = graph;

        buildRooms(graph, bp.theme(), rng, out);
        carveCorridors(graph, bp.theme(), rng, out);
        Room start = graph.withRole(Role.START).get(0);
        buildStartFixtures(start, bp.theme(), rng, out);
        placeObjective(bp, graph, rng, out);
        addBonusChests(bp, rng, out);
        pruneStartRoomMarks(start, out);
        supportChests(bp.theme(), rng, out);
        return out;
    }

    // ---------------------------------------------------------------- rooms

    private void buildRooms(RoomGraph graph, Theme theme, Random rng, GenResult out) {
        // Upper floor first: the ground floor's shaft tube must overwrite the
        // upper room's floor to punch the climb-through.
        List<Room> ordered = new ArrayList<>(graph.all());
        ordered.sort((a, b) -> Integer.compare(b.cell.floor(), a.cell.floor()));
        for (Room room : ordered) {
            RoomBuilder builder = builderFor(room, rng);
            int ox = room.cell.x() * CELL;
            int oy = BASE_Y + room.cell.floor() * FLOOR_H;
            int oz = room.cell.z() * CELL;
            int h = builder.height(rng);
            builder.build(out.buffer, theme, rng, ox, oy, oz, h, room, out);
        }
    }

    private RoomBuilder builderFor(Room room, Random rng) {
        return switch (room.role) {
            case START -> new RoomBuilder.BoxRoom();
            case TREASURE -> new RoomBuilder.TreasureRoom();
            case OBJECTIVE -> new RoomBuilder.BossArena();
            case SHAFT -> room.cell.floor() == 0
                    ? new RoomBuilder.ShaftRoom(FLOOR_H)
                    : new RoomBuilder.BoxRoom(); // the upper twin is a plain room
            case NORMAL -> switch (rng.nextInt(8)) {
                case 0 -> new RoomBuilder.PillarHall();
                case 1 -> new RoomBuilder.CavernRoom();
                case 2 -> new RoomBuilder.OctagonRoom();
                case 3 -> new RoomBuilder.CircleRoom();
                case 4 -> new RoomBuilder.CrossRoom();
                case 5 -> new RoomBuilder.TriangleRoom();
                case 6 -> new RoomBuilder.AtriumRoom();
                default -> new RoomBuilder.BoxRoom();
            };
        };
    }

    // ------------------------------------------------------------ corridors

    private void carveCorridors(RoomGraph graph, Theme theme, Random rng, GenResult out) {
        for (Room room : graph.all()) {
            // Each connection carved once, from its west/north side.
            if (room.doors.contains(Dir.EAST)) {
                carve(room, Dir.EAST, theme, rng, out);
            }
            if (room.doors.contains(Dir.SOUTH)) {
                carve(room, Dir.SOUTH, theme, rng, out);
            }
        }
    }

    private void carve(Room room, Dir dir, Theme theme, Random rng, GenResult out) {
        int ox = room.cell.x() * CELL;
        int oy = BASE_Y + room.cell.floor() * FLOOR_H;
        int oz = room.cell.z() * CELL;
        int mid = RoomBuilder.ROOM / 2; // 9, the center line both rooms share
        BlockBuffer buf = out.buffer;

        if (dir == Dir.EAST) {
            int cz = oz + mid;
            for (int x = ox + RoomBuilder.ROOM; x < ox + CELL; x++) {
                for (int z = cz - 2; z <= cz + 2; z++) {
                    buf.set(x, oy, z, theme.pick(theme.floor, rng));
                    buf.set(x, oy + 4, z, theme.pick(theme.ceiling, rng));
                }
                for (int y = 1; y <= 3; y++) {
                    buf.set(x, oy + y, cz - 2, theme.pick(theme.wall, rng));
                    buf.set(x, oy + y, cz + 2, theme.pick(theme.wall, rng));
                    for (int z = cz - 1; z <= cz + 1; z++) {
                        buf.set(x, oy + y, z, GenBlocks.AIR);
                    }
                }
            }
            punchDoor(buf, ox + RoomBuilder.ROOM - 1, oy, cz, true);
            punchDoor(buf, ox + CELL, oy, cz, true);
        } else { // SOUTH
            int cx = ox + mid;
            for (int z = oz + RoomBuilder.ROOM; z < oz + CELL; z++) {
                for (int x = cx - 2; x <= cx + 2; x++) {
                    buf.set(x, oy, z, theme.pick(theme.floor, rng));
                    buf.set(x, oy + 4, z, theme.pick(theme.ceiling, rng));
                }
                for (int y = 1; y <= 3; y++) {
                    buf.set(cx - 2, oy + y, z, theme.pick(theme.wall, rng));
                    buf.set(cx + 2, oy + y, z, theme.pick(theme.wall, rng));
                    for (int x = cx - 1; x <= cx + 1; x++) {
                        buf.set(x, oy + y, z, GenBlocks.AIR);
                    }
                }
            }
            punchDoor(buf, cx, oy, oz + RoomBuilder.ROOM - 1, false);
            punchDoor(buf, cx, oy, oz + CELL, false);
        }
    }

    /** 3-wide, 3-tall air opening in a room wall. */
    private void punchDoor(BlockBuffer buf, int x, int oy, int z, boolean eastWest) {
        for (int y = 1; y <= 3; y++) {
            for (int off = -1; off <= 1; off++) {
                if (eastWest) {
                    buf.set(x, oy + y, z + off, GenBlocks.AIR);
                } else {
                    buf.set(x + off, oy + y, z, GenBlocks.AIR);
                }
            }
        }
    }

    // -------------------------------------------------------- start fixtures

    private void buildStartFixtures(Room start, Theme theme, Random rng, GenResult out) {
        int ox = start.cell.x() * CELL;
        int oy = BASE_Y + start.cell.floor() * FLOOR_H;
        int oz = start.cell.z() * CELL;
        int mid = RoomBuilder.ROOM / 2;

        // Clean landing zone in the room center.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                out.buffer.set(ox + mid + dx, oy, oz + mid + dz, theme.pick(theme.accent, rng));
                for (int y = 1; y <= 3; y++) {
                    out.buffer.set(ox + mid + dx, oy + y, oz + mid + dz, GenBlocks.AIR);
                }
            }
        }
        out.startPad = new Vec3(ox + mid, oy + 1, oz + mid);

        // Exit pad in the northwest corner: crying obsidian, swapped to
        // amethyst when the objective opens it. Stepping on it extracts.
        int ex = ox + 4;
        int ez = oz + 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                out.buffer.set(ex + dx, oy, ez + dz, GenBlocks.CRYING_OBSIDIAN);
                for (int y = 1; y <= 3; y++) {
                    out.buffer.set(ex + dx, oy + y, ez + dz, GenBlocks.AIR);
                }
            }
        }
        // Corner posts so the pad reads as a structure.
        for (int[] corner : new int[][] {{-2, -2}, {-2, 2}, {2, -2}, {2, 2}}) {
            out.buffer.set(ex + corner[0], oy + 1, ez + corner[1], GenBlocks.OBSIDIAN);
            out.buffer.set(ex + corner[0], oy + 2, ez + corner[1], theme.light);
        }
        out.exitCenter = new Vec3(ex, oy, ez);
    }

    // ------------------------------------------------------------- objective

    private void placeObjective(VaultBlueprint bp, RoomGraph graph, Random rng, GenResult out) {
        switch (bp.objective()) {
            case ARTIFACTS -> {
                int want = bp.objective().targetCount(bp.level(), rng);
                List<Vec3> spots = new ArrayList<>(out.pedestalCandidates);
                Collections.shuffle(spots, rng);
                for (Vec3 spot : spots) {
                    if (out.artifacts.size() >= want) {
                        break;
                    }
                    // Pedestal: amethyst base with a cluster to break on top.
                    out.buffer.set(spot.x(), spot.y(), spot.z(), GenBlocks.AMETHYST_BLOCK);
                    out.buffer.set(spot.x(), spot.y() + 1, spot.z(), GenBlocks.AMETHYST_CLUSTER);
                    out.artifacts.add(new Vec3(spot.x(), spot.y() + 1, spot.z()));
                }
            }
            case TREASURE -> {
                int want = bp.objective().targetCount(bp.level(), rng);
                // Convert existing chest spots to marked trapped chests first...
                List<GenResult.ChestSpot> converted = new ArrayList<>();
                List<GenResult.ChestSpot> pool = new ArrayList<>(out.chests);
                Collections.shuffle(pool, rng);
                for (GenResult.ChestSpot spot : pool) {
                    if (converted.size() >= want) {
                        break;
                    }
                    out.buffer.set(spot.pos().x(), spot.pos().y(), spot.pos().z(), GenBlocks.TRAPPED_CHEST);
                    converted.add(new GenResult.ChestSpot(spot.pos(), true));
                }
                out.chests.removeAll(pool.subList(0, Math.min(want, pool.size())));
                // ...then mint extras on pedestal spots if the vault rolled few chests.
                List<Vec3> extras = new ArrayList<>(out.pedestalCandidates);
                Collections.shuffle(extras, rng);
                for (Vec3 spot : extras) {
                    if (converted.size() >= want) {
                        break;
                    }
                    out.buffer.set(spot.x(), spot.y(), spot.z(), GenBlocks.TRAPPED_CHEST);
                    converted.add(new GenResult.ChestSpot(spot, true));
                }
                out.chests.addAll(converted);
            }
            case DEFEND -> {
                Vec3 point = out.defendPoint;
                out.buffer.set(point.x(), point.y() - 1, point.z(), GenBlocks.IRON_BLOCK);
                out.buffer.set(point.x(), point.y(), point.z(), GenBlocks.BEACON);
            }
            case BOSS, ESCAPE -> {
                // Boss spawns lazily when a player nears bossSpawn; escape
                // needs no fixtures — the exit is open from the first second.
            }
        }
    }

    /** GILDED (and escape runs) sprinkle extra loot chests around. */
    private void addBonusChests(VaultBlueprint bp, Random rng, GenResult out) {
        double chestMult = bp.modifierProduct(m -> m.chestMult);
        if (bp.objective() == VaultObjective.ESCAPE) {
            chestMult *= 1.5; // sweeten the sprint
        }
        int extra = (int) Math.round(out.chests.size() * (chestMult - 1.0));
        List<Vec3> spots = new ArrayList<>(out.pedestalCandidates);
        Collections.shuffle(spots, rng);
        for (Vec3 spot : spots) {
            if (extra <= 0) {
                break;
            }
            if (out.buffer.get(spot.x(), spot.y(), spot.z()) == GenBlocks.AIR
                    || out.buffer.get(spot.x(), spot.y(), spot.z()) == null) {
                out.buffer.set(spot.x(), spot.y(), spot.z(), GenBlocks.CHEST);
                out.chests.add(new GenResult.ChestSpot(spot, false));
                extra--;
            }
        }
    }

    /** No chest may float: anything airy below a chest becomes floor. */
    private void supportChests(Theme theme, Random rng, GenResult out) {
        for (GenResult.ChestSpot spot : out.chests) {
            var below = out.buffer.get(spot.pos().x(), spot.pos().y() - 1, spot.pos().z());
            if (below == null || below == GenBlocks.AIR || below == GenBlocks.COBWEB) {
                out.buffer.set(spot.pos().x(), spot.pos().y() - 1, spot.pos().z(),
                        theme.pick(theme.floor, rng));
            }
        }
    }

    /** No mobs ambushing spawn: drop marks inside the start room's box. */
    private void pruneStartRoomMarks(Room start, GenResult out) {
        int ox = start.cell.x() * CELL;
        int oz = start.cell.z() * CELL;
        int oy = BASE_Y + start.cell.floor() * FLOOR_H;
        out.mobSpawns.removeIf(v -> v.x() >= ox && v.x() < ox + RoomBuilder.ROOM
                && v.z() >= oz && v.z() < oz + RoomBuilder.ROOM
                && v.y() >= oy && v.y() < oy + FLOOR_H);
        // Chests whose block got overwritten by fixtures are filtered at
        // apply time (the applier checks the block is actually a chest).
    }
}
