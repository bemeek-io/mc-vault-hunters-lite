package com.evensteven.vhlite.item;

import com.evensteven.vhlite.util.Keys;
import com.evensteven.vhlite.util.Text;
import com.evensteven.vhlite.vault.modifier.VaultModifier;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Factory and identifier for all custom items. {@code typeOf} is the single
 * identification path every listener uses.
 */
public final class VhItems {

    private VhItems() {
    }

    public static ItemStack create(VhItemType type) {
        ItemStack item = new ItemStack(type.material);
        item.editMeta(meta -> {
            meta.displayName(Text.item(type.displayName));
            meta.lore(Text.lore(type.lore));
            // Pinned per-type id a server resource pack can retexture against.
            meta.setCustomModelData(type.modelData);
            meta.getPersistentDataContainer().set(Keys.ITEM_TYPE, PersistentDataType.STRING, type.name());
        });
        return item;
    }

    /** Which custom item this stack is, or null for plain items. */
    public static VhItemType typeOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer()
                .get(Keys.ITEM_TYPE, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        try {
            return VhItemType.valueOf(id);
        } catch (IllegalArgumentException ex) {
            return null; // tag from a newer/older plugin version
        }
    }

    // ------------------------------------------------------------- crystals

    public static ItemStack crystal(int level, List<VaultModifier> forced) {
        ItemStack item = create(VhItemType.VAULT_CRYSTAL);
        item.editMeta(meta -> {
            meta.displayName(Text.item("§dVault Crystal §7(Level " + level + ")"));
            List<Component> lore = new ArrayList<>(Text.lore(VhItemType.VAULT_CRYSTAL.lore));
            for (VaultModifier mod : forced) {
                lore.add(Text.item("§5• " + mod.displayName));
            }
            meta.lore(lore);
            meta.getPersistentDataContainer().set(Keys.CRYSTAL_LEVEL, PersistentDataType.INTEGER, level);
            if (!forced.isEmpty()) {
                meta.getPersistentDataContainer().set(Keys.CRYSTAL_MODIFIERS, PersistentDataType.STRING,
                        String.join(",", forced.stream().map(Enum::name).toList()));
            }
        });
        return item;
    }

    public static int crystalLevel(ItemStack crystal) {
        Integer level = crystal.getItemMeta().getPersistentDataContainer()
                .get(Keys.CRYSTAL_LEVEL, PersistentDataType.INTEGER);
        return level == null ? 1 : level;
    }

    public static List<VaultModifier> crystalModifiers(ItemStack crystal) {
        String raw = crystal.getItemMeta().getPersistentDataContainer()
                .get(Keys.CRYSTAL_MODIFIERS, PersistentDataType.STRING);
        List<VaultModifier> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String name : raw.split(",")) {
            try {
                out.add(VaultModifier.valueOf(name));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return out;
    }

    // ------------------------------------------------------------ catalysts

    public static ItemStack catalyst(VaultModifier modifier) {
        ItemStack item = create(VhItemType.CATALYST);
        item.editMeta(meta -> {
            meta.displayName(Text.item("§5Catalyst: " + modifier.displayName));
            meta.lore(Text.lore("§7Craft together with a Vault Crystal",
                    "§7to force: §5" + modifier.displayName, "§8" + modifier.description));
            meta.getPersistentDataContainer().set(Keys.CATALYST_MODIFIER, PersistentDataType.STRING, modifier.name());
        });
        return item;
    }

    public static VaultModifier catalystModifier(ItemStack catalyst) {
        String name = catalyst.getItemMeta().getPersistentDataContainer()
                .get(Keys.CATALYST_MODIFIER, PersistentDataType.STRING);
        if (name == null) {
            return null;
        }
        try {
            return VaultModifier.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // ------------------------------------------------------------ backpacks

    public static ItemStack backpack() {
        ItemStack item = create(VhItemType.BACKPACK);
        item.editMeta(meta -> meta.getPersistentDataContainer()
                .set(Keys.BACKPACK_ID, PersistentDataType.STRING, UUID.randomUUID().toString()));
        return item;
    }

    public static UUID backpackId(ItemStack backpack) {
        String id = backpack.getItemMeta().getPersistentDataContainer()
                .get(Keys.BACKPACK_ID, PersistentDataType.STRING);
        return id == null ? null : UUID.fromString(id);
    }

    // -------------------------------------------------------------- helpers

    /** Removes {@code amount} custom items of the given type; false if short. */
    public static boolean consume(Player player, VhItemType type, int amount) {
        ItemStack[] contents = player.getInventory().getContents();
        int found = 0;
        for (ItemStack item : contents) {
            if (item != null && typeOf(item) == type) {
                found += item.getAmount();
            }
        }
        if (found < amount) {
            return false;
        }
        int remaining = amount;
        for (ItemStack item : contents) {
            if (remaining <= 0) {
                break;
            }
            if (item != null && typeOf(item) == type) {
                int take = Math.min(remaining, item.getAmount());
                item.setAmount(item.getAmount() - take);
                remaining -= take;
            }
        }
        return true;
    }

    public static int count(Player player, VhItemType type) {
        int found = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && typeOf(item) == type) {
                found += item.getAmount();
            }
        }
        return found;
    }

    public static void give(Player player, ItemStack... items) {
        for (ItemStack item : items) {
            for (ItemStack rest : player.getInventory().addItem(item).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
    }
}
