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

    public PlayerLifecycleListener(ProfileStore profiles, StatService stats,
            RecipeService recipes, PartyService parties) {
        this.profiles = profiles;
        this.stats = stats;
        this.recipes = recipes;
        this.parties = parties;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        profiles.get(event.getPlayer());
        stats.apply(event.getPlayer());
        recipes.syncDiscovered(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        parties.handleQuit(event.getPlayer());
        profiles.unload(event.getPlayer().getUniqueId());
    }
}
