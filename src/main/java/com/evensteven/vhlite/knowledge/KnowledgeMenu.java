package com.evensteven.vhlite.knowledge;

import com.evensteven.vhlite.menu.Menu;
import com.evensteven.vhlite.player.PlayerProfile;
import com.evensteven.vhlite.player.ProfileStore;
import com.evensteven.vhlite.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The research screen. Locked nodes show their point cost; researched
 * ability nodes become claim-buttons for their item.
 */
public final class KnowledgeMenu extends Menu {

    private final ProfileStore profiles;
    private final KnowledgeService knowledge;

    public KnowledgeMenu(Player viewer, ProfileStore profiles, KnowledgeService knowledge) {
        super(viewer, 3, "§bKnowledge — unlock features");
        this.profiles = profiles;
        this.knowledge = knowledge;
    }

    @Override
    protected void build() {
        PlayerProfile profile = profiles.get(viewer);
        icon(4, named(Material.NETHER_STAR, "§bKnowledge Points: §e" + profile.knowledgePoints,
                "§7One every few vault levels;",
                "§7Knowledge Stars grant them instantly."));

        int[] slots = {10, 11, 12, 14, 15, 16};
        ResearchNode[] nodes = ResearchNode.values();
        for (int i = 0; i < nodes.length && i < slots.length; i++) {
            ResearchNode node = nodes[i];
            boolean owned = profile.has(node);
            ItemStack icon = new ItemStack(node.icon);
            List<String> lore = new ArrayList<>(List.of(node.description));
            if (owned) {
                lore.add(isAbility(node) ? "§aResearched — click to claim the item." : "§aResearched.");
            } else {
                lore.add("§7Cost: §b" + knowledge.cost(node) + " knowledge");
                lore.add(profile.knowledgePoints >= knowledge.cost(node)
                        ? "§aClick to research." : "§8Not enough points.");
            }
            icon.editMeta(meta -> {
                meta.displayName(Text.item((owned ? "§a✔ " : "§8✘ ") + node.displayName));
                meta.lore(Text.lore(lore.toArray(String[]::new)));
                meta.setEnchantmentGlintOverride(owned);
            });
            button(slots[i], icon, event -> {
                PlayerProfile now = profiles.get(viewer);
                if (now.has(node)) {
                    knowledge.claimAbilityItem(viewer, node);
                } else {
                    knowledge.research(viewer, node);
                }
                refresh();
            });
        }
        fillRow(0, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        fillRow(2, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
    }

    private boolean isAbility(ResearchNode node) {
        return node == ResearchNode.ABILITY_HEAL || node == ResearchNode.ABILITY_DASH
                || node == ResearchNode.ABILITY_WARCRY;
    }
}
