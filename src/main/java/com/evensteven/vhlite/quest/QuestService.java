package com.evensteven.vhlite.quest;

import com.evensteven.vhlite.item.VaultGear;
import com.evensteven.vhlite.item.VhItemType;
import com.evensteven.vhlite.item.VhItems;
import com.evensteven.vhlite.player.PlayerProfile;
import com.evensteven.vhlite.player.ProfileStore;
import com.evensteven.vhlite.util.Text;
import com.evensteven.vhlite.vault.modifier.VaultModifier;
import com.evensteven.vhlite.vault.objective.VaultObjective;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;

import java.util.Random;

/**
 * Quest bookkeeping: hooks all over the plugin report progress here;
 * completion pays the reward, announces it, and slides the unlock window
 * (three quests visible at a time) further down the chain.
 */
public final class QuestService implements Listener {

    /** How many not-yet-completed quests are active at once. */
    private static final int WINDOW = 3;

    private final ProfileStore profiles;
    private final com.evensteven.vhlite.player.CurrencyService currency;
    private final Random random = new Random();

    public QuestService(ProfileStore profiles, com.evensteven.vhlite.player.CurrencyService currency) {
        this.profiles = profiles;
        this.currency = currency;
    }

    public boolean isUnlocked(PlayerProfile profile, QuestType quest) {
        return quest.ordinal() < profile.questsCompleted.size() + WINDOW;
    }

    public boolean isComplete(PlayerProfile profile, QuestType quest) {
        return profile.questsCompleted.contains(quest.name());
    }

    public int progressOf(PlayerProfile profile, QuestType quest) {
        return profile.questProgress.getOrDefault(quest.name(), 0);
    }

    /** Additive progress (kills, chests, one-shot deeds). */
    public void progress(Player player, QuestType quest, int amount) {
        PlayerProfile profile = profiles.get(player);
        if (isComplete(profile, quest)) {
            return;
        }
        profile.questProgress.merge(quest.name(), amount, Integer::sum);
        settle(player, profile);
    }

    /** High-water progress (level milestones). */
    public void progressTo(Player player, QuestType quest, int value) {
        PlayerProfile profile = profiles.get(player);
        if (isComplete(profile, quest)) {
            return;
        }
        profile.questProgress.merge(quest.name(), value, Math::max);
        settle(player, profile);
    }

    /** SPECIALIST: one tick per distinct objective type ever completed. */
    public void progressObjectiveType(Player player, VaultObjective objective) {
        PlayerProfile profile = profiles.get(player);
        String seenKey = "SPECIALIST_" + objective.name();
        if (profile.questProgress.putIfAbsent(seenKey, 1) == null) {
            progress(player, QuestType.SPECIALIST, 1);
        }
    }

    /** Level milestones can be satisfied retroactively (e.g. on join). */
    public void syncLevelMilestones(Player player) {
        int level = profiles.get(player).vaultLevel;
        progressTo(player, QuestType.APPRENTICE, level);
        progressTo(player, QuestType.JOURNEYMAN, level);
        progressTo(player, QuestType.MASTER, level);
    }

    /** Completes everything that's earned; repeats while the window slides. */
    private void settle(Player player, PlayerProfile profile) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (QuestType quest : QuestType.values()) {
                if (!isComplete(profile, quest) && isUnlocked(profile, quest)
                        && progressOf(profile, quest) >= quest.target) {
                    profile.questsCompleted.add(quest.name());
                    reward(player, profile, quest);
                    announce(player, quest);
                    changed = true;
                }
            }
        }
        profiles.save(profile);
    }

    private void announce(Player player, QuestType quest) {
        player.showTitle(Title.title(Text.c("§6Quest complete!"), Text.c("§e" + quest.displayName)));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.3f);
        player.sendMessage(Text.c("§6✦ Quest complete: §e" + quest.displayName
                + " §7— reward: §f" + quest.rewardText + "§7. More await in §e/vh quests§7."));
    }

    private void reward(Player player, PlayerProfile profile, QuestType quest) {
        switch (quest) {
            case BUILD_ALTAR -> giveEssence(player, 4);
            case FORGE_CRYSTAL -> profile.knowledgePoints += 1;
            case FIRST_DELVE, SCHOLAR -> VhItems.give(player, VhItems.create(VhItemType.KNOWLEDGE_STAR));
            case LOOTER -> giveEssence(player, 8);
            case CULLER -> giveCatalyst(player);
            case APPRENTICE -> profile.skillPoints += 2;
            case VAULTFORGED -> VhItems.give(player,
                    VaultGear.unidentified(Math.max(1, profile.vaultLevel), random, 0.3));
            case JOURNEYMAN -> giveStars(player, 2);
            case SPECIALIST -> VhItems.give(player,
                    VaultGear.unidentified(Math.max(1, profile.vaultLevel), random, 1.0));
            case GUARDIAN_BANE -> {
                giveEssence(player, 16);
                giveCatalyst(player);
            }
            case MASTER -> {
                giveStars(player, 3);
                VhItems.give(player, VaultGear.unidentified(Math.max(1, profile.vaultLevel), random, 1.0));
            }
        }
    }

    private void giveEssence(Player player, int amount) {
        currency.addEssence(player, amount, false); // the quest-complete toast already fires
    }

    private void giveStars(Player player, int amount) {
        for (int i = 0; i < amount; i++) {
            VhItems.give(player, VhItems.create(VhItemType.KNOWLEDGE_STAR));
        }
    }

    private void giveCatalyst(Player player) {
        VhItems.give(player, VhItems.catalyst(
                VaultModifier.values()[random.nextInt(VaultModifier.values().length)]));
    }

    // The one hook that needs its own listener: picking up vault gear.
    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && VhItems.typeOf(event.getItem().getItemStack()) == VhItemType.VAULT_GEAR) {
            progress(player, QuestType.VAULTFORGED, 1);
        }
    }
}
