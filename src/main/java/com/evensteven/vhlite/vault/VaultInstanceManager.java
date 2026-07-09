package com.evensteven.vhlite.vault;

import com.evensteven.vhlite.item.VhItemType;
import com.evensteven.vhlite.item.VhItems;
import com.evensteven.vhlite.player.LevelService;
import com.evensteven.vhlite.player.PartyService;
import com.evensteven.vhlite.player.PlayerProfile;
import com.evensteven.vhlite.player.ProfileStore;
import com.evensteven.vhlite.skills.StatType;
import com.evensteven.vhlite.spirit.SpiritStore;
import com.evensteven.vhlite.util.Text;
import com.evensteven.vhlite.vault.generation.GenResult;
import com.evensteven.vhlite.vault.generation.LayoutType;
import com.evensteven.vhlite.vault.generation.LootRoller;
import com.evensteven.vhlite.vault.generation.Theme;
import com.evensteven.vhlite.vault.generation.ThemeRegistry;
import com.evensteven.vhlite.vault.generation.VaultGenerator;
import com.evensteven.vhlite.vault.generation.BlockBufferApplier;
import com.evensteven.vhlite.vault.generation.Vec3;
import com.evensteven.vhlite.vault.modifier.VaultModifier;
import com.evensteven.vhlite.vault.objective.VaultObjective;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Container;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Owns every live vault run: rolls blueprints, drives the async generate ->
 * paced apply -> teleport pipeline, routes world events to the right
 * instance, and settles players back out (completion, abandon, timeout,
 * quit, crash).
 */
public final class VaultInstanceManager implements Listener {

    private final Plugin plugin;
    private final FileConfiguration config;
    private final VaultWorldService worlds;
    private final InstanceAllocator allocator;
    private final InstanceStore store;
    private final ThemeRegistry themes;
    private final VaultGenerator generator;
    private final BlockBufferApplier applier;
    private final LootRoller lootRoller = new LootRoller();
    private final ScalingService scaling;
    private final ProfileStore profiles;
    private final LevelService levels;
    private final PartyService parties;
    private final SpiritStore spirits;
    private final Random random = new Random();

    private final Map<UUID, VaultInstance> instances = new HashMap<>();
    private final Map<UUID, VaultInstance> byPlayer = new HashMap<>();

    public VaultInstanceManager(Plugin plugin, FileConfiguration config, VaultWorldService worlds,
            InstanceAllocator allocator, InstanceStore store, ThemeRegistry themes,
            VaultGenerator generator, BlockBufferApplier applier, ScalingService scaling,
            ProfileStore profiles, LevelService levels, PartyService parties, SpiritStore spirits) {
        this.plugin = plugin;
        this.config = config;
        this.worlds = worlds;
        this.allocator = allocator;
        this.store = store;
        this.themes = themes;
        this.generator = generator;
        this.applier = applier;
        this.scaling = scaling;
        this.profiles = profiles;
        this.levels = levels;
        this.parties = parties;
        this.spirits = spirits;
    }

    // ------------------------------------------------------------ run start

    public VaultInstance instanceOf(Player player) {
        return byPlayer.get(player.getUniqueId());
    }

    public java.util.Collection<VaultInstance> all() {
        return instances.values();
    }

