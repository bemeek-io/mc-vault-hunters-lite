package com.evensteven.vhlite.vault.generation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The abstract shape of a vault before any blocks exist: rooms on a cell
 * grid (per floor), the doorways between them, and each room's role. Layout
 * planners produce one of these; the generator turns it into geometry.
 */
public final class RoomGraph {

    public enum Dir {
        NORTH(0, -1), SOUTH(0, 1), WEST(-1, 0), EAST(1, 0);

        public final int dx;
        public final int dz;

        Dir(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }

        public Dir opposite() {
            return switch (this) {
                case NORTH -> SOUTH;
                case SOUTH -> NORTH;
                case WEST -> EAST;
                case EAST -> WEST;
            };
        }
    }

    public enum Role { START, NORMAL, TREASURE, OBJECTIVE, SHAFT }

    public record Cell(int x, int z, int floor) {
        public Cell step(Dir dir) {
            return new Cell(x + dir.dx, z + dir.dz, floor);
        }
    }

    public static final class Room {
        public final Cell cell;
        public Role role;
        public final EnumSet<Dir> doors = EnumSet.noneOf(Dir.class);

        Room(Cell cell, Role role) {
            this.cell = cell;
            this.role = role;
        }
    }

    private final Map<Cell, Room> rooms = new HashMap<>();

    public Room add(Cell cell, Role role) {
        return rooms.computeIfAbsent(cell, c -> new Room(c, role));
    }

    /** Opens a doorway both ways; both rooms must exist. */
    public void connect(Cell a, Dir dir) {
        Room from = rooms.get(a);
        Room to = rooms.get(a.step(dir));
        if (from == null || to == null) {
            throw new IllegalStateException("connect() between missing rooms at " + a + " " + dir);
        }
        from.doors.add(dir);
        to.doors.add(dir.opposite());
    }

    public Room get(Cell cell) {
        return rooms.get(cell);
    }

    public List<Room> all() {
        return new ArrayList<>(rooms.values());
    }

    public List<Room> withRole(Role role) {
        return rooms.values().stream().filter(r -> r.role == role).toList();
    }

    /** BFS distances from a cell, walking doorways (same floor only). */
    public Map<Cell, Integer> distancesFrom(Cell startCell) {
        Map<Cell, Integer> dist = new HashMap<>();
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        dist.put(startCell, 0);
        queue.add(startCell);
        while (!queue.isEmpty()) {
            Cell cur = queue.poll();
            Room room = rooms.get(cur);
            if (room == null) {
                continue;
            }
            for (Dir dir : room.doors) {
                Cell next = cur.step(dir);
                if (!dist.containsKey(next)) {
                    dist.put(next, dist.get(cur) + 1);
                    queue.add(next);
                }
            }
        }
        return dist;
    }

    /** Sanity check used by tests/testgen: every room reachable from start. */
    public boolean fullyConnected() {
        List<Room> starts = withRole(Role.START);
        if (starts.isEmpty()) {
            return false;
        }
        Set<Cell> reached = new HashSet<>(distancesFrom(starts.get(0).cell).keySet());
        // Shaft rooms bridge floors: if a shaft cell is reached on one floor,
        // its twin on the other floor is reachable by ladder.
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Room room : rooms.values()) {
                if (room.role == Role.SHAFT && reached.contains(room.cell)) {
                    for (Room twin : rooms.values()) {
                        if (twin.role == Role.SHAFT && twin.cell.x() == room.cell.x()
                                && twin.cell.z() == room.cell.z() && !reached.contains(twin.cell)) {
                            for (Map.Entry<Cell, Integer> e : distancesFrom(twin.cell).entrySet()) {
                                grew |= reached.add(e.getKey());
                            }
                        }
                    }
                }
            }
        }
        return reached.size() == rooms.size();
    }
}
