package com.evensteven.vhlite.item;

import com.evensteven.vhlite.knowledge.KnowledgeService;
import com.evensteven.vhlite.knowledge.ResearchNode;
import com.evensteven.vhlite.player.ProfileStore;
import com.evensteven.vhlite.skills.AbilityService;
import com.evensteven.vhlite.storage.BackpackService;
import com.evensteven.vhlite.storage.ChestLinkService;
import com.evensteven.vhlite.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * One router for every right-click on a custom item: backpacks open their
 * stash, the link wand toggles chests, knowledge stars are consumed,
 * ability items fire. Also stops custom items being placed as blocks
 * (backpacks are chests underneath).
 */
public final class ItemUseListener implements Listener {

    private final ProfileStore profiles;
    private final KnowledgeService knowledge;
    private final AbilityService abilities;
    private final BackpackService backpacks;
    private final ChestLinkService links;
    private final IdentifyService identify;
    private final java.util.Random random = new java.util.Random();

    public ItemUseListener(ProfileStore profiles, KnowledgeService knowledge,
            AbilityService abilities, BackpackService backpacks, ChestLinkService links,
            IdentifyService identify) {
        this.profiles = profiles;
        this.knowledge = knowledge;
        this.abilities = abilities;
        this.backpacks = backpacks;
        this.links = links;
        this.identify = identify;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR
                        && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = event.getItem();
        VhItemType type = VhItems.typeOf(held);
        if (type == null) {
            return;
        }
        switch (type) {
            case BACKPACK -> {
                event.setCancelled(true);
                if (profiles.get(player).has(ResearchNode.BACKPACK)) {
                    backpacks.open(player, held);
                } else {
                    player.sendMessage(Text.c("§cYou haven't researched backpacks. §7(/vh knowledge)"));
                }
            }
            case LINK_WAND -> {
                event.setCancelled(true);
                if (!profiles.get(player).has(ResearchNode.CHEST_LINKING)) {
                    player.sendMessage(Text.c("§cYou haven't researched chest linking. §7(/vh knowledge)"));
                } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                    links.toggleLink(player, event.getClickedBlock());
                }
            }
            case KNOWLEDGE_STAR -> {
                event.setCancelled(true);
                held.setAmount(held.getAmount() - 1);
                knowledge.consumeStar(player);
            }
            case UNIDENTIFIED_GEAR -> {
                event.setCancelled(true);
                identify.begin(player, held);
            }
            case VAULT_CRATE -> {
                event.setCancelled(true);
                openCrate(player, held);
            }
            case ABILITY_HEAL, ABILITY_DASH, ABILITY_WARCRY -> {
                if (abilities.use(player, type)) {
                    event.setCancelled(true);
                }
            }
            default -> {
                // Crystals interact with altars (AltarListener); catalysts and
                // essence are crafting materials. Just stop stray interactions.
                if (type == VhItemType.VAULT_CRYSTAL || type == VhItemType.CATALYST) {
                    event.setCancelled(true);
                }
            }
        }
    }

    /** Vault Crates: the completion reward. Always gear, plus trimmings. */
    private void openCrate(Player player, ItemStack crate) {
        int level = VhItems.crateLevel(crate);
        crate.setAmount(crate.getAmount() - 1);
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BARREL_OPEN, 1f, 0.9f);
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1f, 0.7f);
        VhItems.give(player, VaultGear.unidentified(level, random, 0.5));
        ItemStack essence = VhItems.create(VhItemType.VAULT_ESSENCE);
        essence.setAmount(2 + random.nextInt(3));
        VhItems.give(player, essence);
        if (random.nextInt(4) == 0) {
            VhItems.give(player, VhItems.catalyst(
                    com.evensteven.vhlite.vault.modifier.VaultModifier.values()[random.nextInt(
                            com.evensteven.vhlite.vault.modifier.VaultModifier.values().length)]));
        }
        player.sendMessage(Text.c("§6The crate creaks open... §7something sealed is inside."));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        VhItemType type = VhItems.typeOf(event.getItemInHand());
        // The altar is the one custom item that IS a block (AltarListener
        // registers it); everything else stays an item.
        if (type != null && type != VhItemType.VAULT_ALTAR) {
            event.setCancelled(true);
        }
    }
}
