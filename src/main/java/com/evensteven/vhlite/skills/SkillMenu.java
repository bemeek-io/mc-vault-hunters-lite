package com.evensteven.vhlite.skills;

import com.evensteven.vhlite.menu.Menu;
import com.evensteven.vhlite.player.PlayerProfile;
import com.evensteven.vhlite.player.ProfileStore;
import com.evensteven.vhlite.util.Text;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The stat investment screen: five stats, click to sink a point. Points
 * come from vault levels; caps and rates live in config.
 */
public final class SkillMenu extends Menu {

    private final ProfileStore profiles;
    private final StatService stats;

    public SkillMenu(Player viewer, ProfileStore profiles, StatService stats) {
        super(viewer, 3, "§dSkills — invest your points");
        this.profiles = profiles;
        this.stats = stats;
    }

    @Override
    protected void build() {
        PlayerProfile profile = profiles.get(viewer);
        icon(4, named(Material.EXPERIENCE_BOTTLE, "§dSkill Points: §e" + profile.skillPoints,
                "§7Earn one per vault level.",
                "§7Click a stat below to invest."));

        int[] slots = {10, 11, 12, 13, 14};
        StatType[] types = StatType.values();
        for (int i = 0; i < types.length; i++) {
            StatType stat = types[i];
            int invested = profile.stat(stat);
            int max = stats.maxPerStat();
            ItemStack icon = new ItemStack(stat.icon);
            icon.editMeta(meta -> {
                meta.displayName(Text.item(stat.displayName + " §7" + invested + "/" + max));
                meta.lore(Text.lore(stat.description,
                        invested >= max ? "§8Maxed out."
                                : profile.skillPoints > 0 ? "§aClick to invest 1 point."
                                        : "§8No points to spend."));
                meta.setEnchantmentGlintOverride(invested >= max);
            });
            button(slots[i], icon, event -> {
                if (stats.invest(viewer, stat)) {
                    viewer.playSound(viewer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
                } else {
                    viewer.playSound(viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
                }
                refresh();
            });
        }
        fillRow(0, Material.MAGENTA_STAINED_GLASS_PANE);
        fillRow(2, Material.MAGENTA_STAINED_GLASS_PANE);
    }
}
