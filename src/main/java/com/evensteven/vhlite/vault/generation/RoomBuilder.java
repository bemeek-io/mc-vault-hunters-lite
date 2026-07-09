package com.evensteven.vhlite.vault.generation;

import com.evensteven.vhlite.vault.generation.RoomGraph.Dir;
import com.evensteven.vhlite.vault.generation.RoomGraph.Room;
import org.bukkit.block.data.BlockData;

import java.util.Random;

/**
 * Builds one room's geometry into the buffer. Rooms are ROOM x ROOM
 * footprints (walls included); the generator carves corridors and doorways
 * afterward, so builders only fill their own box. All coordinates are
 * relative to the instance origin; ox/oy/oz is this room's min corner.
 *
 * Programmatic rooms + weighted palettes give combinatorial variety with no
 * bundled assets. New shapes = new subclass; an NBT-backed builder could
 * slot in here later without touching the generator.
 */
public abstract class RoomBuilder {

    public static final int ROOM = 19;

    /** Interior height this room was built with (needed for doors/corridors). */
    public abstract int height(Random rng);

    public abstract void build(BlockBuffer buf, Theme theme, Random rng,
            int ox, int oy, int oz, int height, Room room, GenResult out);

    // ------------------------------------------------------------- helpers

    /** Floor, ceiling, and four walls of the standard room shell. */
    protected void shell(BlockBuffer buf, Theme theme, Random rng, int ox, int oy, int oz, int h) {
        for (int x = 0; x < ROOM; x++) {
            for (int z = 0; z < ROOM; z++) {
                buf.set(ox + x, oy, oz + z, theme.pick(theme.floor, rng));
                buf.set(ox + x, oy + h + 1, oz + z, theme.pick(theme.ceiling, rng));
            }
        }
        for (int y = 1; y <= h; y++) {
            for (int i = 0; i < ROOM; i++) {
                buf.set(ox + i, oy + y, oz, wallOrLight(theme, rng));
                buf.set(ox + i, oy + y, oz + ROOM - 1, wallOrLight(theme, rng));
                buf.set(ox, oy + y, oz + i, wallOrLight(theme, rng));
                buf.set(ox + ROOM - 1, oy + y, oz + i, wallOrLight(theme, rng));
            }
        }
        air(buf, ox + 1, oy + 1, oz + 1, ox + ROOM - 2, oy + h, oz + ROOM - 2);
    }

    /** Walls get the odd embedded light so rooms are never pitch black. */
    private BlockData wallOrLight(Theme theme, Random rng) {
        return rng.nextInt(24) == 0 ? theme.light : theme.pick(theme.wall, rng);
    }

    protected void air(BlockBuffer buf, int x1, int y1, int z1, int x2, int y2, int z2) {
        buf.fill(x1, y1, z1, x2, y2, z2, GenBlocks.AIR);
    }

    /** A vertical pillar of the theme's pillar block, capped with a light. */
    protected void pillar(BlockBuffer buf, Theme theme, int x, int oy, int z, int h) {
        for (int y = 1; y <= h; y++) {
            buf.set(x, oy + y, z, theme.pillar[0]);
        }
        buf.set(x, oy + Math.max(2, h / 2), z, theme.light);
    }

    /** Registers a loot chest spot (block placed by the applier). */
    protected void chest(BlockBuffer buf, GenResult out, int x, int y, int z, boolean treasure) {
        buf.set(x, y, z, treasure ? GenBlocks.TRAPPED_CHEST : GenBlocks.CHEST);
        out.chests.add(new GenResult.ChestSpot(new Vec3(x, y, z), treasure));
    }

    /** Scatters accent clutter on the floor: never blocks doorways (kept off the midlines). */
    protected void clutter(BlockBuffer buf, Theme theme, Random rng, int ox, int oy, int oz, int amount) {
        for (int i = 0; i < amount; i++) {
            int x = ox + 2 + rng.nextInt(ROOM - 4);
            int z = oz + 2 + rng.nextInt(ROOM - 4);
            if (Math.abs(x - (ox + ROOM / 2)) <= 1 || Math.abs(z - (oz + ROOM / 2)) <= 1) {
                continue; // keep the walking lanes between doors clear
            }
            buf.set(x, oy + 1, z, theme.pick(theme.accent, rng));
        }
    }

