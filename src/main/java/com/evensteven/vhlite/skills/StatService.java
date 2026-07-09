package com.evensteven.vhlite.skills;

import com.evensteven.vhlite.player.PlayerProfile;
import com.evensteven.vhlite.player.ProfileStore;
import com.evensteven.vhlite.util.Keys;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Turns stat investments into vanilla attribute modifiers (idempotent
 * remove-then-add, keyed per stat) — the RPG layer the user wanted instead
 * of ability spam. Fortune has no attribute; the loot roller reads it.
 */
public final class StatService {

    private final ProfileStore profiles;
    private final FileConfiguration config;

    public StatService(ProfileStore profiles, FileConfiguration config) {
        this.profiles = profiles;
        this.config = config;
    }

    /** Reapplies every stat modifier; call on join and after spending. */
    public void apply(Player player) {
        PlayerProfile profile = profiles.get(player);
        scalar(player, Attribute.ATTACK_DAMAGE, Keys.STAT_STRENGTH,
                profile.stat(StatType.STRENGTH) * config.getDouble("skills.strength-damage-per-point", 0.02));
        additive(player, Attribute.MAX_HEALTH, Keys.STAT_VITALITY,
                profile.stat(StatType.VITALITY) * config.getDouble("skills.vitality-hp-per-point", 1.0));
        scalar(player, Attribute.MOVEMENT_SPEED, Keys.STAT_SWIFTNESS,
                profile.stat(StatType.SWIFTNESS) * config.getDouble("skills.swiftness-speed-per-point", 0.015));
        additive(player, Attribute.ARMOR, Keys.STAT_RESILIENCE,
                profile.stat(StatType.RESILIENCE) * config.getDouble("skills.resilience-armor-per-point", 0.5));
    }

    public int maxPerStat() {
        return config.getInt("skills.max-per-stat", 20);
    }

    /** Spends one point if possible; true on success. */
    public boolean invest(Player player, StatType stat) {
        PlayerProfile profile = profiles.get(player);
        if (profile.skillPoints <= 0 || profile.stat(stat) >= maxPerStat()) {
            return false;
        }
        profile.skillPoints--;
        profile.stats.merge(stat, 1, Integer::sum);
        profiles.save(profile);
        apply(player);
        return true;
    }

    private void scalar(Player player, Attribute attribute, NamespacedKey key, double amount) {
        modifier(player, attribute, key, amount, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    }

    private void additive(Player player, Attribute attribute, NamespacedKey key, double amount) {
        modifier(player, attribute, key, amount, AttributeModifier.Operation.ADD_NUMBER);
    }

    private void modifier(Player player, Attribute attribute, NamespacedKey key,
            double amount, AttributeModifier.Operation operation) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst == null) {
            return;
        }
        inst.removeModifier(key);
        if (amount != 0.0) {
            inst.addModifier(new AttributeModifier(key, amount, operation));
        }
    }
}
