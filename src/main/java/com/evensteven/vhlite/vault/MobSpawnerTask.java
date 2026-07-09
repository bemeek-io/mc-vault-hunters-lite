package com.evensteven.vhlite.vault;

import com.evensteven.vhlite.vault.generation.Vec3;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * The only source of ambient vault mobs (natural spawning is off in the
 * vault world). Every 2 seconds each run tops up toward its cap, spawning
 * from generator markers near — but not on — a random runner. A hard cap
 * per instance is the anti-lag contract.
 */
public final class MobSpawnerTask extends BukkitRunnable {

    private static final int NEAR = 24;
    private static final int TOO_CLOSE = 7;
    private static final int PER_CYCLE = 3;

    private final VaultInstanceManager manager;
    private final ScalingService scaling;
    private final FileConfiguration config;

    public MobSpawnerTask(VaultInstanceManager manager, ScalingService scaling, FileConfiguration config) {
        this.manager = manager;
        this.scaling = scaling;
        this.config = config;
    }

    @Override
    public void run() {
        for (VaultInstance instance : new ArrayList<>(manager.all())) {
            if (instance.state != VaultInstance.State.ACTIVE) {
                continue;
            }
            spawnFor(instance);
        }
    }

    private void spawnFor(VaultInstance instance) {
        int cap = (int) Math.round((config.getInt("vault.mob-cap", 24)
                + config.getInt("vault.mob-cap-per-extra-player", 12) * (instance.blueprint().partySize() - 1))
                * instance.blueprint().modifierProduct(m -> m.mobCapMult));
        int alive = instance.countAmbientMobs();
        if (alive >= cap) {
            return;
        }

        List<Player> inside = new ArrayList<>();
        for (Player player : instance.players()) {
            if (instance.contains(player.getLocation())
                    && !instance.deadSpectators.contains(player.getUniqueId())) {
                inside.add(player);
            }
        }
        if (inside.isEmpty()) {
            return;
        }

        int budget = Math.min(PER_CYCLE, cap - alive);
        List<Vec3> markers = instance.gen.mobSpawns;
        if (markers.isEmpty()) {
            return;
        }
        for (int attempt = 0; attempt < budget * 4 && budget > 0; attempt++) {
            Vec3 marker = markers.get(instance.rng.nextInt(markers.size()));
            Location spot = instance.worldPos(marker);
            Player anchor = inside.get(instance.rng.nextInt(inside.size()));
            double d2 = anchor.getLocation().distanceSquared(spot);
            if (d2 > NEAR * NEAR || d2 < TOO_CLOSE * TOO_CLOSE) {
                continue;
            }
            instance.spawnThemedMob(marker, scaling, false);
            budget--;
        }
    }
}
