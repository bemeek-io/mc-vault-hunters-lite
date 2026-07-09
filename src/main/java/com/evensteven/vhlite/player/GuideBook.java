package com.evensteven.vhlite.player;

import com.evensteven.vhlite.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.List;

/**
 * The Vault Hunter's Guide: a written book handed to every player on their
 * first join (and again via /vh guide). Teaches the whole loop in-game so
 * nobody needs a wiki.
 */
public final class GuideBook {

    private GuideBook() {
    }

    public static ItemStack create() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        book.editMeta(meta -> {
            BookMeta bookMeta = (BookMeta) meta;
            bookMeta.setTitle("Vault Hunter's Guide");
            bookMeta.setAuthor("The Vault");
            bookMeta.pages(List.of(
                    page("""
                            §5§lVault Hunter's Guide§0

                            The vaults hold riches, gear, and knowledge — if you can get back out.

                            §7Everything here is tracked in §0/vh§7 — quests, stats, skills, and more."""),
                    page("""
                            §5§l1. The Altar§0

                            Craft a §5Vault Altar§0: an amethyst shard over a diamond, ringed in stone bricks. Place it, right-click it, and it names its price.

                            Gather what it asks and click §dInfuse§0 to forge a §dVault Crystal§0."""),
                    page("""
                            §5§l2. The Vault§0

                            Right-click the altar holding your crystal. Inside: a time limit (boss bar), an objective, and a §dVault Map§0 that charts rooms as you explore.

                            Finish the objective to open the glowing §dexit pad§0 — step on it to leave with everything."""),
                    page("""
                            §5§l3. Death & Time§0

                            §cNo natural healing inside.§0 Bring food, potions, and nerve.

                            Dying or running out of time ends your run. Low-level vaults forgive it; higher vaults trap your items in a §3Spirit§0 — buy it back at the altar with §3Vault Essence§0 from vault mobs and chests."""),
                    page("""
                            §5§l4. Growing§0

                            Vault XP fills the bar above your hotbar. Levels grant §eskill points§0 (§0/vh skills§0: Strength, Vitality, Swiftness, Fortune, Resilience) and §bknowledge points§0 (§0/vh knowledge§0: backpacks, chest linking, abilities, catalysts)."""),
                    page("""
                            §5§l5. Vaultforged Gear§0

                            The vaults hide weapons and armor you cannot craft — they outclass vanilla gear fast, and higher-level vaults drop stronger pieces. Hunt bosses: they always yield one.

                            Higher vaults hit MUCH harder. Upgrade or die."""),
                    page("""
                            §5§l6. Storage & Party§0

                            Research §dChest Linking§0, craft a Link Wand, and link your chests into one searchable screen (§0/vh storage§0).

                            Raid together: §0/vh party invite <friend>§0 — everyone shares the vault, the map, and the loot.

                            Start with §0/vh quests§0!""")));
        });
        return book;
    }

    private static Component page(String legacy) {
        return Text.c(legacy);
    }
}