    /**
     * Furniture pass: 2-3 set-pieces dropped into the room's quadrants so
     * halls read as lived-in spaces instead of empty boxes. Quadrant centers
     * sit clear of the door lanes; later fixture writes (start pad, exit)
     * simply overwrite whatever landed under them.
     */
    protected void features(BlockBuffer buf, Theme theme, Random rng,
            int ox, int oy, int oz, int h, GenResult out, int count) {
        int[][] quadrants = {{5, 5}, {13, 5}, {5, 13}, {13, 13}};
        // Shuffle quadrant order so features never favor one corner.
        for (int i = quadrants.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int[] tmp = quadrants[i];
            quadrants[i] = quadrants[j];
            quadrants[j] = tmp;
        }
        for (int f = 0; f < Math.min(count, quadrants.length); f++) {
            int cx = ox + quadrants[f][0] + rng.nextInt(3) - 1;
            int cz = oz + quadrants[f][1] + rng.nextInt(3) - 1;
            switch (rng.nextInt(5)) {
                case 0 -> { // crate pile: barrels, one of them holding loot
                    buf.set(cx, oy + 1, cz, GenBlocks.BARREL); // the loot barrel
                    for (int i = 0; i < 3 + rng.nextInt(3); i++) {
                        int bx = cx + rng.nextInt(2);
                        int bz = cz + rng.nextInt(2);
                        int by = oy + 1 + (rng.nextInt(3) == 0 ? 1 : 0);
                        if (by > oy + 1 && buf.get(bx, by - 1, bz) == GenBlocks.AIR) {
                            by = oy + 1; // no floating crates
                        }
                        buf.set(bx, by, bz, GenBlocks.BARREL);
                    }
                    out.chests.add(new GenResult.ChestSpot(new Vec3(cx, oy + 1, cz), false));
                }
                case 1 -> { // monument: accent-ringed pillar with a light cap
                    int tall = 2 + rng.nextInt(Math.max(1, h - 2));
                    for (int y = 1; y <= tall; y++) {
                        buf.set(cx, oy + y, cz, theme.pick(theme.pillar, rng));
                    }
                    buf.set(cx, oy + tall + 1, cz, theme.light);
                    for (int[] side : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        buf.set(cx + side[0], oy, cz + side[1], theme.pick(theme.accent, rng));
                    }
                }
                case 2 -> { // rubble: collapsed accent scatter and cobwebs
                    for (int i = 0; i < 4 + rng.nextInt(4); i++) {
                        int bx = cx + rng.nextInt(4) - 1;
                        int bz = cz + rng.nextInt(4) - 1;
                        buf.set(bx, oy + 1, bz,
                                rng.nextInt(3) == 0 ? GenBlocks.COBWEB : theme.pick(theme.accent, rng));
                    }
                }
                case 3 -> { // lamp post
                    buf.set(cx, oy + 1, cz, theme.pick(theme.pillar, rng));
                    buf.set(cx, oy + 2, cz, theme.pick(theme.pillar, rng));
                    buf.set(cx, oy + 3, cz, GenBlocks.LANTERN);
                }
                default -> { // raised dais with accent inlay
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            buf.set(cx + dx, oy + 1, cz + dz, theme.pick(theme.floor, rng));
                        }
                    }
                    buf.set(cx, oy + 2, cz, theme.pick(theme.accent, rng));
                }
            }
        }
        // Tall rooms get a hanging lantern or two under the ceiling.
        if (h >= 7) {
            for (int i = 0; i < 1 + rng.nextInt(2); i++) {
                buf.set(ox + 4 + rng.nextInt(ROOM - 8), oy + h, oz + 4 + rng.nextInt(ROOM - 8),
                        GenBlocks.LANTERN_HANGING);
            }
        }
    }

    /** Registers 2-4 mob spawn points and a couple of pedestal candidates. */
    protected void marks(GenResult out, Random rng, int ox, int oy, int oz) {
        int spawns = 2 + rng.nextInt(3);
        for (int i = 0; i < spawns; i++) {
            out.mobSpawns.add(new Vec3(ox + 3 + rng.nextInt(ROOM - 6), oy + 1, oz + 3 + rng.nextInt(ROOM - 6)));
        }
        out.pedestalCandidates.add(new Vec3(ox + 3 + rng.nextInt(ROOM - 6), oy + 1, oz + 3 + rng.nextInt(ROOM - 6)));
    }

    // ------------------------------------------------------------ variants

    /** Plain hall with random clutter; the bread-and-butter room. */
    public static final class BoxRoom extends RoomBuilder {
        @Override
        public int height(Random rng) {
            return 5 + rng.nextInt(4);
        }

        @Override
        public void build(BlockBuffer buf, Theme theme, Random rng,
                int ox, int oy, int oz, int h, Room room, GenResult out) {
            shell(buf, theme, rng, ox, oy, oz, h);
            clutter(buf, theme, rng, ox, oy, oz, 4 + rng.nextInt(6));
            features(buf, theme, rng, ox, oy, oz, h, out, 2 + rng.nextInt(2));
            if (rng.nextInt(3) == 0) {
                chest(buf, out, ox + 2 + rng.nextInt(ROOM - 4), oy + 1, oz + 2 + rng.nextInt(ROOM - 4), false);
            }
            marks(out, rng, ox, oy, oz);
        }
    }

    /** Tall hall held up by a grid of pillars. */
    public static final class PillarHall extends RoomBuilder {
        @Override
        public int height(Random rng) {
            return 8 + rng.nextInt(4);
        }

        @Override
        public void build(BlockBuffer buf, Theme theme, Random rng,
                int ox, int oy, int oz, int h, Room room, GenResult out) {
            shell(buf, theme, rng, ox, oy, oz, h);
            for (int px = 4; px < ROOM - 2; px += 5) {
                for (int pz = 4; pz < ROOM - 2; pz += 5) {
                    pillar(buf, theme, ox + px, oy, oz + pz, h);
                }
            }
            features(buf, theme, rng, ox, oy, oz, h, out, 1 + rng.nextInt(2));
            if (rng.nextInt(2) == 0) {
                chest(buf, out, ox + ROOM / 2, oy + 1, oz + ROOM / 2, false);
            }
            marks(out, rng, ox, oy, oz);
        }
    }

    /**
     * Organic cavern: the shell is carved back out with hash-noise so walls
     * bulge and shrink. Same footprint, wholly different feel.
     */
    public static final class CavernRoom extends RoomBuilder {
        @Override
        public int height(Random rng) {
            return 7 + rng.nextInt(4);
        }

        @Override
        public void build(BlockBuffer buf, Theme theme, Random rng,
                int ox, int oy, int oz, int h, Room room, GenResult out) {
            long salt = rng.nextLong();
            shell(buf, theme, rng, ox, oy, oz, h);
            // Bulge the walls inward: an inner ring of wall blocks appears
            // where the noise says so, making the cave narrow and widen.
            for (int x = 1; x < ROOM - 1; x++) {
                for (int z = 1; z < ROOM - 1; z++) {
                    boolean edge = x <= 2 || z <= 2 || x >= ROOM - 3 || z >= ROOM - 3;
                    if (!edge || onMidline(x, z)) {
                        continue;
                    }
                    if (noise(salt, x, z) < 0.45) {
                        int bump = 1 + (int) (noise(salt, z * 31, x * 17) * (h - 1));
                        for (int y = 1; y <= bump; y++) {
                            buf.set(ox + x, oy + y, oz + z, theme.pick(theme.wall, rng));
                        }
                    }
                }
            }
            // Stalagmite-ish accents.
            for (int i = 0; i < 5 + rng.nextInt(5); i++) {
                int x = ox + 4 + rng.nextInt(ROOM - 8);
                int z = oz + 4 + rng.nextInt(ROOM - 8);
                int tall = 1 + rng.nextInt(3);
                for (int y = 1; y <= tall; y++) {
                    buf.set(x, oy + y, z, theme.pick(theme.accent, rng));
                }
            }
            features(buf, theme, rng, ox, oy, oz, h, out, 1 + rng.nextInt(2));
            if (rng.nextInt(3) == 0) {
                chest(buf, out, ox + ROOM / 2 + rng.nextInt(3) - 1, oy + 1, oz + ROOM / 2 + rng.nextInt(3) - 1, false);
            }
            marks(out, rng, ox, oy, oz);
        }

        private boolean onMidline(int x, int z) {
            return Math.abs(x - ROOM / 2) <= 1 || Math.abs(z - ROOM / 2) <= 1;
        }

        /** Cheap deterministic 2D hash noise in [0,1). */
        private double noise(long salt, int x, int z) {
            long v = salt ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
            v *= 0xBF58476D1CE4E5B9L;
            v ^= v >>> 27;
            return (v >>> 11) / (double) (1L << 53);
        }
    }

    /** Vault-within-the-vault: accent-trimmed room with several chests. */
    public static final class TreasureRoom extends RoomBuilder {
        @Override
        public int height(Random rng) {
            return 5 + rng.nextInt(2);
        }

        @Override
        public void build(BlockBuffer buf, Theme theme, Random rng,
                int ox, int oy, int oz, int h, Room room, GenResult out) {
            shell(buf, theme, rng, ox, oy, oz, h);
            // Accent floor inlay so the room announces itself.
            for (int x = 5; x < ROOM - 5; x++) {
                for (int z = 5; z < ROOM - 5; z++) {
                    buf.set(ox + x, oy, oz + z, theme.pick(theme.accent, rng));
                }
            }
            int chests = 2 + rng.nextInt(2);
            for (int i = 0; i < chests; i++) {
                chest(buf, out, ox + 6 + rng.nextInt(ROOM - 12), oy + 1, oz + 6 + rng.nextInt(ROOM - 12), false);
            }
            pillar(buf, theme, ox + 3, oy, oz + 3, h);
            pillar(buf, theme, ox + ROOM - 4, oy, oz + 3, h);
            pillar(buf, theme, ox + 3, oy, oz + ROOM - 4, h);
            pillar(buf, theme, ox + ROOM - 4, oy, oz + ROOM - 4, h);
            marks(out, rng, ox, oy, oz);
        }
    }

    /** The objective room: tall, open, with a raised rim and the boss/beacon spot. */
    public static final class BossArena extends RoomBuilder {
        @Override
        public int height(Random rng) {
            return 10 + rng.nextInt(3);
        }

        @Override
        public void build(BlockBuffer buf, Theme theme, Random rng,
                int ox, int oy, int oz, int h, Room room, GenResult out) {
            shell(buf, theme, rng, ox, oy, oz, h);
            // Raised rim walkway around the arena floor.
            for (int x = 1; x < ROOM - 1; x++) {
                for (int z = 1; z < ROOM - 1; z++) {
                    boolean rim = x <= 2 || z <= 2 || x >= ROOM - 3 || z >= ROOM - 3;
                    if (rim && !(Math.abs(x - ROOM / 2) <= 1 || Math.abs(z - ROOM / 2) <= 1)) {
                        buf.set(ox + x, oy + 1, oz + z, theme.pick(theme.floor, rng));
                    }
                }
            }
            int cx = ox + ROOM / 2;
            int cz = oz + ROOM / 2;
            // Accent circle marking the center.
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) <= 2) {
                        buf.set(cx + dx, oy, cz + dz, theme.pick(theme.accent, rng));
                    }
                }
            }
            out.bossSpawn = new Vec3(cx, oy + 1, cz);
            out.defendPoint = new Vec3(cx, oy + 1, cz);
            // Arena always pays out.
            chest(buf, out, ox + 3, oy + 2, oz + ROOM / 2, false);
            chest(buf, out, ox + ROOM - 4, oy + 2, oz + ROOM / 2, false);
            marks(out, rng, ox, oy, oz);
        }
    }

    /**
     * Ladder shaft connecting a room to its twin one floor up. Built once
     * from the LOWER cell; it owns the whole vertical column including the
     * upper room's shell.
     */
    public static final class ShaftRoom extends RoomBuilder {
        private final int floorHeight;

        public ShaftRoom(int floorHeight) {
            this.floorHeight = floorHeight;
        }

        @Override
        public int height(Random rng) {
            return 5;
        }

        @Override
        public void build(BlockBuffer buf, Theme theme, Random rng,
                int ox, int oy, int oz, int h, Room room, GenResult out) {
            shell(buf, theme, rng, ox, oy, oz, h);
            int cx = ox + ROOM / 2;
            int cz = oz + ROOM / 2;
            // Fully enclosed 3x3 climbing tube from this room up through the
            // void gap into the upper room: 8 wall blocks ringing a central
            // ladder column, so nobody can wiggle off into the void.
            // Built AFTER both rooms' shells so its writes punch the holes.
            for (int y = 1; y <= floorHeight + 2; y++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) {
                            buf.set(cx, oy + y, cz,
                                    y <= floorHeight + 1 ? GenBlocks.LADDER_SOUTH : GenBlocks.AIR);
                        } else {
                            buf.set(cx + dx, oy + y, cz + dz, theme.pick(theme.pillar, rng));
                        }
                    }
                }
            }
            // South-facing doorways at the bottom (to get in) and the top
            // (to step off on the upper floor).
            buf.set(cx, oy + 1, cz + 1, GenBlocks.AIR);
            buf.set(cx, oy + 2, cz + 1, GenBlocks.AIR);
            buf.set(cx, oy + floorHeight + 1, cz + 1, GenBlocks.AIR);
            buf.set(cx, oy + floorHeight + 2, cz + 1, GenBlocks.AIR);
            // Cap the tube.
            buf.fill(cx - 1, oy + floorHeight + 3, cz - 1, cx + 1, oy + floorHeight + 3, cz + 1,
                    theme.pick(theme.ceiling, rng));
            marks(out, rng, ox, oy, oz);
        }
    }
}
