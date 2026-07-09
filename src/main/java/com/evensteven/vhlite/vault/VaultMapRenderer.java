package com.evensteven.vhlite.vault;

import com.evensteven.vhlite.vault.generation.RoomGraph;
import com.evensteven.vhlite.vault.generation.VaultGenerator;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.Color;

/**
 * Draws the vault minimap onto a vanilla map item: only rooms the party has
 * actually entered (fog-of-war stays over the rest), doorways between
 * visited rooms, the start room in green, the objective room in purple once
 * found, upper-floor rooms in blue — plus a live arrow cursor for every
 * party member. Pure server-side; vanilla clients just see a map.
 */
public final class VaultMapRenderer extends MapRenderer {

    private static final int PITCH = 14;  // px per cell
    private static final int SQUARE = 10; // room square size
    private static final Color BG = new Color(24, 20, 34);
    private static final Color ROOM = new Color(150, 150, 160);
    private static final Color ROOM_UP = new Color(110, 150, 210);
    private static final Color START = new Color(90, 200, 90);
    private static final Color OBJECTIVE = new Color(190, 90, 220);
    private static final Color TREASURE = new Color(230, 190, 60);
    private static final Color DOOR = new Color(100, 100, 110);

    private final VaultInstance instance;
    private int drawnVersion = -1;

    public VaultMapRenderer(VaultInstance instance) {
        this.instance = instance;
    }

    @Override
    public void render(MapView view, MapCanvas canvas, Player player) {
        if (instance.gen == null || instance.gen.graph == null) {
            return;
        }
        if (drawnVersion != instance.mapVersion) {
            drawnVersion = instance.mapVersion;
            drawRooms(canvas);
        }
        drawCursors(canvas);
    }

    // Fixed mapping: cell (x,z) -> canvas px, centered on the graph's bounds.
    private int minCellX = Integer.MAX_VALUE, minCellZ = Integer.MAX_VALUE;
    private int offsetX, offsetZ;
    private boolean boundsReady;

    private void computeBounds() {
        int maxCellX = Integer.MIN_VALUE, maxCellZ = Integer.MIN_VALUE;
        for (RoomGraph.Room room : instance.gen.graph.all()) {
            minCellX = Math.min(minCellX, room.cell.x());
            minCellZ = Math.min(minCellZ, room.cell.z());
            maxCellX = Math.max(maxCellX, room.cell.x());
            maxCellZ = Math.max(maxCellZ, room.cell.z());
        }
        offsetX = (128 - (maxCellX - minCellX + 1) * PITCH) / 2;
        offsetZ = (128 - (maxCellZ - minCellZ + 1) * PITCH) / 2;
        boundsReady = true;
    }

    private void drawRooms(MapCanvas canvas) {
        if (!boundsReady) {
            computeBounds();
        }
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) {
                canvas.setPixelColor(x, z, BG);
            }
        }
        for (RoomGraph.Cell cell : instance.visitedCells) {
            RoomGraph.Room room = instance.gen.graph.get(cell);
            if (room == null) {
                continue;
            }
            int px = offsetX + (cell.x() - minCellX) * PITCH;
            int pz = offsetZ + (cell.z() - minCellZ) * PITCH;
            Color color = switch (room.role) {
                case START -> START;
                case OBJECTIVE -> OBJECTIVE;
                case TREASURE -> TREASURE;
                default -> cell.floor() > 0 ? ROOM_UP : ROOM;
            };
            fill(canvas, px, pz, SQUARE, SQUARE, color);
            if (cell.floor() > 0) {
                // Upper-floor rooms get a hollow center so floors read apart.
                fill(canvas, px + 3, pz + 3, SQUARE - 6, SQUARE - 6, BG);
            }
            // Doorways to visited neighbors.
            for (RoomGraph.Dir dir : room.doors) {
                if (!instance.visitedCells.contains(cell.step(dir))) {
                    continue;
                }
                switch (dir) {
                    case EAST -> fill(canvas, px + SQUARE, pz + SQUARE / 2 - 1, PITCH - SQUARE, 2, DOOR);
                    case SOUTH -> fill(canvas, px + SQUARE / 2 - 1, pz + SQUARE, 2, PITCH - SQUARE, DOOR);
                    default -> {
                        // WEST/NORTH are drawn by the neighbor's EAST/SOUTH.
                    }
                }
            }
        }
    }

    private void fill(MapCanvas canvas, int x, int z, int w, int h, Color color) {
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < h; dz++) {
                int px = x + dx;
                int pz = z + dz;
                if (px >= 0 && px < 128 && pz >= 0 && pz < 128) {
                    canvas.setPixelColor(px, pz, color);
                }
            }
        }
    }

    private void drawCursors(MapCanvas canvas) {
        MapCursorCollection cursors = new MapCursorCollection();
        for (Player member : instance.players()) {
            Location loc = member.getLocation();
            if (!instance.contains(loc)) {
                continue;
            }
            double cellX = (loc.getX() - instance.blueprint().originX()) / VaultGenerator.CELL - minCellX;
            double cellZ = (loc.getZ() - instance.blueprint().originZ()) / VaultGenerator.CELL - minCellZ;
            // Room squares cover ~the same fraction of a PITCH as the room
            // does of a CELL, so a straight proportional mapping lines up.
            int px = (int) (offsetX + cellX * PITCH);
            int pz = (int) (offsetZ + cellZ * PITCH);
            int cx = Math.max(-128, Math.min(127, px * 2 - 128));
            int cz = Math.max(-128, Math.min(127, pz * 2 - 128));
            byte direction = (byte) (Math.floorMod(Math.round((loc.getYaw() + 11.25f) / 22.5f), 16));
            cursors.addCursor(new MapCursor((byte) cx, (byte) cz, direction,
                    MapCursor.Type.PLAYER, true));
        }
        canvas.setCursors(cursors);
    }
}
