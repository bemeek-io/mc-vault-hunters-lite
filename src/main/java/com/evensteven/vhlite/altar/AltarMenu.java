package com.evensteven.vhlite.altar;

import com.evensteven.vhlite.item.VhItemType;
import com.evensteven.vhlite.menu.Menu;
import com.evensteven.vhlite.player.PlayerProfile;
import com.evensteven.vhlite.player.ProfileStore;
import com.evensteven.vhlite.spirit.RevivalMenu;
import com.evensteven.vhlite.spirit.SpiritStore;
import com.evensteven.vhlite.util.Text;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * The Vault Altar screen: this level's crystal bill (green when you can pay
 * it), the infuse button, and the door to spirit revival. Opened by
 * right-clicking the altar block empty-handed.
 */
public final class AltarMenu extends Menu {

    private final ProfileStore profiles;
    private final CrystalRecipeService recipes;
    private final SpiritStore spirits;
    private final FileConfiguration config;

    public AltarMenu(Player viewer, ProfileStore profiles, CrystalRecipeService recipes,
            SpiritStore spirits, FileConfiguration config) {
        super(viewer, 3, "§5Vault Altar");
        this.profiles = profiles;
        this.recipes = recipes;
        this.spirits = spirits;
        this.config = config;
    }

    @Override
    protected void build() {
        PlayerProfile profile = profiles.get(viewer);
        List<CrystalRecipeService.Requirement> reqs = recipes.requirementsFor(profile);

        icon(4, named(Material.LODESTONE, "§5Vault Altar",
                "§7Your vault level: §d" + profile.vaultLevel,
                "§7The altar asks for what the vault senses", "§7you can gather. It changes as you grow."));

        int[] slots = {10, 11, 12, 13};
        for (int i = 0; i < reqs.size() && i < slots.length; i++) {
            CrystalRecipeService.Requirement req = reqs.get(i);
            int have = countOf(req.material());
            boolean enough = have >= req.amount();
            ItemStack icon = new ItemStack(req.material());
            int shown = Math.max(1, Math.min(64, req.amount()));
            icon.setAmount(shown);
            String name = (enough ? "§a" : "§c") + prettify(req.material());
            icon.editMeta(meta -> {
                meta.displayName(Text.item(name));
                meta.lore(Text.lore("§7Need §e" + req.amount() + "§7, carrying §e" + have,
                        enough ? "§a✔ ready" : "§c✘ keep gathering"));
            });
            icon(slots[i], icon);
        }

        boolean affordable = recipes.canAfford(viewer, reqs);
        button(15, named(Material.AMETHYST_SHARD,
                        affordable ? "§dInfuse Vault Crystal §a(click!)" : "§8Infuse Vault Crystal",
                        "§7Consumes the resources on the left and",
                        "§7forges a §dLevel " + Math.max(1, profile.vaultLevel) + "§7 crystal.",
                        "§8Right-click the altar with it to enter."),
                event -> {
                    if (recipes.infuse(viewer, profiles.get(viewer))) {
                        viewer.playSound(viewer.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.3f);
                        viewer.sendMessage(Text.c("§dThe altar hums. A Vault Crystal is yours."));
                        refresh();
                    } else {
                        viewer.playSound(viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
                        viewer.sendMessage(Text.c("§cYou are missing resources — the altar stays cold."));
                    }
                });

        int spiritCount = spirits.spiritsOf(viewer.getUniqueId()).size();
        button(16, named(Material.SOUL_LANTERN,
                        spiritCount > 0 ? "§3Spirits §b(" + spiritCount + " waiting)" : "§8Spirits §7(none)",
                        "§7Fallen in a vault? Pay §3Vault Essence",
                        "§7here to call your gear back."),
                event -> new RevivalMenu(viewer, spirits, config).open(viewer));

        fillRow(0, Material.PURPLE_STAINED_GLASS_PANE);
        fillRow(2, Material.PURPLE_STAINED_GLASS_PANE);
    }

    private int countOf(Material material) {
        int total = 0;
        for (ItemStack item : viewer.getInventory().getContents()) {
            // Plain resources only — custom items never count toward the bill.
            if (item != null && item.getType() == material
                    && com.evensteven.vhlite.item.VhItems.typeOf(item) == null) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private String prettify(Material material) {
        String[] words = material.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
