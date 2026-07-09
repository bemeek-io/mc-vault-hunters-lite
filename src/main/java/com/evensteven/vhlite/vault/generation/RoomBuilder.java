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

    /**
     * The room's footprint within its 19x19 box. Shapes may carve corners
     * and edges freely but MUST keep the mid-edge door strips (local 7-11
     * at each edge) inside the mask, because corridors punch there.
     */
    protected java.util.function.BiPredicate<Integer, Integer> mask = (x, z) -> true;

    protected final boolean inMask(int localX, int localZ) {
        return localX >= 0 && localX < ROOM && localZ >= 0 && localZ < ROOM
                && mask.test(localX, localZ);
    }

    /** Floor, ceiling, and shape-hugging walls of the room shell. */
    protected void shell(BlockBuffer buf, Theme theme, Random rng, int ox, int oy, int oz, int h) {
        for (int x = 0; x < ROOM; x++) {
            for (int z = 0; z < ROOM; z++) {
                if (!mask.test(x, z)) {
                    continue;
                }
                buf.set(ox + x, oy, oz + z, theme.pick(theme.floor, rng));
                buf.set(ox + x, oy + h + 1, oz + z, theme.pick(theme.ceiling, rng));
                boolean wall = !inMask(x + 1, z) || !inMask(x - 1, z)
                        || !inMask(x, z + 1) || !inMask(x, z - 1);
                for (int y = 1; y <= h; y++) {
                    buf.set(ox + x, oy + y, oz + z, wall ? wallOrLight(theme, rng) : GenBlocks.AIR);
                }
            }
        }
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
            int lx = 2 + rng.nextInt(ROOM - 4);
            int lz = 2 + rng.nextInt(ROOM - 4);
            if (Math.abs(lx - ROOM / 2) <= 1 || Math.abs(lz - ROOM / 2) <= 1
                    || !inMask(lx, lz) || !inMask(lx + 1, lz) || !inMask(lx - 1, lz)) {
                continue; // keep the walking lanes clear, stay off the walls
            }
            buf.set(ox + lx, oy + 1, oz + lz, theme.pick(theme.accent, rng));
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
            int lx = quadrants[f][0] + rng.nextInt(3) - 1;
            int lz = quadrants[f][1] + rng.nextInt(3) - 1;
            // A set-piece needs breathing room inside the shape.
            if (!inMask(lx, lz) || !inMask(lx + 2, lz) || !inMask(lx - 2, lz)
                    || !inMask(lx, lz + 2) || !inMask(lx, lz - 2)) {
                continue;
            }
            int cx = ox + lx;
            int cz = oz + lz;
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
                int lx = 4 + rng.nextInt(ROOM - 8);
                int lz = 4 + rng.nextInt(ROOM - 8);
                if (inMask(lx, lz)) {
                    buf.set(ox + lx, oy + h, oz + lz, GenBlocks.LANTERN_HANGING);
                }
            }
        }
    }

    /**
     * Registers 3-6 one-time encounter spawners plus a pedestal candidate,
     * all inside the room's shape (a spawner in a wall spawns nothing).
     */
    protected void marks(GenResult out, Random rng, int ox, int oy, int oz) {
        int spawns = 3 + rng.nextInt(4);
        for (int i = 0; i < spawns; i++) {
            Vec3 spot = openSpot(rng, ox, oy, oz);
            if (spot != null) {
                out.mobSpawns.add(spot);
            }
        }
        Vec3 pedestal = openSpot(rng, ox, oy, oz);
        if (pedestal != null) {
            out.pedestalCandidates.add(pedestal);
        }
    }

    /** A random floor spot inside the mask, or null after a few misses. */
    private Vec3 openSpot(Random rng, int ox, int oy, int oz) {
        for (int attempt = 0; attempt < 8; attempt++) {
            int lx = 3 + rng.nextInt(ROOM - 6);
            int lz = 3 + rng.nextInt(ROOM - 6);
            if (inMask(lx, lz) && inMask(lx + 1, lz) && inMask(lx - 1, lz)
                    && inMask(lx, lz + 1) && inMask(lx, lz - 1)) {
                return new Vec3(ox + lx, oy + 1, oz + lz);
            }
        }
        return null;
    }

    // ------------------------------------------------------------ variants

    /** Chamfered corners: reads as a bastion chamber, not a box. */
    public static final class OctagonRoom extends RoomBuilder {
        @Override
        public int height(Random rng) {
            return 6 + rng.nextInt(4);
        }

        @Override
        public void build(BlockBuffer buf, Theme theme, Random rng,
                int ox, int oy, int oz, int h, Room room, GenResult out) {
            int chamfer = 4 + rng.nextInt(3);
            int edge = ROOM - 1;
            mask = (x, z) -> x + z >= chamfer && x + z <= 2 * edge - chamfer
                    && x - z <= edge - chamfer && z - x <= edge - chamfer;
            shell(buf, theme, rng, ox, oy, oz, h);
            clutter(buf, theme, rng, ox, oy, oz, 3 + rng.nextInt(5));
            features(buf, theme, rng, ox, oy, oz, h, out, 1 + rng.nextInt(2));
            if (rng.nextInt(3) == 0) {
                chest(buf, out, ox + ROOM / 2 + rng.nextInt(5) - 2, oy + 1,
                        oz + ROOM / 2 + rng.nextInt(5) - 2, false);
            }
            marks(out, rng, ox, oy, oz);
        }
    }

    /** A round chamber; the light ring makes it read as a rotunda. */
    public static final class CircleRoom extends RoomBuilder {
        @Override
        public int height(Random rng) {
            return 6 + rng.nextInt(5);
        }

        @Override
        public void build(BlockBuffer buf, Theme theme, Random rng,
                int ox, int oy, int oz, int h, Room room, GenResult out) {
            double radius = 9.4;
            int mid = ROOM / 2;
            mask = (x, z) -> (x - mid) * (x - mid) + (z - mid) * (z - mid) <= radius * radius;
            shell(buf, theme, rng, ox, oy, oz, h);
            // Ring of lights inlaid in the floor.
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI * 2 * i / 8;
                int lx = mid + (int) Math.round(Math.cos(angle) * 5);
                int lz = mid + (int) Math.round(Math.sin(angle) * 5);
                if (inMask(lx, lz)) {
                    buf.set(ox + lx, oy, oz + lz, theme.light);
                }
            }
            clutter(buf, theme, rng, ox, oy, oz, 3 + rng.nextInt(4));
            if (rng.nextInt(2) == 0) {
                chest(buf, out, ox + mid, oy + 1, oz + mid, false);
            }
            marks(out, rng, ox, oy, oz);
        }
    }

    /** Plus-shaped hall: four alcoves around a tight center. */
    public static final class CrossRoom extends RoomBuilder {
        @Override
        public int height(Random rng) {
            return 5 + rng.nextInt(4);
        }

        @Override
        public void build(BlockBuffer buf, Theme theme, Random rng,
                int ox, int oy, int oz, int h, Room room, GenResult out) {
            int arm = 5 + rng.nextInt(2); // arm half-width start (5 or 6)
            mask = (x, z) -> (x >= arm && x <= ROOM - 1 - arm) || (z >= arm && z <= ROOM - 1 - arm);
            shell(buf, theme, rng, ox, oy, oz, h);
            // A pillar guarding each inner corner of the crossing.
            for (int[] corner : new int[][] {{arm + 1, arm + 1}, {ROOM - 2 - arm, arm + 1},
                    {arm + 1, ROOM - 2 - arm}, {ROOM - 2 - arm, ROOM - 2 - arm}}) {
                if (inMask(corner[0], corner[1])) {
                    pillar(buf, theme, ox + corner[0], oy, oz + corner[1], h);
                }
            }
            clutter(buf, theme, rng, ox, oy, oz, 3 + rng.nextInt(4));
            if (rng.nextInt(3) == 0) {
                chest(buf, out, ox + ROOM / 2, oy + 1, oz + ROOM / 2, false);
            }
            marks(out, rng, ox, oy, oz);
        }
    }

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
            // Arena always pays out. On the floor — the rim has door-lane
            // gaps, so rim-height chests could float.
            chest(buf, out, ox + 4, oy + 1, oz + 5, false);
            chest(buf, out, ox + ROOM - 5, oy + 1, oz + ROOM - 6, false);
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
