package com.evensteven.vhlite.altar;

import com.evensteven.vhlite.item.VhItems;
import com.evensteven.vhlite.player.PlayerProfile;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * What a Vault Crystal costs at the altar: 4 resources drawn from the tier
 * pool for the player's level, seeded by (player, level) so the bill is
 * stable until they level up — you can plan a gathering trip around it.
 * This is the hook that keeps vanilla mining/farming/fighting relevant.
 */
public final class CrystalRecipeService {

    public record Requirement(Material material, int amount) {
    }

    private final FileConfiguration config;

    public CrystalRecipeService(FileConfiguration config) {
        this.config = config;
    }

    public List<Requirement> requirementsFor(PlayerProfile profile) {
        int level = profile.vaultLevel;
        ConfigurationSection tiers = config.getConfigurationSection("altar.tiers");
        int tier = Math.min(level / 5, maxTier(tiers));
        List<Material> pool = new ArrayList<>();
        if (tiers != null) {
            for (String name : tiers.getStringList(String.valueOf(tier))) {
                Material material = Material.matchMaterial(name);
                if (material != null) {
                    pool.add(material);
                }
            }
        }
        if (pool.isEmpty()) {
            pool.add(Material.IRON_INGOT); // config safety net
        }
        Random seeded = new Random(profile.id.getMostSignificantBits() * 31L + level);
        Collections.shuffle(pool, seeded);
        int amount = (int) Math.min(config.getInt("altar.amount-max", 64),
                config.getInt("altar.amount-base", 8)
                        + (long) config.getInt("altar.amount-per-level", 2) * level);
        List<Requirement> out = new ArrayList<>();
        for (Material material : pool.subList(0, Math.min(4, pool.size()))) {
            out.add(new Requirement(material, amount));
        }
        return out;
    }

    public boolean canAfford(Player player, List<Requirement> requirements) {
        for (Requirement req : requirements) {
            if (!player.getInventory().containsAtLeast(new ItemStack(req.material()), req.amount())) {
                return false;
            }
        }
        return true;
    }

    /** Consumes the resources and hands over a level-stamped crystal. */
    public boolean infuse(Player player, PlayerProfile profile) {
        List<Requirement> requirements = requirementsFor(profile);
        if (!canAfford(player, requirements)) {
            return false;
        }
        for (Requirement req : requirements) {
            player.getInventory().removeItem(new ItemStack(req.material(), req.amount()));
        }
        VhItems.give(player, VhItems.crystal(Math.max(1, profile.vaultLevel), List.of()));
        return true;
    }

    private int maxTier(ConfigurationSection tiers) {
        int max = 0;
        if (tiers != null) {
            for (String key : tiers.getKeys(false)) {
                try {
                    max = Math.max(max, Integer.parseInt(key));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return max;
    }
}
