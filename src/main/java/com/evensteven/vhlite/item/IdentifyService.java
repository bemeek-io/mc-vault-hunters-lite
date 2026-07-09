package com.evensteven.vhlite.item;

import com.evensteven.vhlite.quest.QuestService;
import com.evensteven.vhlite.quest.QuestType;
import com.evensteven.vhlite.util.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The identification ritual: right-click an Unidentified piece and a
 * 2.5-second slot-machine spins on the action bar — affix names flickering
 * past with a rising chime — before the real item lands in your hands.
 * Cosmetic, but it makes every drop a little ceremony.
 */
public final class IdentifyService {

    private final Plugin plugin;
    private final QuestService quests;
    private final Random random = new Random();
    private final Set<UUID> identifying = new HashSet<>();

    public IdentifyService(Plugin plugin, QuestService quests) {
        this.plugin = plugin;
        this.quests = quests;
    }

    public void begin(Player player, ItemStack sealedStack) {
        if (!identifying.add(player.getUniqueId())) {
            player.sendMessage(Text.c("§7One at a time — the vault is still whispering."));
            return;
        }
        ItemStack sealed = sealedStack.clone();
        sealed.setAmount(1);
        sealedStack.setAmount(sealedStack.getAmount() - 1);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 0.8f);

        new BukkitRunnable() {
            private static final int SPIN_TICKS = 50;
            private int ticks;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    identifying.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                if (ticks < SPIN_TICKS) {
                    // The reel: a different affix name every frame, pitch rising.
                    player.sendActionBar(Text.c("§5Identifying... §r"
                            + VaultGear.randomAffixName(random) + "§5?"));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING,
                            0.7f, 0.6f + ticks * 0.028f);
                    ticks += 5;
                    return;
                }
                cancel();
                identifying.remove(player.getUniqueId());
                ItemStack revealed = VaultGear.identify(sealed, random);
                VhItems.give(player, revealed);
                String name = Text.plain(revealed.getItemMeta().displayName());
                player.sendActionBar(Text.c("§d✦ " + name + " §d✦"));
                player.showTitle(Title.title(Text.c(""), Text.c("§d✦ " + name + " §d✦"),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(500))));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.7f);
                quests.progress(player, QuestType.VAULTFORGED, 1);
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }
}
