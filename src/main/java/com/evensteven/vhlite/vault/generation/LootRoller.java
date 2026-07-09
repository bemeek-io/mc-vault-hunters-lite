package com.evensteven.vhlite.vault.generation;

import com.evensteven.vhlite.item.VhItemType;
import com.evensteven.vhlite.item.VhItems;
import com.evensteven.vhlite.vault.modifier.VaultModifier;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/**
 * Fills vault chests. Rolls scale with vault level, party size, the party's
 * best Fortune investment, and run modifiers; treasure chests roll more and
 * richer. Runs on the main thread at chest-placement time.
 */
public final class LootRoller {

    private record Entry(Material material, int min, int max) {
    }

    private static final Entry[] COMMON = {
            new Entry(Material.BREAD, 2, 6),
            new Entry(Material.ARROW, 4, 12),
            new Entry(Material.IRON_INGOT, 1, 4),
            new Entry(Material.COAL, 2, 6),
            new Entry(Material.TORCH, 4, 10),
            new Entry(Material.STRING, 2, 6),
            new Entry(Material.COOKED_BEEF, 1, 4),
    };
    private static final Entry[] UNCOMMON = {
            new Entry(Material.GOLD_INGOT, 1, 3),
            new Entry(Material.ENDER_PEARL, 1, 2),
            new Entry(Material.EXPERIENCE_BOTTLE, 1, 3),
            new Entry(Material.IRON_BLOCK, 1, 1),
            new Entry(Material.LAPIS_LAZULI, 3, 8),
            new Entry(Material.GOLDEN_CARROT, 2, 5),
    };
    private static final Entry[] RARE = {
            new Entry(Material.DIAMOND, 1, 2),
            new Entry(Material.EMERALD, 1, 3),
            new Entry(Material.GOLDEN_APPLE, 1, 1),
            new Entry(Material.AMETHYST_SHARD, 1, 3),
    };
    private static final Entry[] EPIC = {
            new Entry(Material.DIAMOND_BLOCK, 1, 1),
            new Entry(Material.NETHERITE_SCRAP, 1, 1),
            new Entry(Material.ENCHANTED_GOLDEN_APPLE, 1, 1),
    };

    /**
     * @param fortuneBonus additive roll multiplier from the party's Fortune
     *                     stat (e.g. 0.4 for 10 points at 4%/pt).
     * @return Vault Gold (in copper units) found in this chest, credited to
     *         whoever opens it — 0 most of the time. Gold has no physical
     *         item form, so it can't be placed in the inventory like the
     *         rest of the roll.
     */
    public long fill(Inventory inv, int level, double lootMult, boolean treasure,
            double fortuneBonus, Random rng) {
        int rolls = (int) Math.round((3 + level / 5.0 + (treasure ? 4 : 0))
                * lootMult * (1.0 + fortuneBonus));
        rolls = Math.max(2, Math.min(rolls, 14));
        for (int i = 0; i < rolls; i++) {
            ItemStack item = rollItem(level, treasure, rng);
            inv.setItem(rng.nextInt(inv.getSize()), item);
        }
        // Vaultforged gear is a RARE chest find — the reliable source is the
        // Vault Crate every completion pays out.
        if (rng.nextDouble() < (treasure ? 0.10 : 0.02) * lootMult) {
            inv.setItem(rng.nextInt(inv.getSize()),
                    com.evensteven.vhlite.item.VaultGear.unidentified(level, rng, treasure ? 0.15 : 0.0));
        }
        return rollGold(level, treasure, lootMult, fortuneBonus, rng);
    }

    private long rollGold(int level, boolean treasure, double lootMult, double fortuneBonus, Random rng) {
        double chance = (treasure ? 0.55 : 0.30) * lootMult;
        if (rng.nextDouble() > chance) {
            return 0;
        }
        int copper = 1 + rng.nextInt(2 + level / 3);
        copper = (int) Math.round(copper * (1.0 + fortuneBonus));
        if (treasure) {
            copper *= 2 + rng.nextInt(3);
        }
        // Occasional windfalls jump a whole tier or two, so gold isn't just
        // a slow copper trickle.
        if (rng.nextInt(12) == 0) {
            copper *= 9;
        }
        if (rng.nextInt(60) == 0) {
            copper *= 81;
        }
        return Math.max(1, copper);
    }

    private ItemStack rollItem(int level, boolean treasure, Random rng) {
        // Tier AVAILABILITY is gated by level first — a level-1 chest simply
        // cannot roll epic loot, no matter how lucky the roll is. Only once
        // a tier unlocks does the level bonus start nudging you toward it.
        boolean uncommonUnlocked = level >= 2;
        boolean rareUnlocked = level >= 5;
        boolean epicUnlocked = level >= 11;

        int roll = rng.nextInt(100) + (treasure ? 25 : 0) + Math.min(20, level);
        if (epicUnlocked && roll >= 118 && rng.nextInt(4) == 0) {
            return VhItems.create(VhItemType.KNOWLEDGE_STAR);
        }
        if (epicUnlocked && roll >= 110) {
            return stack(EPIC, rng);
        }
        if (rareUnlocked && roll >= 95) {
            if (rng.nextInt(5) == 0) {
                VaultModifier[] mods = VaultModifier.values();
                return VhItems.catalyst(mods[rng.nextInt(mods.length)]);
            }
            return stack(RARE, rng);
        }
        if (uncommonUnlocked && roll >= 70) {
            return stack(UNCOMMON, rng);
        }
        return stack(COMMON, rng);
    }

    private ItemStack stack(Entry[] pool, Random rng) {
        Entry entry = pool[rng.nextInt(pool.length)];
        return new ItemStack(entry.material, entry.min + rng.nextInt(entry.max - entry.min + 1));
    }
}
