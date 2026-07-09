package com.evensteven.vhlite.skills;

import com.evensteven.vhlite.item.VhItemType;
import com.evensteven.vhlite.knowledge.ResearchNode;
import com.evensteven.vhlite.player.PartyService;
import com.evensteven.vhlite.player.ProfileStore;
import com.evensteven.vhlite.util.Text;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * The three active abilities — deliberately few, on chunky cooldowns, so
 * stat investment stays the star. Ability items are ordinary tagged items
 * on distinct materials; Player#setCooldown gives a native client cooldown
 * overlay for free. Using one requires having researched it.
 */
public final class AbilityService {

    private final ProfileStore profiles;
    private final PartyService parties;

    public AbilityService(ProfileStore profiles, PartyService parties) {
        this.profiles = profiles;
        this.parties = parties;
    }

    /** @return true if the event should be cancelled (it was an ability use). */
    public boolean use(Player player, VhItemType type) {
        ResearchNode required = switch (type) {
            case ABILITY_HEAL -> ResearchNode.ABILITY_HEAL;
            case ABILITY_DASH -> ResearchNode.ABILITY_DASH;
            case ABILITY_WARCRY -> ResearchNode.ABILITY_WARCRY;
            default -> null;
        };
        if (required == null) {
            return false;
        }
        if (!profiles.get(player).has(required)) {
            player.sendMessage(Text.c("§cYou haven't researched this ability. §7(/vh knowledge)"));
            return true;
        }
        if (player.getCooldown(type.material) > 0) {
            return true; // still recharging; the client shows the overlay
        }
        switch (type) {
            case ABILITY_HEAL -> heal(player);
            case ABILITY_DASH -> dash(player);
            case ABILITY_WARCRY -> warcry(player);
            default -> {
            }
        }
        return true;
    }

    private void heal(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        double cap = max != null ? max.getValue() : 20.0;
        player.setHealth(Math.min(cap, player.getHealth() + 8.0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 5 * 20, 0));
        player.setCooldown(VhItemType.ABILITY_HEAL.material, 60 * 20);
        player.playSound(player.getLocation(), Sound.ITEM_HONEY_BOTTLE_DRINK, 1f, 1.4f);
        player.sendMessage(Text.c("§aThe balm knits your wounds."));
    }

    private void dash(Player player) {
        Vector direction = player.getLocation().getDirection().normalize().multiply(1.7);
        direction.setY(Math.max(0.3, direction.getY() * 0.5 + 0.3));
        player.setVelocity(direction);
        player.setFallDistance(-4f); // the landing is part of the ability
        player.setCooldown(VhItemType.ABILITY_DASH.material, 15 * 20);
        player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1f, 1.6f);
    }

    private void warcry(Player player) {
        for (Player member : parties.membersOf(player)) {
            if (!member.getWorld().equals(player.getWorld())
                    || member.getLocation().distanceSquared(player.getLocation()) > 10 * 10) {
                continue;
            }
            member.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 10 * 20, 0));
            member.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 10 * 20, 0));
            member.sendMessage(Text.c("§c" + player.getName() + "'s warcry hardens your resolve!"));
        }
        player.setCooldown(VhItemType.ABILITY_WARCRY.material, 90 * 20);
        player.playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1.2f);
    }
}
