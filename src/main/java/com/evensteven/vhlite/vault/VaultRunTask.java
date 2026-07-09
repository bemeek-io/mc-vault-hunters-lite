package com.evensteven.vhlite.vault;

import com.evensteven.vhlite.util.Text;
import com.evensteven.vhlite.vault.generation.Vec3;
import com.evensteven.vhlite.vault.objective.VaultObjective;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * One tick per second across all live runs: clock and bossbar, objective
 * proximity triggers (boss spawn, defend waves), exit-pad extraction by
 * position poll (no PlayerMoveEvent — that's the performance deal), and the
 * collapse at zero.
 */
public final class VaultRunTask extends BukkitRunnable {

    private final VaultInstanceManager manager;
    private final ScalingService scaling;
    private final FileConfiguration config;

    public VaultRunTask(VaultInstanceManager manager, ScalingService scaling, FileConfiguration config) {
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
            tick(instance);
        }
    }

    private void tick(VaultInstance instance) {
        instance.secondsLeft--;
        List<Player> inside = new ArrayList<>();
        for (Player player : instance.players()) {
            if (instance.contains(player.getLocation())) {
                inside.add(player);
                // Fog-of-war: standing in a room reveals it on the party map.
                instance.markVisited(instance.cellOf(player.getLocation()));
            }
        }

        updateBossBar(instance);
        for (Player player : inside) {
            player.sendActionBar(Text.c(progressLine(instance)));
        }

        if (instance.exitOpen) {
            Location exit = instance.worldPos(instance.gen.exitCenter).add(0, 1.5, 0);
            instance.world().spawnParticle(Particle.PORTAL, exit, 25, 1.2, 1.0, 1.2, 0.05);
            for (Player player : inside) {
                if (!instance.deadSpectators.contains(player.getUniqueId())
                        && instance.onExitPad(player)) {
                    manager.extract(player, instance, instance.objectiveDone
                            ? "§aYou made it out with the goods."
                            : "§eYou slipped out of the vault.");
                }
            }
        }

        switch (instance.blueprint().objective()) {
            case BOSS -> maybeSpawnBoss(instance, inside);
            case DEFEND -> tickDefend(instance, inside);
            default -> {
            }
        }

        if (instance.secondsLeft == 60) {
            for (Player player : instance.players()) {
                player.sendMessage(Text.c("§4The vault begins to crumble — one minute!"));
                player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 0.5f);
            }
        }
        if (instance.secondsLeft <= 0) {
            manager.timeout(instance);
        }
    }

    private void updateBossBar(VaultInstance instance) {
        if (instance.bossBar == null) {
            return;
        }
        float progress = Math.max(0f, Math.min(1f, instance.secondsLeft / (float) instance.totalSeconds));
        instance.bossBar.progress(progress);
        instance.bossBar.color(instance.secondsLeft <= 60 ? BossBar.Color.RED
                : instance.objectiveDone ? BossBar.Color.GREEN : BossBar.Color.PURPLE);
    }

    private String progressLine(VaultInstance instance) {
        int mins = Math.max(0, instance.secondsLeft) / 60;
        int secs = Math.max(0, instance.secondsLeft) % 60;
        String time = String.format("§7%d:%02d", mins, secs);
        if (instance.objectiveDone) {
            return "§aExit open! §7Step on the glowing pad. " + time;
        }
        return switch (instance.blueprint().objective()) {
            case ARTIFACTS -> "§dArtifacts: " + (instance.objectiveTarget - instance.artifactsLeft.size())
                    + "/" + instance.objectiveTarget + " " + time;
            case TREASURE -> "§6Marked chests: " + (instance.objectiveTarget - instance.treasureLeft.size())
                    + "/" + instance.objectiveTarget + " " + time;
            case DEFEND -> instance.defendStarted
                    ? "§bWave " + instance.wave + "/3 — mobs left: " + instance.waveMobs.size() + " " + time
                    : "§bFind and hold the beacon. " + time;
            case BOSS -> instance.bossId == null
                    ? "§cThe guardian stirs somewhere below... " + time
                    : "§cKill the guardian! " + time;
            case ESCAPE -> "§eGet to the exit pad! " + time;
        };
    }

    // ------------------------------------------------------------------ boss

    private void maybeSpawnBoss(VaultInstance instance, List<Player> inside) {
        if (instance.bossId != null || instance.gen.bossSpawn == null) {
            return;
        }
        Location spawn = instance.worldPos(instance.gen.bossSpawn);
        for (Player player : inside) {
            if (player.getLocation().distanceSquared(spawn) < 20 * 20) {
                instance.spawnBoss(scaling);
                for (Player member : instance.players()) {
                    member.sendMessage(Text.c(instance.blueprint().theme().bossName
                            + " §7awakens in the arena!"));
                    member.playSound(member.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.3f);
                }
                return;
            }
        }
    }

    // ---------------------------------------------------------------- defend

    private void tickDefend(VaultInstance instance, List<Player> inside) {
        if (instance.objectiveDone || instance.gen.defendPoint == null) {
            return;
        }
        Location point = instance.worldPos(instance.gen.defendPoint);
        if (!instance.defendStarted) {
            for (Player player : inside) {
                if (player.getLocation().distanceSquared(point) < 6 * 6) {
                    instance.defendStarted = true;
                    instance.wave = 1;
                    spawnWave(instance);
                    break;
                }
            }
            return;
        }
        // Between-waves countdown / wave completion. Stragglers that wandered
        // (or teleported — shulkers, endermen) get leashed back to the arena
        // so a hidden mob can never soft-lock the wave.
        instance.waveMobs.removeIf(id -> {
            org.bukkit.entity.Entity entity = org.bukkit.Bukkit.getEntity(id);
            if (entity == null || entity.isDead()
                    || !instance.world().equals(entity.getWorld())) {
                return true;
            }
            if (entity.getLocation().distanceSquared(point) > 20 * 20) {
                entity.teleport(ringSpot(instance, point.clone()));
            }
            return false;
        });
        if (!instance.waveMobs.isEmpty()) {
            return;
        }
        if (instance.nextWaveInSeconds < 0) {
            if (instance.wave >= 3) {
                manager.completeObjective(instance);
                return;
            }
            instance.nextWaveInSeconds = 5;
            for (Player player : instance.players()) {
                player.sendMessage(Text.c("§bWave " + instance.wave + " cleared. Next in 5s..."));
            }
            return;
        }
        if (--instance.nextWaveInSeconds == 0) {
            instance.nextWaveInSeconds = -1;
            instance.wave++;
            spawnWave(instance);
        }
    }

    private void spawnWave(VaultInstance instance) {
        int count = 4 + instance.wave * 2 + instance.blueprint().level() / 5;
        Vec3 point = instance.gen.defendPoint;
        // Spawn in a ring around the beacon, INSIDE the arena — never at far
        // markers where mobs could sit unreachable and stall the wave.
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI * i) / count + instance.rng.nextDouble() * 0.5;
            double radius = 4 + instance.rng.nextDouble() * 2.5;
            Vec3 spot = new Vec3(point.x() + (int) Math.round(Math.cos(angle) * radius),
                    point.y(), point.z() + (int) Math.round(Math.sin(angle) * radius));
            instance.spawnThemedMob(spot, scaling, true);
        }
        for (Player player : instance.players()) {
            player.playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 0.7f, 1f);
            player.sendMessage(Text.c("§bWave " + instance.wave + " §7— defend the beacon!"));
        }
    }

    /** A random point on the spawn ring, for leashing runaway wave mobs back. */
    private Location ringSpot(VaultInstance instance, Location point) {
        double angle = instance.rng.nextDouble() * 2 * Math.PI;
        return point.add(Math.cos(angle) * 5, 0, Math.sin(angle) * 5);
    }
}
