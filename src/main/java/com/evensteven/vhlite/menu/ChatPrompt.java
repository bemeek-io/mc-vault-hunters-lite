package com.evensteven.vhlite.menu;

import com.evensteven.vhlite.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Text input for vanilla clients: closes the player's menu, captures their
 * next chat line (never broadcast), and hands it back on the main thread.
 * Typing "cancel" or waiting 30 seconds aborts. Signs and anvils would need
 * packet tricks; chat is the one input box every client already has.
 */
public final class ChatPrompt implements Listener {

    private record Pending(Consumer<String> onAnswer, BukkitTask timeout) {
    }

    private final Plugin plugin;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public ChatPrompt(Plugin plugin) {
        this.plugin = plugin;
    }

    public void prompt(Player player, String question, Consumer<String> onAnswer) {
        player.closeInventory();
        player.sendMessage(Text.c(question));
        player.sendMessage(Text.c("§7Type your answer in chat, or §ccancel§7. (30s)"));
        UUID id = player.getUniqueId();
        cancel(id);
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pending.remove(id) != null) {
                Player online = Bukkit.getPlayer(id);
                if (online != null) {
                    online.sendMessage(Text.c("§7Prompt timed out."));
                }
            }
        }, 30L * 20L);
        pending.put(id, new Pending(onAnswer, timeout));
    }

    private void cancel(UUID id) {
        Pending old = pending.remove(id);
        if (old != null) {
            old.timeout.cancel();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Pending entry = pending.get(id);
        if (entry == null) {
            return;
        }
        event.setCancelled(true);
        cancel(id);
        String answer = Text.plain(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                return;
            }
            if (answer.equalsIgnoreCase("cancel")) {
                player.sendMessage(Text.c("§7Cancelled."));
                return;
            }
            entry.onAnswer().accept(answer);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId());
    }
}
