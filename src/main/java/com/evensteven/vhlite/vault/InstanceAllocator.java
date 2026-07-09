package com.evensteven.vhlite.vault;

/**
 * Hands out instance slots on a 64x64 grid of region-aligned parcels.
 * Slot i sits at (slotX * spacing, slotZ * spacing); the vault itself is
 * generated a comfortable margin inside so multi-floor branches that grow
 * into negative cells never cross a region boundary.
 */
public final class InstanceAllocator {

    /** Margin from the slot's region corner to the vault origin. */
    public static final int MARGIN = 512;
    private static final int GRID = 64;

    private final InstanceStore store;
    private final int spacing;

    public InstanceAllocator(InstanceStore store, int spacing) {
        this.store = store;
        this.spacing = spacing;
    }

    public static int slotX(int slot) {
        return Math.floorMod(slot, GRID);
    }

    public static int slotZ(int slot) {
        return Math.floorMod(slot / GRID, GRID);
    }

    /** Claims the next slot (persisted immediately for crash safety). */
    public int claim() {
        return store.nextSlot();
    }

    public int originX(int slot) {
        return slotX(slot) * spacing + MARGIN;
    }

    public int originZ(int slot) {
        return slotZ(slot) * spacing + MARGIN;
    }
}
