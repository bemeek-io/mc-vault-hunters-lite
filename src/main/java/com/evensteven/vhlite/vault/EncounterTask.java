package com.evensteven.vhlite.vault;

import com.evensteven.vhlite.util.Text;
import com.evensteven.vhlite.vault.generation.Vec3;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * One-time encounters instead of an ambient mob drip: each generator spawn
 * marker is an ambush that triggers once when a runner comes near, spawning
 * a level-scaled group. Clear a room and it STAYS clear — exploring back
 * through the vault is safe, and total mob count stays bounded by the
 * markers the generator placed.
 *
 * Every group rolls its own archetype: a horde of many weak bodies, a
 * balanced pack (which may include one rare Elite leader), or — rarest of
 * all — a Champion, a named unit well above Elite strength that always
 * drops something. Ranged members are deliberately capped at ~1 in 3
 * groups so a room isn't a wall of arrows.
 */
public final class EncounterTask extends BukkitRunnable {

    /** Tight radius: spawners pop one by one as you push into a room. */
    private static final double TRIGGER_RANGE_SQ = 8 * 8;
    private static final double ELITE_CHANCE = 0.08;
    private static final double CHAMPION_CHANCE = 0.06;
    private static final double RANGED_GROUP_CHANCE = 0.32;

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
                    alive += spawnEncounter(instance, marker, player);
                    break;
                }
            }
        }
    }

    private int spawnEncounter(VaultInstance instance, Vec3 marker, Player trigger) {
        int level = instance.blueprint().level();
        if (instance.rng.nextDouble() < CHAMPION_CHANCE * (1.0 + level / 25.0)) {
            return spawnChampionEncounter(instance, marker, trigger);
        }
        return spawnGroup(instance, marker, trigger);
    }

    private int spawnChampionEncounter(VaultInstance instance, Vec3 marker, Player trigger) {
        LivingEntity champion = instance.spawnChampion(marker, scaling);
        int spawned = champion != null ? 1 : 0;
        // A couple of weak escorts, never ranged — the champion is the threat.
        int escorts = instance.rng.nextInt(3);
        for (int i = 0; i < escorts; i++) {
            Vec3 offset = marker.offset(instance.rng.nextInt(5) - 2, 0, instance.rng.nextInt(5) - 2);
            LivingEntity mob = instance.spawnEncounterMob(safeSpot(instance, offset, marker), scaling, false, false);
            if (mob != null) {
                scaling.tweakMob(mob, 0.7, 0.8);
                MobNameplates.refresh(mob);
                spawned++;
            }
        }
        if (spawned > 0) {
            telegraph(trigger, "§d§lA Champion Stirs...", Sound.ENTITY_RAVAGER_ROAR);
        }
        return spawned;
    }

    private int spawnGroup(VaultInstance instance, Vec3 marker, Player trigger) {
        int level = instance.blueprint().level();
        int size = 1 + level / 8 + instance.rng.nextInt(3);
        boolean hordeArchetype = instance.rng.nextDouble() < 0.35;
        double hpMult = 1.0;
        double dmgMult = 1.0;
        if (hordeArchetype) {
            // Horde: double the bodies, each much weaker. Chaff, not a threat.
            size = size * 2 + 1;
            hpMult = 0.55;
            dmgMult = 0.75;
        }
        size = (int) Math.max(1, Math.round(size * instance.blueprint().modifierProduct(m -> m.mobCapMult)));

        // At most one ranged member, and only in ~1/3 of groups — the rest
        // are melee-only so a room isn't a knockback gauntlet.
        boolean hasRanged = instance.rng.nextDouble() < RANGED_GROUP_CHANCE;
        int rangedIndex = hasRanged ? instance.rng.nextInt(size) : -1;

        int spawned = 0;
        for (int i = 0; i < size; i++) {
            // Cluster around the marker so the group reads as an ambush.
            Vec3 offset = marker.offset(instance.rng.nextInt(5) - 2, 0, instance.rng.nextInt(5) - 2);
            // Elites are rare and only lead a balanced pack — hordes stay chaff.
            boolean elite = !hordeArchetype && spawned == 0
                    && instance.rng.nextDouble() < ELITE_CHANCE * (1.0 + level / 20.0);
            boolean ranged = i == rangedIndex;
            LivingEntity mob = instance.spawnEncounterMob(
                    safeSpot(instance, offset, marker), scaling, elite, ranged);
            if (mob != null) {
                if (hpMult != 1.0 || dmgMult != 1.0) {
                    scaling.tweakMob(mob, hpMult, dmgMult);
                    MobNameplates.refresh(mob);
                }
                spawned++;
            }
        }
        if (spawned > 0) {
            telegraph(trigger, hordeArchetype ? "§cA horde closes in!" : "§6Ambush!",
                    Sound.ENTITY_EVOKER_PREPARE_SUMMON);
        }
        return spawned;
    }

    /** A brief, unmistakable "this was a scripted ambush" cue. */
    private void telegraph(Player trigger, String titleText, Sound sound) {
        trigger.showTitle(Title.title(Text.c(titleText), Text.c(""),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(700), Duration.ofMillis(300))));
        trigger.playSound(trigger.getLocation(), sound, 0.7f, 0.9f);
    }

    /** Falls back to the marker itself if the jittered spot is inside a block. */
    private Vec3 safeSpot(VaultInstance instance, Vec3 candidate, Vec3 marker) {
        return instance.blockAt(candidate).getType().isAir()
                && instance.blockAt(candidate.offset(0, 1, 0)).getType().isAir()
                ? candidate : marker;
    }
}
