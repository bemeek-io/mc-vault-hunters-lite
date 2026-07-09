package com.evensteven.vhlite.vault;

import com.evensteven.vhlite.util.Keys;
import com.evensteven.vhlite.vault.generation.GenResult;
import com.evensteven.vhlite.vault.generation.RoomGraph;
import com.evensteven.vhlite.vault.generation.VaultGenerator;
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

    // Exploration + encounters.
    /** Rooms any party member has stood in — what the vault map reveals. */
    public final Set<RoomGraph.Cell> visitedCells = new HashSet<>();
    /** Bumped whenever visitedCells grows, so the map renderer redraws. */
    public int mapVersion;
    /** Spawn markers that haven't triggered their one-time encounter yet. */
    public final Set<Vec3> encountersLeft = new HashSet<>();
    public org.bukkit.map.MapView mapView;

    // Objective progress.
    public int objectiveTarget;
    public final Set<Vec3> artifactsLeft = new HashSet<>();
    public final Set<Vec3> treasureLeft = new HashSet<>();
    public final Set<Vec3> chestsOpened = new HashSet<>();
    /** Vault Gold (copper units) rolled for each chest, paid on first open. */
    public final Map<Vec3, Long> chestGold = new HashMap<>();
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

    /** Which layout cell this location falls in, or null if between rooms. */
    public RoomGraph.Cell cellOf(Location loc) {
        if (gen == null || gen.graph == null) {
            return null;
        }
        int relX = loc.getBlockX() - blueprint.originX();
        int relZ = loc.getBlockZ() - blueprint.originZ();
        int floor = Math.floorDiv(loc.getBlockY() - VaultGenerator.BASE_Y, VaultGenerator.FLOOR_H);
        RoomGraph.Cell cell = new RoomGraph.Cell(Math.floorDiv(relX, VaultGenerator.CELL),
                Math.floorDiv(relZ, VaultGenerator.CELL), floor);
        return gen.graph.get(cell) != null ? cell : null;
    }

    public void markVisited(RoomGraph.Cell cell) {
        if (cell != null && visitedCells.add(cell)) {
            mapVersion++;
        }
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

    /**
     * True if this generated block is part of the vault's outer skin: any
     * face touching a position the generator never wrote (the void beyond).
     * Player-placed blocks (no buffer entry at their own position) are never
     * shell — players may always reclaim their own blocks.
     */
    public boolean isShellBlock(Vec3 rel) {
        if (gen == null || gen.buffer.get(rel.x(), rel.y(), rel.z()) == null) {
            return false;
        }
        return gen.buffer.get(rel.x() + 1, rel.y(), rel.z()) == null
                || gen.buffer.get(rel.x() - 1, rel.y(), rel.z()) == null
                || gen.buffer.get(rel.x(), rel.y() + 1, rel.z()) == null
                || gen.buffer.get(rel.x(), rel.y() - 1, rel.z()) == null
                || gen.buffer.get(rel.x(), rel.y(), rel.z() + 1) == null
                || gen.buffer.get(rel.x(), rel.y(), rel.z() - 1) == null;
    }

    /** The exit pad and the defend beacon must survive enthusiastic miners. */
    public boolean isProtectedFixture(Vec3 rel) {
        if (gen == null) {
            return false;
        }
        Vec3 exit = gen.exitCenter;
        if (exit != null && Math.abs(rel.x() - exit.x()) <= 2 && Math.abs(rel.z() - exit.z()) <= 2
                && rel.y() >= exit.y() && rel.y() <= exit.y() + 2) {
            return true;
        }
        Vec3 defend = gen.defendPoint;
        return blueprint.objective() == com.evensteven.vhlite.vault.objective.VaultObjective.DEFEND
                && defend != null && Math.abs(rel.x() - defend.x()) <= 1
                && Math.abs(rel.z() - defend.z()) <= 1
                && rel.y() >= defend.y() - 1 && rel.y() <= defend.y() + 1;
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

    public LivingEntity spawnThemedMob(Vec3 rel, ScalingService scaling, boolean waveMob, boolean ranged) {
        EntityType type = ranged ? blueprint.theme().pickRanged(rng) : blueprint.theme().pickMelee(rng);
        return spawn(type, rel, scaling, false, waveMob, false);
    }

    public LivingEntity spawnEncounterMob(Vec3 rel, ScalingService scaling, boolean elite, boolean ranged) {
        EntityType type = ranged ? blueprint.theme().pickRanged(rng) : blueprint.theme().pickMelee(rng);
        return spawn(type, rel, scaling, false, false, elite);
    }

    /** Between an Elite and a boss: rare, unmistakable, guaranteed drops. */
    public LivingEntity spawnChampion(Vec3 rel, ScalingService scaling) {
        EntityType type = blueprint.theme().pickMelee(rng); // never a bow-sniper champion
        LivingEntity champion = spawn(type, rel, scaling, false, false, false);
        if (champion != null) {
            champion.getPersistentDataContainer().set(Keys.MOB_CHAMPION, PersistentDataType.INTEGER, 1);
            champion.setGlowing(true);
            scaling.tweakMob(champion, 3.2, 1.6);
            MobNameplates.applyChampion(champion, blueprint.level());
        }
        return champion;
    }

    public boolean isChampion(Entity entity) {
        return entity.getPersistentDataContainer().has(Keys.MOB_CHAMPION, PersistentDataType.INTEGER);
    }

    public LivingEntity spawnBoss(ScalingService scaling) {
        LivingEntity boss = spawn(blueprint.theme().bossType, gen.bossSpawn, scaling, true, false, false);
        if (boss != null) {
            boss.setGlowing(true);
            bossId = boss.getUniqueId();
        }
        return boss;
    }

    private LivingEntity spawn(EntityType type, Vec3 rel, ScalingService scaling,
            boolean boss, boolean waveMob, boolean elite) {
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
            mob.setGlowing(true); // wave mobs must be findable, always
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
        if (mob instanceof org.bukkit.entity.Zombie && mob.getEquipment() != null) {
            // Melee variety: nothing, a sword, or (per request) a tool —
            // zombie-family AI attacks with whatever's in hand regardless.
            org.bukkit.inventory.ItemStack weapon = rollMeleeWeapon();
            if (weapon != null) {
                mob.getEquipment().setItemInMainHand(weapon);
                mob.getEquipment().setItemInMainHandDropChance(0f);
            }
        }
        scaling.scaleMob(mob, blueprint, boss);
        if (elite) {
            var health = mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (health != null) {
                health.removeModifier(Keys.MOB_ELITE);
                health.addModifier(new org.bukkit.attribute.AttributeModifier(Keys.MOB_ELITE, 1.0,
                        org.bukkit.attribute.AttributeModifier.Operation.MULTIPLY_SCALAR_1));
                mob.setHealth(health.getValue());
            }
        }
        MobNameplates.apply(mob, blueprint.level(), elite, boss ? blueprint.theme().bossName : null);
        return mob;
    }

    private static final org.bukkit.Material[] MELEE_FLAVOR_TOOLS = {
            org.bukkit.Material.IRON_PICKAXE, org.bukkit.Material.IRON_AXE,
            org.bukkit.Material.IRON_SHOVEL, org.bukkit.Material.IRON_HOE};

    /** 15% bare-handed, 55% a level-tiered sword, 30% a random tool. */
    private org.bukkit.inventory.ItemStack rollMeleeWeapon() {
        int roll = rng.nextInt(100);
        if (roll < 15) {
            return null;
        }
        if (roll < 70) {
            org.bukkit.Material tier = blueprint.level() < 6 ? org.bukkit.Material.WOODEN_SWORD
                    : blueprint.level() < 14 ? org.bukkit.Material.STONE_SWORD : org.bukkit.Material.IRON_SWORD;
            return new org.bukkit.inventory.ItemStack(tier);
        }
        return new org.bukkit.inventory.ItemStack(MELEE_FLAVOR_TOOLS[rng.nextInt(MELEE_FLAVOR_TOOLS.length)]);
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
