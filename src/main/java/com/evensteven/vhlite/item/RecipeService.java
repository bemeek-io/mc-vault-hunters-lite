package com.evensteven.vhlite.item;

import com.evensteven.vhlite.knowledge.ResearchNode;
import com.evensteven.vhlite.player.PlayerProfile;
import com.evensteven.vhlite.player.ProfileStore;
import com.evensteven.vhlite.util.Text;
import com.evensteven.vhlite.vault.modifier.VaultModifier;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers every crafting recipe and enforces two rules the vanilla table
 * can't: recipes stay locked until their knowledge node is researched, and
 * "shaped like" ingredients (amethyst/prismarine shards) must actually be
 * the custom items, not their vanilla lookalikes.
 */
public final class RecipeService implements Listener {

    private final Plugin plugin;
    private final ProfileStore profiles;
    /** recipe key -> knowledge node required to craft it (null = free). */
    private final Map<NamespacedKey, ResearchNode> gates = new HashMap<>();
    /** node -> recipe keys to reveal in the recipe book on research. */
    private final Map<ResearchNode, List<NamespacedKey>> byNode = new HashMap<>();
    private NamespacedKey backpackKey;
    private NamespacedKey combineKey;
    private NamespacedKey altarKey;

    public RecipeService(Plugin plugin, ProfileStore profiles) {
        this.plugin = plugin;
        this.profiles = profiles;
    }

    public Map<ResearchNode, List<NamespacedKey>> recipeKeysByNode() {
        return byNode;
    }

    public void register() {
        // Backpack: leather around a chest. Result gets a fresh id per craft.
        backpackKey = key("backpack");
        ShapedRecipe backpack = new ShapedRecipe(backpackKey, VhItems.create(VhItemType.BACKPACK));
        backpack.shape("LLL", "LCL", "LLL");
        backpack.setIngredient('L', Material.LEATHER);
        backpack.setIngredient('C', Material.CHEST);
        add(backpack, ResearchNode.BACKPACK);

        ShapedRecipe wand = new ShapedRecipe(key("link_wand"), VhItems.create(VhItemType.LINK_WAND));
        wand.shape("E", "B", "B");
        wand.setIngredient('E', Material.ENDER_PEARL);
        wand.setIngredient('B', Material.BLAZE_ROD);
        add(wand, ResearchNode.CHEST_LINKING);

        // The altar itself: the gateway recipe, never knowledge-gated.
        // Amethyst comes from geodes, a diamond from any brave cave trip.
        altarKey = key("vault_altar");
        ShapedRecipe altar = new ShapedRecipe(altarKey, VhItems.create(VhItemType.VAULT_ALTAR));
        altar.shape(" A ", "SDS", "SSS");
        altar.setIngredient('A', Material.AMETHYST_SHARD);
        altar.setIngredient('S', Material.STONE_BRICKS);
        altar.setIngredient('D', Material.DIAMOND);
        add(altar, null);

        ShapedRecipe star = new ShapedRecipe(key("knowledge_star"), VhItems.create(VhItemType.KNOWLEDGE_STAR));
        star.shape("DLD", "LBL", "DLD");
        star.setIngredient('D', Material.DIAMOND);
        star.setIngredient('L', Material.LAPIS_BLOCK);
        star.setIngredient('B', Material.BOOK);
        add(star, null); // knowledge begets knowledge; never gated

        // Catalysts themselves are bought with Vault Essence at the altar
        // (CatalystMenu) now that essence is a currency, not a craftable
        // ingredient — no table recipe for them.

        // Crystal + catalyst -> crystal with the forced modifier.
        combineKey = key("combine_crystal");
        ShapelessRecipe combine = new ShapelessRecipe(combineKey, VhItems.crystal(1, List.of()));
        combine.addIngredient(Material.AMETHYST_SHARD);
        combine.addIngredient(Material.PRISMARINE_SHARD);
        add(combine, ResearchNode.CATALYSTS);
    }

    private void add(Recipe recipe, ResearchNode gate) {
        Bukkit.addRecipe(recipe);
        NamespacedKey recipeKey = ((Keyed) recipe).getKey();
        if (gate != null) {
            gates.put(recipeKey, gate);
            byNode.computeIfAbsent(gate, n -> new ArrayList<>()).add(recipeKey);
        }
    }

    private NamespacedKey key(String name) {
        return new NamespacedKey(plugin, name);
    }

    /** Reveal already-earned recipes in the book (called on join). */
    public void syncDiscovered(Player player) {
        PlayerProfile profile = profiles.get(player);
        player.discoverRecipe(key("knowledge_star"));
        player.discoverRecipe(key("vault_altar"));
        for (ResearchNode node : profile.research) {
            for (NamespacedKey recipeKey : byNode.getOrDefault(node, List.of())) {
                player.discoverRecipe(recipeKey);
            }
        }
    }

    // --------------------------------------------------------------- events

    @EventHandler
    public void onPrepare(PrepareItemCraftEvent event) {
        if (!(event.getRecipe() instanceof Keyed keyed)
                || !keyed.getKey().getNamespace().equals(plugin.getName().toLowerCase())) {
            return;
        }
        NamespacedKey recipeKey = keyed.getKey();
        Player crafter = event.getView().getPlayer() instanceof Player p ? p : null;

        ResearchNode gate = gates.get(recipeKey);
        if (gate != null && (crafter == null || !profiles.get(crafter).has(gate))) {
            event.getInventory().setResult(null);
            return;
        }

        // Lookalike guard: every amethyst/prismarine shard in the grid must
        // be the real custom item.
        ItemStack crystal = null;
        ItemStack catalyst = null;
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            VhItemType type = VhItems.typeOf(item);
            if (item.getType() == Material.AMETHYST_SHARD) {
                if (type != VhItemType.VAULT_CRYSTAL) {
                    if (recipeKey.equals(combineKey)) {
                        event.getInventory().setResult(null);
                        return;
                    }
                    continue; // plain shards are fine in non-combine recipes
                }
                crystal = item;
            }
            if (item.getType() == Material.PRISMARINE_SHARD) {
                if (type != VhItemType.CATALYST) {
                    if (recipeKey.equals(combineKey)) {
                        event.getInventory().setResult(null);
                        return;
                    }
                    continue;
                }
                catalyst = item;
            }
        }

        // Never let a real crystal/catalyst be burned as a plain shard in
        // some other recipe (e.g. the altar's amethyst slot).
        if (!recipeKey.equals(combineKey) && (crystal != null || catalyst != null)) {
            event.getInventory().setResult(null);
            return;
        }

        if (recipeKey.equals(combineKey)) {
            if (crystal == null || catalyst == null) {
                event.getInventory().setResult(null);
                return;
            }
            List<VaultModifier> mods = new ArrayList<>(VhItems.crystalModifiers(crystal));
            VaultModifier forced = VhItems.catalystModifier(catalyst);
            if (forced == null || mods.contains(forced) || mods.size() >= 3) {
                event.getInventory().setResult(null);
                return;
            }
            mods.add(forced);
            event.getInventory().setResult(VhItems.crystal(VhItems.crystalLevel(crystal), mods));
        } else if (recipeKey.equals(backpackKey)) {
            // Fresh id per craft so two backpacks never share a stash.
            event.getInventory().setResult(VhItems.backpack());
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!(event.getRecipe() instanceof Keyed keyed) || !keyed.getKey().equals(backpackKey)) {
            return;
        }
        if (event.isShiftClick()) {
            // Shift-crafting would clone one id across the whole batch.
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage(Text.c("§7Backpacks must be crafted one at a time."));
            }
        }
    }
}
