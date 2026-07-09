package com.evensteven.vhlite.vault.generation;

/** An immutable block position relative to a vault instance's origin. */
public record Vec3(int x, int y, int z) {

    public Vec3 offset(int dx, int dy, int dz) {
        return new Vec3(x + dx, y + dy, z + dz);
    }
}
