package com.evensteven.vhlite.menu;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * A 6-row menu whose top 5 rows page through a list of entries; the bottom
 * row holds prev/next arrows and an info item. Subclasses supply the entries
 * and what a click on one does.
 */
public abstract class PagedMenu<T> extends Menu {

    protected static final int PAGE_SIZE = 45;
    private int page;

    protected PagedMenu(Player viewer, String title) {
        super(viewer, 6, title);
    }

    /** The full list being paged. Re-queried on every build. */
    protected abstract List<T> entries();

    /** The icon shown for one entry. */
    protected abstract ItemStack iconFor(T entry);

    /** What clicking an entry does. */
    protected abstract void onEntryClick(T entry, InventoryClickEvent event);

    protected String infoLine() {
        return "";
    }

    @Override
    protected final void build() {
        List<T> all = entries();
        int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(page, pages - 1);

        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = page * PAGE_SIZE + i;
            if (index >= all.size()) {
                break;
            }
            T entry = all.get(index);
            button(i, iconFor(entry), event -> onEntryClick(entry, event));
        }

        if (page > 0) {
            button(45, named(Material.ARROW, "§ePrevious page"), event -> {
                page--;
                refresh();
            });
        }
        if (page < pages - 1) {
            button(53, named(Material.ARROW, "§eNext page"), event -> {
                page++;
                refresh();
            });
        }
        icon(49, named(Material.OAK_SIGN, "§7Page §e" + (page + 1) + "§7/§e" + pages,
                infoLine().isEmpty() ? new String[0] : new String[] {infoLine()}));
        buildExtras();
        fillRow(5, Material.GRAY_STAINED_GLASS_PANE);
    }

    /** Hook for subclasses to add bottom-row buttons after paging is laid out. */
    protected void buildExtras() {
    }
}
