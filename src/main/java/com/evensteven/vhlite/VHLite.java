package com.evensteven.vhlite;

import com.evensteven.vhlite.altar.AltarListener;
import com.evensteven.vhlite.altar.CrystalRecipeService;
import com.evensteven.vhlite.command.VhAdminCommand;
import com.evensteven.vhlite.command.VhCommand;
import com.evensteven.vhlite.item.ItemUseListener;
import com.evensteven.vhlite.item.RecipeService;
import com.evensteven.vhlite.knowledge.KnowledgeService;
import com.evensteven.vhlite.menu.ChatPrompt;
import com.evensteven.vhlite.menu.MenuManager;
import com.evensteven.vhlite.player.LevelService;
import com.evensteven.vhlite.player.PartyService;
import com.evensteven.vhlite.player.PlayerLifecycleListener;
import com.evensteven.vhlite.player.ProfileStore;
import com.evensteven.vhlite.skills.AbilityService;
import com.evensteven.vhlite.skills.StatService;
import com.evensteven.vhlite.spirit.SpiritStore;
import com.evensteven.vhlite.spirit.VaultDeathListener;
import com.evensteven.vhlite.storage.BackpackService;
import com.evensteven.vhlite.storage.ChestLinkService;
import com.evensteven.vhlite.storage.StorageStore;
import com.evensteven.vhlite.util.Keys;
import com.evensteven.vhlite.vault.InstanceAllocator;
import com.evensteven.vhlite.vault.InstanceStore;
import com.evensteven.vhlite.vault.MobSpawnerTask;
import com.evensteven.vhlite.vault.ScalingService;
import com.evensteven.vhlite.vault.VaultInstanceManager;
import com.evensteven.vhlite.vault.VaultRunTask;
import com.evensteven.vhlite.vault.VaultWorldService;
import com.evensteven.vhlite.vault.generation.BlockBufferApplier;
import com.evensteven.vhlite.vault.generation.ThemeRegistry;
import com.evensteven.vhlite.vault.generation.VaultGenerator;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Vault Hunters Lite: a server-side, vanilla-client take on the Vault
 * Hunters loop. Gather resources, forge a crystal at any lodestone altar,
 * and raid a procedurally generated vault — leveling up, investing RPG
 * stats, researching features, and linking your storage as you go.
 *
 * Design pillars (see README):
 *  - zero client mods, zero client lag added;
 *  - one persistent void world, region-aligned instance slots, cleanup by
 *    deleting region files on startup — never block-clearing loops;
 *  - four independent variety axes (layout x theme x objective x modifiers)
 *    so runs don't go samey;
 *  - hard per-instance mob caps and no per-tick work outside two small
 *    scheduled tasks.
 */
public final class VHLite extends JavaPlugin {

    private ProfileStore profiles;
    private VaultInstanceManager vaults;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Keys.init(this);

        // --- persistence first: crash recovery must run before world load.
        InstanceStore instanceStore = new InstanceStore(this);
        instanceStore.recoverCrashedRuns();
        VaultWorldService worlds = new VaultWorldService(this,
                getConfig().getString("vault-world", "vhlite_vaults"),
                getConfig().getInt("generation.slot-spacing", 2048));
        worlds.purgeRetiredSlots(instanceStore.retiredSlots());
        instanceStore.clearRetiredSlots();

        // --- theme palettes resolve BlockData: main thread, before any run.
        ThemeRegistry themes = new ThemeRegistry();
        themes.init();
        worlds.createWorld();

        // --- services.
        profiles = new ProfileStore(this);
        LevelService levels = new LevelService(profiles, getConfig());
        PartyService parties = new PartyService(getConfig().getInt("party.max-size", 4));
        StatService stats = new StatService(profiles, getConfig());
        AbilityService abilities = new AbilityService(profiles, parties);
        SpiritStore spirits = new SpiritStore(this);
        StorageStore storageStore = new StorageStore(this);
        BackpackService backpacks = new BackpackService(storageStore, getConfig());
        ChestLinkService links = new ChestLinkService(storageStore, getConfig());
        CrystalRecipeService crystalRecipes = new CrystalRecipeService(getConfig());
        RecipeService recipes = new RecipeService(this, profiles);
        recipes.register();
        KnowledgeService knowledge = new KnowledgeService(profiles, getConfig(), recipes.recipeKeysByNode());

        ScalingService scaling = new ScalingService(getConfig());
        VaultGenerator generator = new VaultGenerator(
                getConfig().getDouble("generation.multi-floor-chance", 0.55));
        BlockBufferApplier applier = new BlockBufferApplier(this,
                getConfig().getInt("generation.blocks-per-tick", 20000));
        InstanceAllocator allocator = new InstanceAllocator(instanceStore,
                getConfig().getInt("generation.slot-spacing", 2048));
        vaults = new VaultInstanceManager(this, getConfig(), worlds, allocator, instanceStore,
                themes, generator, applier, scaling, profiles, levels, parties, spirits);

        // --- listeners.
        ChatPrompt prompts = new ChatPrompt(this);
        var pm = getServer().getPluginManager();
        pm.registerEvents(new MenuManager(this), this);
        pm.registerEvents(prompts, this);
        pm.registerEvents(recipes, this);
        pm.registerEvents(vaults, this);
        pm.registerEvents(new VaultDeathListener(this, vaults, profiles, spirits, getConfig()), this);
        pm.registerEvents(new AltarListener(getConfig(), profiles, crystalRecipes, spirits, vaults), this);
        pm.registerEvents(new ItemUseListener(profiles, knowledge, abilities, backpacks, links), this);
        pm.registerEvents(new PlayerLifecycleListener(profiles, stats, recipes, parties), this);

        // --- commands.
        VhCommand vh = new VhCommand(profiles, stats, knowledge, links, prompts, spirits,
                levels, parties, vaults);
        getCommand("vh").setExecutor(vh);
        getCommand("vh").setTabCompleter(vh);
        VhAdminCommand vhadmin = new VhAdminCommand(this, profiles, stats, vaults, instanceStore);
        getCommand("vhadmin").setExecutor(vhadmin);
        getCommand("vhadmin").setTabCompleter(vhadmin);

        // --- recurring work: 1s run tick, 2s spawner tick, 1s XP-bar paint.
        new VaultRunTask(vaults, scaling, getConfig()).runTaskTimer(this, 20L, 20L);
        new MobSpawnerTask(vaults, scaling, getConfig()).runTaskTimer(this, 40L, 40L);
        new com.evensteven.vhlite.player.XpBarTask(profiles, levels).runTaskTimer(this, 20L, 20L);

        getLogger().info("Vault Hunters Lite enabled. Altar block: "
                + getConfig().getString("altar.block", "LODESTONE") + ".");
    }

    @Override
    public void onDisable() {
        if (vaults != null) {
            vaults.closeAllForShutdown();
        }
        if (profiles != null) {
            profiles.saveAll();
        }
    }
}
