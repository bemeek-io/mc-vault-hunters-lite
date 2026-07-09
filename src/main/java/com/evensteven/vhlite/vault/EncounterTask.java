package com.evensteven.vhlite.vault;

import com.evensteven.vhlite.vault.generation.Vec3;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * One-time encounters instead of an ambient mob drip: each generator spawn
 * marker is an ambush that triggers once when a runner comes near, spawning
 * a level-scaled group (with a chance of a beefy Elite). Clear a room and
 * it STAYS clear — exploring back through the vault is safe, and total mob
 * count stays bounded by the markers the generator placed.
 */
public final class EncounterTask extends BukkitRunnable {

    /** Tight radius: spawners pop one by one as you push into a room. */
    private static final double TRIGGER_RANGE_SQ = 8 * 8;
    private static final double ELITE_CHANCE = 0.15;

    private final VaultInstanceManager manager;
    private final ScalingService scaling;
    private final FileConfiguration config;

    public EncounterTask(VaultInstanceManager manager, ScalingService scaling, FileConfiguration config) {
        this.manager = manager;
        this.scaling = scaling;
        this.config = config;
    }

    @Override
    public void run() {
        for (VaultInstance instance : new ArrayList<>(manager.all())) {
            if (instance.state != VaultInstance.State.ACTIVE || instance.encountersLeft.isEmpty()) {
                continue;
            }
            tick(instance);
        }
    }

    private void tick(VaultInstance instance) {
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
        // Global safety cap so a party sprinting through can't stack armies.
        int cap = (int) Math.round((config.getInt("vault.mob-cap", 24)
                + config.getInt("vault.mob-cap-per-extra-player", 12)
                * (instance.blueprint().partySize() - 1))
                * instance.blueprint().modifierProduct(m -> m.mobCapMult));
        int alive = instance.countAmbientMobs();

        // At most one ambush per player per second — walking into a room
        // pops its spawners as a rolling skirmish, not one big blob.
        for (Player player : inside) {
            if (alive >= cap) {
                return;
            }
            Iterator<Vec3> markers = instance.encountersLeft.iterator();
            while (markers.hasNext()) {
                Vec3 marker = markers.next();
                if (player.getLocation().distanceSquared(instance.worldPos(marker)) <= TRIGGER_RANGE_SQ) {
                    markers.remove();
                    alive += spawnGroup(instance, marker, player);
                    break;
                }
            }
        }
    }

    private int spawnGroup(VaultInstance instance, Vec3 marker, Player trigger) {
        // Smaller per-spawner groups; rooms have 3-6 spawners now.
        int level = instance.blueprint().level();
        int size = 1 + level / 8 + instance.rng.nextInt(3);
        size = (int) Math.max(1, Math.round(size
                * instance.blueprint().modifierProduct(m -> m.mobCapMult)));
        int spawned = 0;
        for (int i = 0; i < size; i++) {
            // Cluster around the marker so the group reads as an ambush.
            Vec3 offset = marker.offset(instance.rng.nextInt(5) - 2, 0, instance.rng.nextInt(5) - 2);
            boolean elite = spawned == 0 && instance.rng.nextDouble() < ELITE_CHANCE
                    * (1.0 + level / 20.0);
            LivingEntity mob = instance.spawnEncounterMob(
                    safeSpot(instance, offset, marker), scaling, elite);
            if (mob != null) {
                spawned++;
            }
        }
        if (spawned > 0) {
            trigger.playSound(trigger.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 0.7f, 0.8f);
        }
        return spawned;
    }

    /** Falls back to the marker itself if the jittered spot is inside a block. */
    private Vec3 safeSpot(VaultInstance instance, Vec3 candidate, Vec3 marker) {
        return instance.blockAt(candidate).getType().isAir()
                && instance.blockAt(candidate.offset(0, 1, 0)).getType().isAir()
                ? candidate : marker;
    }
}
