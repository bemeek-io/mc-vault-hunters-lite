package com.evensteven.vhlite.player;

import com.evensteven.vhlite.item.VhItemType;
import com.evensteven.vhlite.item.VhItems;
import com.evensteven.vhlite.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Vault Essence and Vault Gold: currencies tied to the player PROFILE, not
 * the inventory — no slots, no drop-on-death risk, always visible on the
 * HUD and stats sheet. Essence is flat (earned mainly from vault kills).
 * Gold is tiered copper/silver/gold/platinum (9 of one = 1 of the next,
 * found in vault loot) but stored as one flat copper count; formatting
 * breaks it back into tiers for display only.
 */
public final class CurrencyService {

    private static final long SILVER = 9;
    private static final long GOLD = SILVER * 9;
    private static final long PLATINUM = GOLD * 9;

    private final ProfileStore profiles;

    public CurrencyService(ProfileStore profiles) {
        this.profiles = profiles;
    }

    // ------------------------------------------------------------ essence

    public long essenceOf(Player player) {
        return profiles.get(player).vaultEssence;
    }

    public void addEssence(Player player, long amount) {
        addEssence(player, amount, true);
    }

    public void addEssence(Player player, long amount, boolean toast) {
        if (amount <= 0) {
            return;
        }
        PlayerProfile profile = profiles.get(player);
        profile.vaultEssence += amount;
        profiles.save(profile);
        if (toast) {
            player.sendActionBar(Text.c("§3+" + amount + " Vault Essence"));
            // The action bar text gets stomped within a second by the vault's
            // own objective-progress line during a run, so the SOUND — not
            // the text — is the reliable "you got essence" signal in combat.
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.6f);
        }
    }

    public boolean spendEssence(Player player, long amount) {
        PlayerProfile profile = profiles.get(player);
        if (profile.vaultEssence < amount) {
            return false;
        }
        profile.vaultEssence -= amount;
        profiles.save(profile);
        return true;
    }

    // --------------------------------------------------------------- gold

    public long goldOf(Player player) {
        return profiles.get(player).vaultGoldCopper;
    }

    public void addGold(Player player, long copper) {
        if (copper <= 0) {
            return;
        }
        PlayerProfile profile = profiles.get(player);
        profile.vaultGoldCopper += copper;
        profiles.save(profile);
        player.sendActionBar(Text.c("§6+" + formatGold(copper) + " §6found!"));
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.8f);
    }

    public boolean spendGold(Player player, long copper) {
        PlayerProfile profile = profiles.get(player);
        if (profile.vaultGoldCopper < copper) {
            return false;
        }
        profile.vaultGoldCopper -= copper;
        profiles.save(profile);
        return true;
    }

    /** Full tiered breakdown, e.g. "§f1p §62g §75s §c3c". Zero tiers are omitted. */
    public String formatGold(long copper) {
        if (copper <= 0) {
            return "§c0c";
        }
        long platinum = copper / PLATINUM;
        long remainder = copper % PLATINUM;
        long gold = remainder / GOLD;
        remainder %= GOLD;
        long silver = remainder / SILVER;
        long bronze = remainder % SILVER;

        StringBuilder sb = new StringBuilder();
        if (platinum > 0) {
            sb.append("§f").append(platinum).append("p ");
        }
        if (gold > 0) {
            sb.append("§6").append(gold).append("g ");
        }
        if (silver > 0) {
            sb.append("§7").append(silver).append("s ");
        }
        if (bronze > 0 || sb.isEmpty()) {
            sb.append("§c").append(bronze).append("c");
        }
        return sb.toString().trim();
    }

    /** Single highest-tier form for tight HUD space, e.g. "§f1p" or "§75s". */
    public String formatGoldShort(long copper) {
        if (copper >= PLATINUM) {
            return "§f" + (copper / PLATINUM) + "p";
        }
        if (copper >= GOLD) {
            return "§6" + (copper / GOLD) + "g";
        }
        if (copper >= SILVER) {
            return "§7" + (copper / SILVER) + "s";
        }
        return "§c" + copper + "c";
    }

    // --------------------------------------------------------- migration

    /**
     * One-time sweep for players who logged out before this update with
     * physical Vault Essence items sitting in their inventory: convert
     * them to currency so nothing is lost. New essence is never a
     * physical item, so this only ever finds pre-update leftovers.
     */
    public void migrateLegacyEssenceItems(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        long found = 0;
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && VhItems.typeOf(item) == VhItemType.VAULT_ESSENCE) {
                found += item.getAmount();
                player.getInventory().setItem(i, null);
            }
        }
        if (found > 0) {
            addEssence(player, found, false);
            player.sendMessage(Text.c("§7Converted §3" + found
                    + " old Vault Essence §7item(s) into currency."));
        }
    }
}
