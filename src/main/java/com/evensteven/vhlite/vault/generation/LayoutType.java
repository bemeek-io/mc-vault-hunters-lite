package com.evensteven.vhlite.vault.generation;

import com.evensteven.vhlite.vault.generation.RoomGraph.Cell;
import com.evensteven.vhlite.vault.generation.RoomGraph.Dir;
import com.evensteven.vhlite.vault.generation.RoomGraph.Role;
import com.evensteven.vhlite.vault.generation.RoomGraph.Room;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The three macro-shapes a vault can take. Each produces a RoomGraph;
 * MAZE and HUB can grow a second floor reached by ladder shafts. Together
 * with theme, objective, and modifiers this is one of the four axes that
 * keep runs from feeling samey.
 */
public enum LayoutType {

    /** A 6-8 room gauntlet: fight forward, objective at the end. */
    LINEAR {
        @Override
        public RoomGraph plan(Random rng, boolean multiFloor) {
            RoomGraph graph = new RoomGraph();
            int length = 6 + rng.nextInt(3);
            for (int i = 0; i < length; i++) {
                Role role = i == 0 ? Role.START : i == length - 1 ? Role.OBJECTIVE : Role.NORMAL;
                graph.add(new Cell(i, 0, 0), role);
            }
            for (int i = 0; i < length - 1; i++) {
                graph.connect(new Cell(i, 0, 0), Dir.EAST);
            }
            // One treasure room in the middle stretch.
            graph.get(new Cell(1 + rng.nextInt(length - 2), 0, 0)).role = Role.TREASURE;
            return graph;
        }
    },

