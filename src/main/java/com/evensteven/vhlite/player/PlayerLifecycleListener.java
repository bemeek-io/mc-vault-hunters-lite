package com.evensteven.vhlite.player;

import com.evensteven.vhlite.item.RecipeService;
import com.evensteven.vhlite.skills.StatService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Join: load the profile, re-apply stat modifiers (attribute modifiers are
 * saved with the player but reapplying keeps them honest with config), and
 * reveal researched recipes. Quit: save + drop the cache, tidy the party.
 */
public final class PlayerLifecycleListener implements Listener {

    private final ProfileStore profiles;
    private final StatService stats;
    private final RecipeService recipes;
    private final PartyService parties;
    private final com.evensteven.vhlite.quest.QuestService quests;

    public PlayerLifecycleListener(ProfileStore profiles, StatService stats,
            RecipeService recipes, PartyService parties,
            com.evensteven.vhlite.quest.QuestService quests) {
        this.profiles = profiles;
        this.stats = stats;
        this.recipes = recipes;
        this.parties = parties;
        this.quests = quests;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerProfile profile = profiles.get(event.getPlayer());
        stats.apply(event.getPlayer());
        recipes.syncDiscovered(event.getPlayer());
        quests.syncLevelMilestones(event.getPlayer());
        if (!profile.guideGiven) {
            profile.guideGiven = true;
            profiles.save(profile);
            com.evensteven.vhlite.item.VhItems.give(event.getPlayer(), GuideBook.create());
            event.getPlayer().sendMessage(com.evensteven.vhlite.util.Text.c(
                    "§5The vaults are waiting. §7Read your §dVault Hunter's Guide§7,"
                            + " or type §e/vh quests§7 to be led there."));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        parties.handleQuit(event.getPlayer());
        profiles.unload(event.getPlayer().getUniqueId());
    }
}