    /**
     * Rolls a blueprint and launches the run for the host's whole party.
     * seedOverride is the testgen hook; normal runs pass null.
     */
    public boolean startRun(Player host, int level, List<VaultModifier> forced, Long seedOverride) {
        if (instanceOf(host) != null) {
            host.sendMessage(Text.c("§cYou are already in a vault."));
            return false;
        }
        List<Player> members = new ArrayList<>();
        for (Player member : parties.membersOf(host)) {
            if (instanceOf(member) == null) {
                members.add(member);
            }
        }
        long seed = seedOverride != null ? seedOverride : random.nextLong();
        Random roll = new Random(seed * 31 + 7); // distinct stream from the generator's
        Theme theme = themes.roll(level, roll);
        LayoutType layout = LayoutType.roll(roll);
        VaultObjective objective = VaultObjective.roll(roll);
        List<VaultModifier> modifiers = new ArrayList<>(forced);
        while (modifiers.size() < 2 && roll.nextDouble() < 0.40) {
            VaultModifier extra = VaultModifier.values()[roll.nextInt(VaultModifier.values().length)];
            if (!modifiers.contains(extra)) {
                modifiers.add(extra);
            }
        }

        int slot = allocator.claim();
        VaultBlueprint bp = new VaultBlueprint(UUID.randomUUID(), seed, level, members.size(),
                theme, layout, objective, List.copyOf(modifiers), slot,
                allocator.originX(slot), allocator.originZ(slot));
        VaultInstance instance = new VaultInstance(bp, worlds.world());
        for (Player member : members) {
            instance.addMember(member);
            byPlayer.put(member.getUniqueId(), instance);
            member.showTitle(Title.title(Text.c("§5The vault is forming..."),
                    Text.c("§7hold tight")));
            member.playSound(member.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.6f, 1.4f);
        }
        instances.put(instance.id(), instance);
        store.recordActive(instance);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            GenResult gen = generator.generate(bp);
            Bukkit.getScheduler().runTask(plugin, () -> beginApply(instance, gen));
        });
        return true;
    }

    private void beginApply(VaultInstance instance, GenResult gen) {
        instance.gen = gen;
        instance.encountersLeft.addAll(gen.mobSpawns);
        switch (instance.blueprint().objective()) {
            case ARTIFACTS -> {
                instance.artifactsLeft.addAll(gen.artifacts);
                instance.objectiveTarget = gen.artifacts.size();
            }
            case TREASURE -> {
                for (GenResult.ChestSpot spot : gen.chests) {
                    if (spot.treasure()) {
                        instance.treasureLeft.add(spot.pos());
                    }
                }
                instance.objectiveTarget = instance.treasureLeft.size();
            }
            case DEFEND -> instance.objectiveTarget = 3;
            case BOSS, ESCAPE -> instance.objectiveTarget = 1;
        }
        VaultBlueprint bp = instance.blueprint();
        applier.apply(instance.world(), gen.buffer, bp.originX(), bp.originZ(),
                        () -> fillChests(instance))
                .thenAccept(chunks -> {
                    instance.pinnedChunks = chunks;
                    activate(instance);
                });
    }

    private void fillChests(VaultInstance instance) {
        VaultBlueprint bp = instance.blueprint();
        double lootMult = bp.modifierProduct(m -> m.lootMult)
                * (1.0 + (bp.partySize() - 1) * config.getDouble("vault.loot-per-extra-player", 0.5));
        double fortune = 0.0;
        for (Player player : instance.players()) {
            PlayerProfile profile = profiles.get(player);
            fortune = Math.max(fortune,
                    profile.stat(StatType.FORTUNE) * config.getDouble("skills.fortune-loot-per-point", 0.04));
        }
        for (GenResult.ChestSpot spot : instance.gen.chests) {
            if (instance.blockAt(spot.pos()).getState() instanceof Container container) {
                lootRoller.fill(container.getInventory(), bp.level(), lootMult, spot.treasure(),
                        fortune, new Random(bp.seed() ^ com.evensteven.vhlite.vault.generation.BlockBuffer
                                .pack(spot.pos().x(), spot.pos().y(), spot.pos().z())));
            }
        }
    }

    private void activate(VaultInstance instance) {
        VaultBlueprint bp = instance.blueprint();
        instance.state = VaultInstance.State.ACTIVE;
        int base = config.getInt("vault.time-limit-seconds", 1200)
                + config.getInt("vault.time-per-extra-player", 120) * (bp.partySize() - 1);
        instance.totalSeconds = (int) Math.round(base * bp.modifierProduct(m -> m.timeMult));
        instance.secondsLeft = instance.totalSeconds;

        StringBuilder mods = new StringBuilder();
        for (VaultModifier modifier : bp.modifiers()) {
            mods.append(" §8[").append(modifier.displayName).append("§8]");
        }
        instance.bossBar = BossBar.bossBar(
                Text.c(bp.theme().displayName + " §7— " + bp.objective().displayName + mods),
                1f, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);

        // The party map: a shared server-rendered view revealing visited rooms.
        instance.mapView = Bukkit.createMap(instance.world());
        for (org.bukkit.map.MapRenderer renderer : instance.mapView.getRenderers()) {
            instance.mapView.removeRenderer(renderer);
        }
        instance.mapView.addRenderer(new VaultMapRenderer(instance));
        instance.mapView.setTrackingPosition(false);

        Location start = instance.worldPos(instance.gen.startPad);
        instance.markVisited(instance.cellOf(start));
        for (Player player : instance.players()) {
            player.teleport(start);
            player.setGameMode(GameMode.SURVIVAL);
            player.setFallDistance(0f);
            player.showBossBar(instance.bossBar);
            scaling.applyPlayerSpeed(player, bp);
            org.bukkit.inventory.ItemStack mapItem = VhItems.create(VhItemType.VAULT_MAP);
            mapItem.editMeta(meta ->
                    ((org.bukkit.inventory.meta.MapMeta) meta).setMapView(instance.mapView));
            VhItems.give(player, mapItem);
            player.showTitle(Title.title(Text.c(bp.theme().displayName),
                    Text.c(bp.objective().displayName)));
            player.sendMessage(Text.c("§5» §7" + bp.objective().description));
            for (VaultModifier modifier : bp.modifiers()) {
                player.sendMessage(Text.c("§5» " + modifier.displayName + "§7: " + modifier.description));
            }
            player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.4f, 1.6f);
        }
        if (bp.objective() == VaultObjective.ESCAPE) {
            instance.openExit();
        }
    }

    /**
     * Console-friendly generator harness: computes {@code count} random
     * vaults, validates their invariants, prints the variety spread, and
     * applies the first one to the world so the whole pipeline gets
     * exercised without a player. /vhadmin benchgen [count].
     */
    public void benchGenerate(org.bukkit.command.CommandSender sender, int count) {
        Map<String, Integer> spread = new java.util.TreeMap<>();
        long totalBlocks = 0;
        long worstNanos = 0;
        VaultInstance applied = null;
        int failures = 0;
        for (int i = 0; i < count; i++) {
            long seed = random.nextLong();
            int level = 1 + random.nextInt(20);
            Random roll = new Random(seed * 31 + 7);
            Theme theme = themes.roll(level, roll);
            LayoutType layout = LayoutType.roll(roll);
            VaultObjective objective = VaultObjective.roll(roll);
            boolean apply = applied == null;
            int slot = apply ? allocator.claim() : 0;
            VaultBlueprint bp = new VaultBlueprint(UUID.randomUUID(), seed, level, 1, theme,
                    layout, objective, List.of(), slot,
                    apply ? allocator.originX(slot) : 0, apply ? allocator.originZ(slot) : 0);
            try {
                long startNanos = System.nanoTime();
                GenResult gen = generator.generate(bp);
                worstNanos = Math.max(worstNanos, System.nanoTime() - startNanos);
                if (gen.startPad == null || gen.exitCenter == null
                        || gen.mobSpawns.isEmpty() || gen.chests.isEmpty()) {
                    throw new IllegalStateException("missing markers");
                }
                totalBlocks += gen.buffer.size();
                spread.merge("layout " + layout, 1, Integer::sum);
                spread.merge("theme " + theme.name, 1, Integer::sum);
                spread.merge("objective " + objective, 1, Integer::sum);
                if (apply) {
                    VaultInstance instance = new VaultInstance(bp, worlds.world());
                    instance.gen = gen;
                    long applyStart = System.nanoTime();
                    applier.apply(worlds.world(), gen.buffer, bp.originX(), bp.originZ(),
                                    () -> fillChests(instance))
                            .thenAccept(chunks -> {
                                instance.pinnedChunks = chunks;
                                sender.sendMessage(Text.c("§abenchgen: applied seed " + seed
                                        + " (" + gen.buffer.size() + " blocks) in "
                                        + ((System.nanoTime() - applyStart) / 1_000_000) + "ms; slot "
                                        + slot + " retired for next restart."));
                                for (Chunk chunk : chunks) {
                                    chunk.removePluginChunkTicket(plugin);
                                }
                                store.retireSlot(slot);
                            });
                    applied = instance;
                }
            } catch (Exception ex) {
                failures++;
                sender.sendMessage(Text.c("§cbenchgen: seed " + seed + " failed: " + ex.getMessage()));
            }
        }
        sender.sendMessage(Text.c("§7benchgen: " + count + " vaults, " + failures + " failure(s), avg "
                + (totalBlocks / Math.max(1, count - failures)) + " blocks, worst compute "
                + (worstNanos / 1_000_000) + "ms."));
        for (Map.Entry<String, Integer> entry : spread.entrySet()) {
            sender.sendMessage(Text.c("§8  " + entry.getKey() + ": " + entry.getValue()));
        }
    }

    // -------------------------------------------------------------- endings

    /** The objective is met: open the exit and pay the completion XP now. */
    public void completeObjective(VaultInstance instance) {
        if (instance.objectiveDone) {
            return;
        }
        instance.objectiveDone = true;
        instance.openExit();
        int xp = config.getInt("xp.completion-base", 60)
                + config.getInt("xp.completion-per-level", 10) * instance.blueprint().level();
        for (Player player : instance.players()) {
            levels.addXp(player, xp);
            player.showTitle(Title.title(Text.c("§aObjective complete!"),
                    Text.c("§7The exit pad is glowing — step on it to leave.")));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
    }

    /** Clean exit through the pad (or a completed escape). No penalties. */
    public void extract(Player player, VaultInstance instance, String message) {
        settleOut(player, instance);
        if (message != null) {
            player.sendMessage(Text.c(message));
        }
        // ESCAPE pays on successful extraction rather than at objectiveDone.
        if (instance.blueprint().objective() == VaultObjective.ESCAPE
                && !instance.deadSpectators.contains(player.getUniqueId())) {
            int xp = config.getInt("xp.completion-base", 60)
                    + config.getInt("xp.completion-per-level", 10) * instance.blueprint().level();
            levels.addXp(player, xp);
        }
        finishRemoval(player, instance);
    }

    /** Leaving early or running out the clock: post-grace players feed a spirit. */
    public void abandon(Player player, VaultInstance instance, String message) {
        boolean wasDead = instance.deadSpectators.contains(player.getUniqueId());
        settleOut(player, instance);
        if (!wasDead && profiles.get(player).vaultLevel >= config.getInt("grace-level", 5)) {
            spirits.capture(player, instance.blueprint().level());
            player.sendMessage(Text.c("§cThe vault kept what you carried. §7Revive your spirit at a"
                    + " Vault Altar with §3Vault Essence§7."));
        }
        if (message != null) {
            player.sendMessage(Text.c(message));
        }
        finishRemoval(player, instance);
    }

    /** Teleport home + strip run-scoped state. */
    private void settleOut(Player player, VaultInstance instance) {
        if (instance.bossBar != null) {
            player.hideBossBar(instance.bossBar);
        }
        scaling.clearPlayerSpeed(player);
        // The vault map dies with the vault.
        org.bukkit.inventory.ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (VhItems.typeOf(contents[i]) == VhItemType.VAULT_MAP) {
                player.getInventory().setItem(i, null);
            }
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.setFallDistance(0f);
        player.teleport(instance.returnLocation(player.getUniqueId()));
    }

    private void finishRemoval(Player player, VaultInstance instance) {
        byPlayer.remove(player.getUniqueId());
        instance.removeMember(player.getUniqueId());
        if (instance.isEmpty()) {
            close(instance);
        }
    }

    public void timeout(VaultInstance instance) {
        for (Player player : instance.players()) {
            player.showTitle(Title.title(Text.c("§4The vault collapsed"), Text.c("")));
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.7f, 0.6f);
            abandon(player, instance, "§7The vault collapsed around you.");
        }
        close(instance); // sweeps any offline stragglers off the roster
    }

    public void close(VaultInstance instance) {
        if (instance.state == VaultInstance.State.CLOSED) {
            return;
        }
        // Anyone somehow still tracked gets settled out first.
        for (Player player : instance.players()) {
            settleOut(player, instance);
            byPlayer.remove(player.getUniqueId());
        }
        instance.state = VaultInstance.State.CLOSED;
        instance.killOwnedMobs();
        for (Chunk chunk : instance.pinnedChunks) {
            chunk.removePluginChunkTicket(plugin);
        }
        store.retireSlot(instance.blueprint().slot());
        store.clearActive(instance.id());
        instances.remove(instance.id());
        plugin.getLogger().info("Closed vault " + instance.id() + " (slot "
                + instance.blueprint().slot() + "); region purge on next restart.");
    }

    public void closeAllForShutdown() {
        for (VaultInstance instance : new ArrayList<>(instances.values())) {
            for (Player player : instance.players()) {
                // No penalty on server stop: they were snapshotted at entry,
                // but a clean stop can settle them out gently instead.
                settleOut(player, instance);
                byPlayer.remove(player.getUniqueId());
                instance.removeMember(player.getUniqueId());
            }
            close(instance);
        }
    }

    // ---------------------------------------------------- objective events

    private void handleArtifactBreak(Player player, org.bukkit.block.Block block, VaultInstance instance) {
        Vec3 rel = instance.relativeOf(block.getLocation());
        if (!instance.artifactsLeft.remove(rel)) {
            return;
        }
        block.setType(Material.AIR, false);
        int done = instance.objectiveTarget - instance.artifactsLeft.size();
        for (Player member : instance.players()) {
            member.playSound(member.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 0.8f);
            member.sendMessage(Text.c("§d✦ Artifact recovered §7(" + done + "/"
                    + instance.objectiveTarget + ") §8by " + player.getName()));
        }
        levels.addXp(player, config.getInt("xp.per-chest", 5));
        if (instance.artifactsLeft.isEmpty()) {
            completeObjective(instance);
        }
    }

    // --------------------------------------------------------------- events

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!worlds.isVaultWorld(event.getBlock().getWorld())) {
            return;
        }
        VaultInstance instance = instanceOf(event.getPlayer());
        if (instance == null || instance.state != VaultInstance.State.ACTIVE
                || !instance.contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        if (event.getBlock().getType() == Material.AMETHYST_CLUSTER) {
            event.setCancelled(true);
            handleArtifactBreak(event.getPlayer(), event.getBlock(), instance);
            return;
        }
        Vec3 rel = instance.relativeOf(event.getBlock().getLocation());
        // Unopened objective chests must be OPENED (counted), not smashed.
        if (instance.treasureLeft.contains(rel)) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Text.c("§6Open this chest — don't break it."));
            return;
        }
        if (instance.isProtectedFixture(rel)) {
            event.setCancelled(true);
            return;
        }
        if (instance.isShellBlock(rel)) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Text.c("§5The vault's outer walls resist you."));
            return;
        }
        // Anything else — interior walls, decor, chests, player blocks — is
        // fair game. Mine between rooms if you like; the shell keeps you in.
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!worlds.isVaultWorld(event.getBlock().getWorld())) {
            return;
        }
        VaultInstance instance = instanceOf(event.getPlayer());
        // Building inside your own live vault is allowed (bridges, pillars,
        // cheeky mob cages); everything is wiped with the slot anyway.
        if (instance == null || instance.state != VaultInstance.State.ACTIVE
                || !instance.contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || !worlds.isVaultWorld(event.getClickedBlock().getWorld())) {
            return;
        }
        VaultInstance instance = instanceOf(event.getPlayer());
        if (instance != null && event.getClickedBlock().getType() == Material.AMETHYST_CLUSTER) {
            event.setCancelled(true);
            handleArtifactBreak(event.getPlayer(), event.getClickedBlock(), instance);
        }
    }

    @EventHandler
    public void onChestOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)
                || !(event.getInventory().getHolder() instanceof Container container)
                || !worlds.isVaultWorld(container.getWorld())) {
            return;
        }
        VaultInstance instance = instanceOf(player);
        if (instance == null || instance.state != VaultInstance.State.ACTIVE) {
            return;
        }
        Vec3 rel = instance.relativeOf(container.getLocation());
        if (instance.chestsOpened.add(rel)) {
            levels.addXp(player, config.getInt("xp.per-chest", 5));
        }
        if (instance.treasureLeft.remove(rel)) {
            int done = instance.objectiveTarget - instance.treasureLeft.size();
            for (Player member : instance.players()) {
                member.playSound(member.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 0.7f);
                member.sendMessage(Text.c("§6☒ Marked chest found §7(" + done + "/"
                        + instance.objectiveTarget + ") §8by " + player.getName()));
            }
            if (instance.treasureLeft.isEmpty()) {
                completeObjective(instance);
            }
        }
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        String tag = event.getEntity().getPersistentDataContainer()
                .get(com.evensteven.vhlite.util.Keys.INSTANCE_ID, PersistentDataType.STRING);
        if (tag == null) {
            return;
        }
        VaultInstance instance = instances.get(UUID.fromString(tag));
        if (instance == null) {
            return;
        }
        instance.waveMobs.remove(event.getEntity().getUniqueId());
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            levels.addXp(killer, config.getInt("xp.per-mob-kill", 2));
            if (instance.rng.nextInt(5) == 0) {
                event.getDrops().add(VhItems.create(VhItemType.VAULT_ESSENCE));
            }
        }
        if (event.getEntity().getUniqueId().equals(instance.bossId)) {
            org.bukkit.inventory.ItemStack essence = VhItems.create(VhItemType.VAULT_ESSENCE);
            essence.setAmount(5);
            event.getDrops().add(essence);
            event.getDrops().add(VhItems.catalyst(
                    VaultModifier.values()[instance.rng.nextInt(VaultModifier.values().length)]));
            for (Player member : instance.players()) {
                member.sendMessage(Text.c(instance.blueprint().theme().bossName + " §7has fallen!"));
            }
            if (instance.blueprint().objective() == VaultObjective.BOSS) {
                completeObjective(instance);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.LivingEntity mob)
                || !worlds.isVaultWorld(mob.getWorld())
                || !mob.getPersistentDataContainer().has(
                        com.evensteven.vhlite.util.Keys.MOB_BASE, PersistentDataType.STRING)) {
            return;
        }
        // Health applies after the event; repaint the bar next tick.
        Bukkit.getScheduler().runTask(plugin, () -> MobNameplates.refresh(mob));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        VaultInstance instance = instanceOf(event.getPlayer());
        if (instance == null) {
            return;
        }
        // Quitting mid-run isn't punished (disconnects happen); the player is
        // settled home so they never log into a purged region.
        settleOut(event.getPlayer(), instance);
        finishRemoval(event.getPlayer(), instance);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Crash recovery: restore the entry snapshot and refund a crystal.
        InstanceStore.Recovery recovery = store.takeRecovery(player.getUniqueId());
        if (recovery != null) {
            player.getInventory().clear();
            for (int i = 0; i < recovery.items().size() && i < player.getInventory().getSize(); i++) {
                if (recovery.items().get(i) != null) {
                    player.getInventory().setItem(i, recovery.items().get(i));
                }
            }
            VhItems.give(player, VhItems.crystal(recovery.refundLevel(), List.of()));
            player.sendMessage(Text.c("§7The server went down mid-vault. Your gear is restored"
                    + " and a §dLevel " + recovery.refundLevel() + " crystal§7 is on the house."));
        }
        // Stranded in the vault world with no live run? Send them home.
        if (worlds.isVaultWorld(player.getWorld()) && instanceOf(player) == null) {
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            player.setGameMode(GameMode.SURVIVAL);
        }
    }
}
