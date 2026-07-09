package com.evensteven.vhlite.vault;

import com.evensteven.vhlite.util.Keys;
import com.evensteven.vhlite.vault.generation.GenResult;
import com.evensteven.vhlite.vault.generation.Vec3;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * One live vault run: who's inside, where they go back to, the clock, and
 * the objective's progress. Behavior lives in VaultInstanceManager and
 * VaultRunTask; this is the state they share.
 */
public final class VaultInstance {

    public enum State { GENERATING, ACTIVE, CLOSED }

    private final UUID id = UUID.randomUUID();
    private final VaultBlueprint blueprint;
    private final World world;
    public final Random rng;

    public State state = State.GENERATING;
    public GenResult gen;
    public List<Chunk> pinnedChunks = new ArrayList<>();

    private final List<UUID> members = new ArrayList<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    public final Set<UUID> deadSpectators = new HashSet<>();

    public int totalSeconds;
    public int secondsLeft;
    public boolean exitOpen;
    public boolean objectiveDone;
    public BossBar bossBar;

    // Objective progress.
    public int objectiveTarget;
    public final Set<Vec3> artifactsLeft = new HashSet<>();
    public final Set<Vec3> treasureLeft = new HashSet<>();
    public final Set<Vec3> chestsOpened = new HashSet<>();
    public UUID bossId;
    public boolean defendStarted;
    public int wave;
    public final Set<UUID> waveMobs = new HashSet<>();
    public int nextWaveInSeconds = -1;

    public VaultInstance(VaultBlueprint blueprint, World world) {
        this.blueprint = blueprint;
        this.world = world;
        this.rng = new Random(blueprint.seed() ^ 0x5DEECE66DL);
    }

    public UUID id() {
        return id;
    }

    public VaultBlueprint blueprint() {
        return blueprint;
    }

    public World world() {
        return world;
    }

    // -------------------------------------------------------------- members

    public void addMember(Player player) {
        members.add(player.getUniqueId());
        returnLocations.put(player.getUniqueId(), player.getLocation().clone());
    }

    public void removeMember(UUID playerId) {
        members.remove(playerId);
        deadSpectators.remove(playerId);
    }

    public boolean isMember(UUID playerId) {
        return members.contains(playerId);
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }

    /** Online members, in or out of the vault. */
    public List<Player> players() {
        List<Player> out = new ArrayList<>();
        for (UUID member : members) {
            Player player = Bukkit.getPlayer(member);
            if (player != null && player.isOnline()) {
                out.add(player);
            }
        }
        return out;
    }

    public Location returnLocation(UUID playerId) {
        Location loc = returnLocations.get(playerId);
        return loc != null ? loc : world.getSpawnLocation();
    }

    // ------------------------------------------------------------ geometry

    public Location worldPos(Vec3 rel) {
        return new Location(world,
                blueprint.originX() + rel.x() + 0.5, rel.y(), blueprint.originZ() + rel.z() + 0.5);
    }

    /** Block position (no centering) for block edits. */
    public org.bukkit.block.Block blockAt(Vec3 rel) {
        return world.getBlockAt(blueprint.originX() + rel.x(), rel.y(), blueprint.originZ() + rel.z());
    }

    public Vec3 relativeOf(Location loc) {
        return new Vec3(loc.getBlockX() - blueprint.originX(), loc.getBlockY(),
                loc.getBlockZ() - blueprint.originZ());
    }

    public boolean contains(Location loc) {
        if (!world.equals(loc.getWorld()) || gen == null) {
            return false;
        }
        int x = loc.getBlockX() - blueprint.originX();
        int z = loc.getBlockZ() - blueprint.originZ();
        return x >= gen.buffer.minX() - 2 && x <= gen.buffer.maxX() + 2
                && z >= gen.buffer.minZ() - 2 && z <= gen.buffer.maxZ() + 2;
    }

    public boolean onExitPad(Player player) {
        if (!exitOpen || gen == null) {
            return false;
        }
        Location loc = player.getLocation();
        Vec3 exit = gen.exitCenter;
        int x = loc.getBlockX() - blueprint.originX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ() - blueprint.originZ();
        return Math.abs(x - exit.x()) <= 1 && Math.abs(z - exit.z()) <= 1
                && y >= exit.y() && y <= exit.y() + 3;
    }

    public void openExit() {
        if (exitOpen) {
            return;
        }
        exitOpen = true;
        Vec3 exit = gen.exitCenter;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                blockAt(exit.offset(dx, 0, dz)).setType(Material.AMETHYST_BLOCK, false);
            }
        }
    }

    // ----------------------------------------------------------------- mobs

    public LivingEntity spawnThemedMob(Vec3 rel, ScalingService scaling, boolean waveMob) {
        EntityType type = blueprint.theme().pickMob(rng);
        return spawn(type, rel, scaling, false, waveMob);
    }

    public LivingEntity spawnBoss(ScalingService scaling) {
        LivingEntity boss = spawn(blueprint.theme().bossType, gen.bossSpawn, scaling, true, false);
        if (boss != null) {
            boss.customName(com.evensteven.vhlite.util.Text.c(blueprint.theme().bossName));
            boss.setCustomNameVisible(true);
            boss.setGlowing(true);
            bossId = boss.getUniqueId();
        }
        return boss;
    }

    private LivingEntity spawn(EntityType type, Vec3 rel, ScalingService scaling,
            boolean boss, boolean waveMob) {
        Location loc = worldPos(rel);
        Entity entity = world.spawnEntity(loc, type);
        if (!(entity instanceof LivingEntity mob)) {
            entity.remove();
            return null;
        }
        mob.getPersistentDataContainer().set(Keys.INSTANCE_ID, PersistentDataType.STRING, id.toString());
        if (waveMob) {
            mob.getPersistentDataContainer().set(Keys.WAVE_MOB, PersistentDataType.INTEGER, 1);
            waveMobs.add(mob.getUniqueId());
        }
        mob.setPersistent(false);
        mob.setRemoveWhenFarAway(false);
        if (mob instanceof org.bukkit.entity.PiglinAbstract piglin) {
            piglin.setImmuneToZombification(true);
        }
        if (mob instanceof org.bukkit.entity.Hoglin hoglin) {
            hoglin.setImmuneToZombification(true);
        }
        if (mob instanceof org.bukkit.entity.Phantom phantom) {
            phantom.setSize(1);
        }
        scaling.scaleMob(mob, blueprint, boss);
        return mob;
    }

    public boolean ownsMob(Entity entity) {
        String tag = entity.getPersistentDataContainer().get(Keys.INSTANCE_ID, PersistentDataType.STRING);
        return id.toString().equals(tag);
    }

    /** Alive ambient mobs (wave mobs are budgeted separately). */
    public int countAmbientMobs() {
        int count = 0;
        for (Chunk chunk : pinnedChunks) {
            for (Entity entity : chunk.getEntities()) {
                if (entity instanceof LivingEntity && !(entity instanceof Player) && ownsMob(entity)
                        && !entity.getPersistentDataContainer().has(Keys.WAVE_MOB, PersistentDataType.INTEGER)
                        && !((LivingEntity) entity).isDead()) {
                    count++;
                }
            }
        }
        return count;
    }

    public void killOwnedMobs() {
        for (Chunk chunk : pinnedChunks) {
            for (Entity entity : chunk.getEntities()) {
                if (!(entity instanceof Player) && ownsMob(entity)) {
                    entity.remove();
                }
            }
        }
    }
}