    /** A 5x5 maze: spanning tree plus a few loops, objective at max distance. */
    MAZE {
        @Override
        public RoomGraph plan(Random rng, boolean multiFloor) {
            RoomGraph graph = new RoomGraph();
            int size = 5;
            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    graph.add(new Cell(x, z, 0), Role.NORMAL);
                }
            }
            // Randomized DFS spanning tree.
            List<Cell> stack = new ArrayList<>();
            List<Cell> visited = new ArrayList<>();
            Cell startCell = new Cell(rng.nextInt(size), rng.nextInt(size), 0);
            stack.add(startCell);
            visited.add(startCell);
            while (!stack.isEmpty()) {
                Cell cur = stack.get(stack.size() - 1);
                List<Dir> options = new ArrayList<>();
                for (Dir dir : Dir.values()) {
                    Cell next = cur.step(dir);
                    if (graph.get(next) != null && !visited.contains(next)) {
                        options.add(dir);
                    }
                }
                if (options.isEmpty()) {
                    stack.remove(stack.size() - 1);
                    continue;
                }
                Dir dir = options.get(rng.nextInt(options.size()));
                graph.connect(cur, dir);
                Cell next = cur.step(dir);
                visited.add(next);
                stack.add(next);
            }
            // ~20% extra doorways make loops so backtracking isn't forced.
            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    Cell cell = new Cell(x, z, 0);
                    for (Dir dir : new Dir[] {Dir.EAST, Dir.SOUTH}) {
                        Cell next = cell.step(dir);
                        if (graph.get(next) != null && !graph.get(cell).doors.contains(dir)
                                && rng.nextDouble() < 0.20) {
                            graph.connect(cell, dir);
                        }
                    }
                }
            }
            graph.get(startCell).role = Role.START;
            placeObjectiveFarthest(graph, startCell);
            markRandomTreasure(graph, rng, 2);
            if (multiFloor) {
                addSecondFloor(graph, rng);
            }
            return graph;
        }
    },

    /** A 3x3 block around an oversized central arena holding the objective. */
    HUB {
        @Override
        public RoomGraph plan(Random rng, boolean multiFloor) {
            RoomGraph graph = new RoomGraph();
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    graph.add(new Cell(x, z, 0), Role.NORMAL);
                }
            }
            Cell center = new Cell(1, 1, 0);
            graph.get(center).role = Role.OBJECTIVE;
            // The arena opens to all four neighbors.
            for (Dir dir : Dir.values()) {
                graph.connect(center, dir);
            }
            // Ring connections, randomized but never fully closed off.
            Cell[] ring = {new Cell(0, 0, 0), new Cell(1, 0, 0), new Cell(2, 0, 0), new Cell(2, 1, 0),
                    new Cell(2, 2, 0), new Cell(1, 2, 0), new Cell(0, 2, 0), new Cell(0, 1, 0)};
            Dir[] ringDirs = {Dir.EAST, Dir.EAST, Dir.SOUTH, Dir.SOUTH, Dir.WEST, Dir.WEST, Dir.NORTH, Dir.NORTH};
            for (int i = 0; i < ring.length; i++) {
                if (rng.nextDouble() < 0.6) {
                    graph.connect(ring[i], ringDirs[i]);
                }
            }
            // Corners only touch the map through ring edges; make sure none
            // was left doorless by the random rolls.
            for (int i = 0; i < ring.length; i += 2) {
                if (graph.get(ring[i]).doors.isEmpty()) {
                    graph.connect(ring[i], ringDirs[i]);
                }
            }
            Cell startCell = ring[rng.nextInt(ring.length) & ~1]; // a corner
            graph.get(startCell).role = Role.START;
            markRandomTreasure(graph, rng, 1 + rng.nextInt(2));
            if (multiFloor) {
                addSecondFloor(graph, rng);
            }
            return graph;
        }
    };

    public abstract RoomGraph plan(Random rng, boolean multiFloor);

    public static LayoutType roll(Random rng) {
        return values()[rng.nextInt(values().length)];
    }

    // ---------------------------------------------------------------- shared

    /** Moves the objective to the room farthest (by doors) from start. */
    private static void placeObjectiveFarthest(RoomGraph graph, Cell startCell) {
        Map<Cell, Integer> dist = graph.distancesFrom(startCell);
        Cell farthest = startCell;
        for (Map.Entry<Cell, Integer> entry : dist.entrySet()) {
            if (entry.getValue() > dist.getOrDefault(farthest, 0)) {
                farthest = entry.getKey();
            }
        }
        graph.get(farthest).role = Role.OBJECTIVE;
    }

    private static void markRandomTreasure(RoomGraph graph, Random rng, int count) {
        List<Room> normals = new ArrayList<>(graph.withRole(Role.NORMAL));
        Collections.shuffle(normals, rng);
        for (int i = 0; i < Math.min(count, normals.size()); i++) {
            normals.get(i).role = Role.TREASURE;
        }
    }

    /**
     * Grows a small upper floor: one NORMAL room becomes a ladder shaft with
     * a twin above, and a short randomized branch of 3-5 rooms hangs off it.
     * If the objective was on the ground floor it may migrate up, which makes
     * multi-floor runs feel genuinely different.
     */
    private static void addSecondFloor(RoomGraph graph, Random rng) {
        List<Room> normals = new ArrayList<>(graph.withRole(Role.NORMAL));
        if (normals.isEmpty()) {
            return;
        }
        Room shaftBase = normals.get(rng.nextInt(normals.size()));
        shaftBase.role = Role.SHAFT;
        Cell base = shaftBase.cell;
        Cell top = new Cell(base.x(), base.z(), 1);
        graph.add(top, Role.SHAFT);

        Cell cur = top;
        int branch = 3 + rng.nextInt(3);
        List<Cell> added = new ArrayList<>();
        for (int i = 0; i < branch; i++) {
            List<Dir> options = new ArrayList<>();
            for (Dir dir : Dir.values()) {
                if (graph.get(cur.step(dir)) == null) {
                    options.add(dir);
                }
            }
            if (options.isEmpty()) {
                break;
            }
            Dir dir = options.get(rng.nextInt(options.size()));
            Cell next = cur.step(dir);
            graph.add(next, Role.NORMAL);
            graph.connect(cur, dir);
            added.add(next);
            cur = next;
        }
        if (!added.isEmpty() && rng.nextBoolean()) {
            // Relocate the objective to the end of the upper branch.
            for (Room room : graph.withRole(Role.OBJECTIVE)) {
                room.role = Role.NORMAL;
            }
            graph.get(added.get(added.size() - 1)).role = Role.OBJECTIVE;
        } else if (!added.isEmpty()) {
            graph.get(added.get(added.size() - 1)).role = Role.TREASURE;
        }
    }
}
