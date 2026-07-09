package com.evensteven.vhlite.vault;

import com.evensteven.vhlite.menu.Menu;
import com.evensteven.vhlite.vault.generation.RoomGraph;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * A chest-GUI rendering of the Vault Map for players who want a bigger
 * look than the offhand corner overlay gives: colored panes on a 9x5 grid
 * centered on wherever you're standing, matching the same fog-of-war and
 * room-role coloring as the held map. Opened with /vh map.
 */
public final class VaultMapMenu extends Menu {

    private static final int WIDTH = 9;
    private static final int HEIGHT = 5;

    private final VaultInstance instance;
    private int viewFloor;

    public VaultMapMenu(Player viewer, VaultInstance instance) {
        super(viewer, 6, "§dVault Map");
        this.instance = instance;
        RoomGraph.Cell here = instance.cellOf(viewer.getLocation());
        this.viewFloor = here != null ? here.floor() : 0;
    }

    @Override
    protected void build() {
        fillRow(5, Material.BLACK_STAINED_GLASS_PANE);
        RoomGraph graph = instance.gen != null ? instance.gen.graph : null;
        if (graph == null) {
            icon(22, named(Material.BARRIER, "§cThe vault hasn't finished forming."));
            return;
        }
        RoomGraph.Cell playerCell = instance.cellOf(viewer.getLocation());
        RoomGraph.Cell center = playerCell != null ? playerCell : anyVisited();
        if (center == null) {
            icon(22, named(Material.BARRIER, "§7Nothing explored yet — go take a look around."));
            return;
        }
        int originX = center.x() - WIDTH / 2;
        int originZ = center.z() - HEIGHT / 2;
        for (int dz = 0; dz < HEIGHT; dz++) {
            for (int dx = 0; dx < WIDTH; dx++) {
                RoomGraph.Cell cell = new RoomGraph.Cell(originX + dx, originZ + dz, viewFloor);
                int slot = dz * 9 + dx;
                RoomGraph.Room room = graph.get(cell);
                if (room == null) {
                    icon(slot, named(Material.BLACK_STAINED_GLASS_PANE, "§8"));
                    continue;
                }
                boolean visited = instance.visitedCells.contains(cell);
                if (cell.equals(playerCell)) {
                    icon(slot, named(Material.COMPASS, "§e➤ You are here", roleLabel(room.role)));
                    continue;
                }
                Material mat = !visited ? Material.GRAY_STAINED_GLASS_PANE : roleMaterial(room.role);
                icon(slot, named(mat, visited ? roleLabel(room.role) : "§8Unexplored"));
            }
        }
        boolean hasUpper = graph.all().stream().anyMatch(r -> r.cell.floor() > 0);
        if (hasUpper) {
            button(49, named(Material.LADDER, "§7Viewing floor §f" + viewFloor,
                            "§7Click to view the " + (viewFloor == 0 ? "upper" : "ground") + " floor."),
                    event -> {
                        viewFloor = viewFloor == 0 ? 1 : 0;
                        refresh();
                    });
        }
        icon(53, named(Material.WRITABLE_BOOK, "§d" + instance.blueprint().theme().displayName,
                "§7" + instance.blueprint().objective().displayName));
    }

    private RoomGraph.Cell anyVisited() {
        return instance.visitedCells.stream().findFirst().orElse(null);
    }

    private Material roleMaterial(RoomGraph.Role role) {
        return switch (role) {
            case START -> Material.LIME_STAINED_GLASS_PANE;
            case OBJECTIVE -> Material.MAGENTA_STAINED_GLASS_PANE;
            case TREASURE -> Material.YELLOW_STAINED_GLASS_PANE;
            case SHAFT -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case NORMAL -> Material.WHITE_STAINED_GLASS_PANE;
        };
    }

    private String roleLabel(RoomGraph.Role role) {
        return switch (role) {
            case START -> "§aStart";
            case OBJECTIVE -> "§dObjective";
            case TREASURE -> "§6Treasure";
            case SHAFT -> "§bStairs";
            case NORMAL -> "§7Room";
        };
    }
}
