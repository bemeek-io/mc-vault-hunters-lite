package com.evensteven.vhlite.vault;

import com.evensteven.vhlite.util.Keys;
import com.evensteven.vhlite.util.Text;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

/**
 * Floating nameplates for vault mobs: "Lv.7 Skeleton" plus a colored health
 * bar, visible on vanilla clients via custom names. The base label is
 * stamped into the mob's PDC at spawn so damage events can re-render just
 * the bar.
 */
public final class MobNameplates {

    private static final int SEGMENTS = 8;

    private MobNameplates() {
    }

    /** Stamp the base label and paint the initial (full) bar. */
    public static void apply(LivingEntity mob, int level, boolean elite, String customBase) {
        String base;
        if (customBase != null) {
            base = customBase;
        } else if (elite) {
            base = "§6Elite §7Lv." + level + " §f" + prettyType(mob);
        } else {
            base = "§7Lv." + level + " §f" + prettyType(mob);
        }
        mob.getPersistentDataContainer().set(Keys.MOB_BASE, PersistentDataType.STRING, base);
        refresh(mob);
        mob.setCustomNameVisible(true);
    }

    /** Re-render the health bar; call a tick after any damage/heal. */
    public static void refresh(LivingEntity mob) {
        String base = mob.getPersistentDataContainer().get(Keys.MOB_BASE, PersistentDataType.STRING);
        if (base == null || mob.isDead()) {
            return;
        }
        AttributeInstance maxAttr = mob.getAttribute(Attribute.MAX_HEALTH);
        double max = maxAttr != null ? maxAttr.getValue() : 20.0;
        double pct = Math.max(0.0, Math.min(1.0, mob.getHealth() / Math.max(0.01, max)));
        int filled = (int) Math.ceil(pct * SEGMENTS);
        String color = pct > 0.6 ? "§a" : pct > 0.3 ? "§e" : "§c";
        StringBuilder bar = new StringBuilder(" ").append(color);
        for (int i = 0; i < SEGMENTS; i++) {
            if (i == filled) {
                bar.append("§8");
            }
            bar.append('❚');
        }
        mob.customName(Text.c(base + bar));
    }

    private static String prettyType(LivingEntity mob) {
        String[] words = mob.getType().name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
