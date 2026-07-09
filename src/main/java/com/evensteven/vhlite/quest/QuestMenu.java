package com.evensteven.vhlite.quest;

import com.evensteven.vhlite.menu.Menu;
import com.evensteven.vhlite.player.PlayerProfile;
import com.evensteven.vhlite.player.ProfileStore;
import com.evensteven.vhlite.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The quest log: done quests glow green, active ones show a progress bar,
 * locked ones stay shadowed until the chain reaches them.
 */
public final class QuestMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23};

    private final ProfileStore profiles;
    private final QuestService quests;

    public QuestMenu(Player viewer, ProfileStore profiles, QuestService quests) {
        super(viewer, 4, "§6Quests");
        this.profiles = profiles;
        this.quests = quests;
    }

    @Override
    protected void build() {
        PlayerProfile profile = profiles.get(viewer);
        icon(4, named(Material.WRITABLE_BOOK, "§6Quest Log §7("
                        + profile.questsCompleted.size() + "/" + QuestType.values().length + ")",
                "§7Quests track themselves — play and they",
                "§7complete. Finishing one reveals the next."));

        QuestType[] all = QuestType.values();
        for (int i = 0; i < all.length && i < SLOTS.length; i++) {
            QuestType quest = all[i];
            boolean done = quests.isComplete(profile, quest);
            boolean unlocked = quests.isUnlocked(profile, quest);
            ItemStack icon = new ItemStack(done || unlocked ? quest.icon : Material.GRAY_DYE);
            int progress = Math.min(quest.target, quests.progressOf(profile, quest));
            icon.editMeta(meta -> {
                String name = done ? "§a✔ " + quest.displayName
                        : unlocked ? "§e" + quest.displayName : "§8???";
                meta.displayName(Text.item(name));
                if (done) {
                    meta.lore(Text.lore("§7" + quest.description, "§aComplete."));
                } else if (unlocked) {
                    meta.lore(Text.lore("§7" + quest.description,
                            "§7Progress: §e" + progress + "§7/§e" + quest.target + " " + bar(progress, quest.target),
                            "§7Reward: §f" + quest.rewardText));
                } else {
                    meta.lore(Text.lore("§8Complete earlier quests to reveal."));
                }
                meta.setEnchantmentGlintOverride(done);
            });
            icon(SLOTS[i], icon);
        }
        fillRow(0, Material.ORANGE_STAINED_GLASS_PANE);
        fillRow(3, Material.ORANGE_STAINED_GLASS_PANE);
    }

    private String bar(int progress, int target) {
        int filled = (int) Math.round(8.0 * progress / Math.max(1, target));
        StringBuilder sb = new StringBuilder("§a");
        for (int i = 0; i < 8; i++) {
            if (i == filled) {
                sb.append("§8");
            }
            sb.append('❚');
        }
        return sb.toString();
    }
}
