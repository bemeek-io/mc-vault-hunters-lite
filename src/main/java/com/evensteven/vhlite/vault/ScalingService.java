package com.evensteven.vhlite.vault;

import com.evensteven.vhlite.util.Keys;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Difficulty scaling as idempotent attribute modifiers (remove-then-add
 * keyed by NamespacedKey, the sibling project's proven pattern). Mob
 * strength grows with crystal level and party size; run modifiers multiply
 * on top; bosses get a chunky premium.
 */
public final class ScalingService {

    private final FileConfiguration config;

    public ScalingService(FileConfiguration config) {
        this.config = config;
    }

    public void scaleMob(LivingEntity mob, VaultBlueprint bp, boolean boss) {
        double levelHp = bp.level() * config.getDouble("vault.mob-health-per-level", 0.12);
        double partyHp = (bp.partySize() - 1) * config.getDouble("vault.mob-health-per-extra-player", 0.35);
        double hpScalar = (1.0 + levelHp + partyHp) * bp.modifierProduct(m -> m.mobHealthMult) - 1.0;
        if (boss) {
            hpScalar = (1.0 + hpScalar) * 4.0 - 1.0;
        }
        double levelDmg = bp.level() * config.getDouble("vault.mob-damage-per-level", 0.08);
        double partyDmg = (bp.partySize() - 1) * config.getDouble("vault.mob-damage-per-extra-player", 0.10);
        double dmgScalar = (1.0 + levelDmg + partyDmg) * bp.modifierProduct(m -> m.mobDamageMult) - 1.0;
        if (boss) {
            dmgScalar = (1.0 + dmgScalar) * 1.5 - 1.0;
        }
        double speedScalar = bp.modifierProduct(m -> m.mobSpeedMult) - 1.0;

        applyScalar(mob, Attribute.MAX_HEALTH, Keys.MOB_HEALTH, hpScalar);
        applyScalar(mob, Attribute.ATTACK_DAMAGE, Keys.MOB_DAMAGE, dmgScalar);
        applyScalar(mob, Attribute.MOVEMENT_SPEED, Keys.MOB_SPEED, speedScalar);

        AttributeInstance health = mob.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            mob.setHealth(health.getValue()); // fresh spawn starts full
        }
    }

    /**
     * Per-encounter archetype tweak on top of the run scaling: hordes trade
     * per-mob power for numbers, elite squads the reverse.
     */
    public void tweakMob(LivingEntity mob, double healthMult, double damageMult) {
        applyScalar(mob, Attribute.MAX_HEALTH, com.evensteven.vhlite.util.Keys.of("mob_tweak_health"),
                healthMult - 1.0);
        applyScalar(mob, Attribute.ATTACK_DAMAGE, com.evensteven.vhlite.util.Keys.of("mob_tweak_damage"),
                damageMult - 1.0);
        AttributeInstance health = mob.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            mob.setHealth(health.getValue());
        }
    }

    /** RUSH gives runners quicker feet for the duration; removed on exit. */
    public void applyPlayerSpeed(Player player, VaultBlueprint bp) {
        double bonus = 0.0;
        for (var modifier : bp.modifiers()) {
            bonus += modifier.playerSpeedBonus;
        }
        if (bonus > 0) {
            applyScalar(player, Attribute.MOVEMENT_SPEED, Keys.RUSH_SPEED, bonus);
        }
    }

    public void clearPlayerSpeed(Player player) {
        AttributeInstance inst = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (inst != null) {
            inst.removeModifier(Keys.RUSH_SPEED);
        }
    }

    private void applyScalar(LivingEntity entity, Attribute attribute, NamespacedKey key, double amount) {
        AttributeInstance inst = entity.getAttribute(attribute);
        if (inst == null) {
            return;
        }
        inst.removeModifier(key);
        if (amount != 0.0) {
            inst.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }
}
