package com.evensteven.vhlite.storage;

import com.evensteven.vhlite.item.VhItems;
import com.evensteven.vhlite.menu.ChatPrompt;
import com.evensteven.vhlite.menu.PagedMenu;
import com.evensteven.vhlite.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The searchable one-view interface over every linked chest: items are
 * aggregated by kind, clicking withdraws a stack, the hopper deposits
 * everything that matches something already stored, and the spyglass
 * filters by name (chat input — vanilla clients have no search box).
 * Contents are read live only while the menu is open; nothing is indexed
 * in the background.
 */
public final class StorageMenu extends PagedMenu<StorageMenu.Aggregate> {

    /** One kind of item across the network and everywhere it lives. */
    record Slice(Location chest, int slot, int amount) {
    }

    public static final class Aggregate {
        final ItemStack sample;
        int total;
        final List<Slice> slices = new ArrayList<>();

        Aggregate(ItemStack sample) {
            this.sample = sample.asOne();
        }
    }

    private final ChestLinkService links;
    private final ChatPrompt prompts;
    private final String filter;

    public StorageMenu(Player viewer, ChestLinkService links, ChatPrompt prompts, String filter) {
        super(viewer, filter.isEmpty() ? "§6Storage Network" : "§6Storage §7— \"" + filter + "\"");
        this.links = links;
        this.prompts = prompts;
        this.filter = filter.toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------ aggregate

    private String keyOf(ItemStack item) {
        ItemStack one = item.asOne();
        Component name = one.hasItemMeta() && one.getItemMeta().hasDisplayName()
                ? one.getItemMeta().displayName() : null;
        return one.getType().name() + "|" + (name == null ? "" : Text.plain(name));
    }

    private String displayNameOf(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return Text.plain(item.getItemMeta().displayName());
        }
        return item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    @Override
    protected List<Aggregate> entries() {
        Map<String, Aggregate> byKind = new LinkedHashMap<>();
        for (Location loc : links.liveNetwork(viewer)) {
            if (!(loc.getBlock().getState() instanceof Container container)) {
                continue;
            }
            Inventory inv = container.getInventory();
            for (int slot = 0; slot < inv.getSize(); slot++) {
                ItemStack item = inv.getItem(slot);
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                if (!filter.isEmpty()
                        && !displayNameOf(item).toLowerCase(Locale.ROOT).contains(filter)) {
                    continue;
                }
                Aggregate agg = byKind.computeIfAbsent(keyOf(item), k -> new Aggregate(item));
                agg.total += item.getAmount();
                agg.slices.add(new Slice(loc, slot, item.getAmount()));
            }
        }
        List<Aggregate> out = new ArrayList<>(byKind.values());
        out.sort((a, b) -> Integer.compare(b.total, a.total));
        return out;
    }

    @Override
    protected ItemStack iconFor(Aggregate agg) {
        ItemStack icon = agg.sample.clone();
        icon.setAmount(Math.max(1, Math.min(64, agg.total)));
        icon.editMeta(meta -> {
            List<Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
            lore.add(Text.item("§7Stored: §e" + agg.total + " §7in §e" + agg.slices.size() + "§7 stack(s)"));
            lore.add(Text.item("§aClick to withdraw a stack."));
            meta.lore(lore);
        });
        return icon;
    }

    @Override
    protected void onEntryClick(Aggregate agg, InventoryClickEvent event) {
        int want = Math.min(agg.sample.getMaxStackSize(), agg.total);
        int gathered = 0;
        for (Slice slice : agg.slices) {
            if (gathered >= want) {
                break;
            }
            if (!(slice.chest().getBlock().getState() instanceof Container container)) {
                continue;
            }
            ItemStack inSlot = container.getInventory().getItem(slice.slot());
            if (inSlot == null || !inSlot.isSimilar(agg.sample)) {
                continue; // the chest changed under us; skip stale slice
            }
            int take = Math.min(inSlot.getAmount(), want - gathered);
            inSlot.setAmount(inSlot.getAmount() - take);
            container.getInventory().setItem(slice.slot(), inSlot.getAmount() <= 0 ? null : inSlot);
            gathered += take;
        }
        if (gathered > 0) {
            ItemStack given = agg.sample.clone();
            given.setAmount(gathered);
            VhItems.give(viewer, given);
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1f);
        }
        refresh();
    }

    @Override
    protected void buildExtras() {
        button(46, named(Material.SPYGLASS, "§eSearch",
                        "§7Filter the view by item name.", "§8Answer in chat."),
                event -> prompts.prompt(viewer, "§eWhat are you looking for?", answer ->
                        new StorageMenu(viewer, links, prompts, answer).open(viewer)));
        if (!filter.isEmpty()) {
            button(47, named(Material.BARRIER, "§7Clear search"),
                    event -> new StorageMenu(viewer, links, prompts, "").open(viewer));
        }
        button(52, named(Material.HOPPER, "§bDeposit matching",
                        "§7Moves everything in your inventory that", "§7matches an item already stored."),
                event -> depositMatching());
    }

    @Override
    protected String infoLine() {
        return "§7Link more chests with the §eLink Wand§7.";
    }

    private void depositMatching() {
        List<Aggregate> stored = entries();
        int moved = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = viewer.getInventory().getItem(slot);
            if (item == null || item.getType().isAir() || VhItems.typeOf(item) != null) {
                continue; // custom items stay with the player
            }
            Aggregate match = null;
            for (Aggregate agg : stored) {
                if (agg.sample.isSimilar(item)) {
                    match = agg;
                    break;
                }
            }
            if (match == null) {
                continue;
            }
            // Prefer chests already holding this kind.
            for (Slice slice : match.slices) {
                if (slice.chest().getBlock().getState() instanceof Container container) {
                    Map<Integer, ItemStack> leftover = container.getInventory().addItem(item);
                    int before = item.getAmount();
                    ItemStack rest = leftover.isEmpty() ? null : leftover.values().iterator().next();
                    int after = rest == null ? 0 : rest.getAmount();
                    moved += before - after;
                    viewer.getInventory().setItem(slot, rest);
                    item = rest;
                    if (item == null) {
                        break;
                    }
                }
            }
        }
        viewer.sendMessage(Text.c(moved > 0 ? "§bStashed §e" + moved + "§b items." : "§7Nothing matched."));
        if (moved > 0) {
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_ITEM_FRAME_ADD_ITEM, 1f, 0.9f);
        }
        refresh();
    }
}
