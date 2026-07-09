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

    /**
     * A 6-8 room spine with 2-4 side branches hanging off it — a gauntlet
     * that still rewards poking into the arms. Objective at the far end.
     */
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
            // Side branches: 1-2 rooms poking north/south off the spine,
            // the deepest one holding treasure.
            int branches = 2 + rng.nextInt(3);
            for (int b = 0; b < branches; b++) {
                int spine = 1 + rng.nextInt(length - 2);
                Dir dir = rng.nextBoolean() ? Dir.NORTH : Dir.SOUTH;
                Cell cur = new Cell(spine, 0, 0);
                int arm = 1 + rng.nextInt(2);
                for (int step = 0; step < arm; step++) {
                    Cell next = cur.step(dir);
                    if (graph.get(next) != null) {
                        break;
                    }
                    graph.add(next, step == arm - 1 ? Role.TREASURE : Role.NORMAL);
                    graph.connect(cur, dir);
                    cur = next;
                }
            }
            // Guarantee at least one treasure room even if every branch collided.
            if (graph.withRole(Role.TREASURE).isEmpty()) {
                graph.get(new Cell(1 + rng.nextInt(length - 2), 0, 0)).role = Role.TREASURE;
            }
            if (multiFloor) {
                addSecondFloor(graph, rng);
            }
            return graph;
        }
    },

    /** A 4-6 wide maze: spanning tree plus a few loops, objective at max distance. */
    MAZE {
        @Override
        public RoomGraph plan(Random rng, boolean multiFloor) {
            RoomGraph graph = new RoomGraph();
            int size = 4 + rng.nextInt(3);
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
     * Grows an upper floor: one NORMAL room becomes a ladder shaft with a
     * twin above, and a 4-7 room mini-layout grows off it by attaching each
     * new room to a random existing upper room — so upstairs branches too,
     * instead of being one hallway. Half the time the objective migrates up
     * to the farthest upper room; otherwise that room holds treasure.
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

        record Growth(Cell from, Dir dir) {
        }
        List<Cell> upper = new ArrayList<>();
        upper.add(top);
        int rooms = 4 + rng.nextInt(4);
        for (int i = 0; i < rooms; i++) {
            List<Growth> options = new ArrayList<>();
            for (Cell from : upper) {
                for (Dir dir : Dir.values()) {
                    if (graph.get(from.step(dir)) == null) {
                        options.add(new Growth(from, dir));
                    }
                }
            }
            if (options.isEmpty()) {
                break;
            }
            Growth pick = options.get(rng.nextInt(options.size()));
            Cell next = pick.from().step(pick.dir());
            graph.add(next, Role.NORMAL);
            graph.connect(pick.from(), pick.dir());
            upper.add(next);
            // Occasional extra doorway between adjacent upper rooms = loops.
            for (Dir dir : Dir.values()) {
                Cell around = next.step(dir);
                if (graph.get(around) != null && around.floor() == 1
                        && !graph.get(next).doors.contains(dir) && rng.nextDouble() < 0.15) {
                    graph.connect(next, dir);
                }
            }
        }
        if (upper.size() <= 1) {
            return;
        }
        // Farthest upper room from the shaft gets the payoff.
        Map<Cell, Integer> dist = graph.distancesFrom(top);
        Cell far = top;
        for (Cell cell : upper) {
            if (dist.getOrDefault(cell, 0) > dist.getOrDefault(far, 0)) {
                far = cell;
            }
        }
        if (rng.nextBoolean()) {
            for (Room room : graph.withRole(Role.OBJECTIVE)) {
                room.role = Role.NORMAL;
            }
            graph.get(far).role = Role.OBJECTIVE;
        } else {
            graph.get(far).role = Role.TREASURE;
        }
    }
}
