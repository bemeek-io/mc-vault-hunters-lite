package com.evensteven.vhlite.item;

import com.evensteven.vhlite.util.Keys;
import com.evensteven.vhlite.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Vaultforged gear: weapons and armor that ONLY drop inside vaults, scale
 * with vault level, and roll 1-3 random affixes. Attribute affixes (health,
 * armor, damage, speed...) ride on vanilla item attribute modifiers, so
 * clients render green stat lines natively; the exotic affixes (essence
 * hoarding, vault XP, on-kill healing) are plugin-enforced and described in
 * lore. Because any custom modifier wipes a vanilla item's implicit stats,
 * base damage/armor are re-added explicitly per piece.
 */
public final class VaultGear {

    // ------------------------------------------------------------- pieces

    private enum Piece {
        SWORD(EquipmentSlotGroup.MAINHAND, "Blade"),
        HELMET(EquipmentSlotGroup.HEAD, "Helm"),
        CHESTPLATE(EquipmentSlotGroup.CHEST, "Plate"),
        LEGGINGS(EquipmentSlotGroup.LEGS, "Greaves"),
        BOOTS(EquipmentSlotGroup.FEET, "Treads");

        final EquipmentSlotGroup slot;
        final String label;

        Piece(EquipmentSlotGroup slot, String label) {
            this.slot = slot;
            this.label = label;
        }
    }

    /** [helmet, chest, legs, boots] armor points per material tier. */
    private static final Map<String, int[]> ARMOR_POINTS = Map.of(
            "IRON", new int[] {2, 6, 5, 2},
            "DIAMOND", new int[] {3, 8, 6, 3},
            "NETHERITE", new int[] {3, 8, 6, 3});

    // ------------------------------------------------------------- affixes

    public enum Affix {
        VITALITY("§cVitality", true, 2.0, 8.0),
        FORTIFIED("§9Fortified", true, 1.0, 4.0),
        SWIFT("§bSwift", true, 0.05, 0.20),
        SAVAGE("§4Savage", true, 0.05, 0.35),
        FRENZIED("§eFrenzied", true, 0.05, 0.25),
        STALWART("§7Stalwart", true, 0.1, 0.4),
        ESSENCE_HOARDER("§3Essence Hoarder", false, 0.20, 0.80),
        VAULTBORN("§dVaultborn", false, 0.10, 0.50),
        SECOND_WIND("§aSecond Wind", false, 1.0, 4.0);

        public final String displayName;
        /** Attribute-backed (vanilla tooltip) vs plugin-enforced (lore). */
        public final boolean attribute;
        public final double min;
        public final double max;

        Affix(String displayName, boolean attribute, double min, double max) {
            this.displayName = displayName;
            this.attribute = attribute;
            this.min = min;
            this.max = max;
        }
    }

    private static final String[] FLAVOR = {"Warden's", "Riftbound", "Gloomforged",
            "Echoing", "Colossal", "Spirit-Touched", "Collapsing", "Sunken"};

    private VaultGear() {
    }

    // --------------------------------------------------------------- roll

    /**
     * @param rarityBoost 0 for chest loot, higher for treasure/boss drops.
     */
    public static ItemStack roll(int level, Random rng, double rarityBoost) {
        Piece piece = Piece.values()[rng.nextInt(Piece.values().length)];
        String tier = level < 8 ? "IRON" : level < 16 ? "DIAMOND" : "NETHERITE";
        Material material = Material.valueOf(tier + "_" + piece.name());

        double rarity = rng.nextDouble() + rarityBoost;
        int affixCount = rarity > 0.92 ? 3 : rarity > 0.62 ? 2 : 1;
        String color = affixCount == 3 ? "§5" : affixCount == 2 ? "§9" : "§f";
        String tag = affixCount == 3 ? "Epic" : affixCount == 2 ? "Rare" : "Common";

        List<Affix> pool = new ArrayList<>(List.of(Affix.values()));
        Collections.shuffle(pool, rng);
        Map<Affix, Double> rolled = new EnumMap<>(Affix.class);
        for (int i = 0; i < affixCount; i++) {
            Affix affix = pool.get(i);
            // Level dominates: a level-20 drop is unambiguously stronger
            // than a level-5 drop, so gear keeps getting out-leveled.
            double levelFrac = Math.min(1.0, level / 25.0);
            double magnitude = affix.min + (affix.max - affix.min)
                    * (0.15 * rng.nextDouble() + 0.85 * levelFrac);
            rolled.put(affix, Math.round(magnitude * 100.0) / 100.0);
        }

        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Text.item(color + FLAVOR[rng.nextInt(FLAVOR.length)]
                    + " " + piece.label));
            List<Component> lore = new ArrayList<>();
            lore.add(Text.item("§8" + tag + " Vaultforged — level " + level));
            for (Map.Entry<Affix, Double> entry : rolled.entrySet()) {
                if (!entry.getKey().attribute) {
                    lore.add(Text.item(entry.getKey().displayName + "§7: "
                            + describe(entry.getKey(), entry.getValue())));
                }
            }
            lore.add(Text.item("§8Found only in the vaults."));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(affixCount >= 2);
            meta.getPersistentDataContainer().set(Keys.ITEM_TYPE, PersistentDataType.STRING,
                    VhItemType.VAULT_GEAR.name());
            meta.getPersistentDataContainer().set(Keys.GEAR_AFFIXES, PersistentDataType.STRING,
                    encode(rolled));

            baseStats(meta, piece, tier, level);
            for (Map.Entry<Affix, Double> entry : rolled.entrySet()) {
                applyAttribute(meta, piece, entry.getKey(), entry.getValue());
            }
        });
        return item;
    }

    /**
     * Custom modifiers wipe implicit stats; put the vanilla numbers back —
     * PLUS a per-level bonus, so vault gear rapidly outgrows vanilla gear
     * (a level-12 Vaultforged iron blade already out-damages a vanilla
     * netherite sword) and each level bracket outgrows the last.
     */
    private static void baseStats(org.bukkit.inventory.meta.ItemMeta meta, Piece piece,
            String tier, int level) {
        if (piece == Piece.SWORD) {
            double damage = (tier.equals("IRON") ? 5 : tier.equals("DIAMOND") ? 6 : 7)
                    + 0.35 * level;
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                    Keys.of("gear_base_damage"), Math.round(damage * 10.0) / 10.0,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                    Keys.of("gear_base_attack_speed"), -2.4, AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.MAINHAND));
            return;
        }
        int index = switch (piece) {
            case HELMET -> 0;
            case CHESTPLATE -> 1;
            case LEGGINGS -> 2;
            default -> 3;
        };
        double armor = ARMOR_POINTS.get(tier)[index] + 0.15 * level;
        meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(
                Keys.of("gear_base_armor"), Math.round(armor * 10.0) / 10.0,
                AttributeModifier.Operation.ADD_NUMBER, piece.slot));
        double toughness = (tier.equals("DIAMOND") ? 2 : tier.equals("NETHERITE") ? 3 : 0)
                + 0.05 * level;
        meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(
                Keys.of("gear_base_toughness"), Math.round(toughness * 10.0) / 10.0,
                AttributeModifier.Operation.ADD_NUMBER, piece.slot));
    }

    private static void applyAttribute(org.bukkit.inventory.meta.ItemMeta meta,
            Piece piece, Affix affix, double value) {
        record Spec(Attribute attribute, AttributeModifier.Operation operation) {
        }
        Spec spec = switch (affix) {
            case VITALITY -> new Spec(Attribute.MAX_HEALTH, AttributeModifier.Operation.ADD_NUMBER);
            case FORTIFIED -> new Spec(Attribute.ARMOR, AttributeModifier.Operation.ADD_NUMBER);
            case SWIFT -> new Spec(Attribute.MOVEMENT_SPEED, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            case SAVAGE -> new Spec(Attribute.ATTACK_DAMAGE, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            case FRENZIED -> new Spec(Attribute.ATTACK_SPEED, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
            case STALWART -> new Spec(Attribute.KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_NUMBER);
            default -> null;
        };
        if (spec != null) {
            meta.addAttributeModifier(spec.attribute(), new AttributeModifier(
                    Keys.of("gear_affix_" + affix.name().toLowerCase(Locale.ROOT)),
                    value, spec.operation(), piece.slot));
        }
    }

    private static String describe(Affix affix, double value) {
        return switch (affix) {
            case ESSENCE_HOARDER -> "§3+" + Math.round(value * 100) + "%§7 essence from kills";
            case VAULTBORN -> "§d+" + Math.round(value * 100) + "%§7 vault XP";
            case SECOND_WIND -> "§aheal " + value + "§7 on kill";
            default -> String.valueOf(value);
        };
    }

    // ------------------------------------------------------------- reading

    private static String encode(Map<Affix, Double> rolled) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Affix, Double> entry : rolled.entrySet()) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(entry.getKey().name()).append(':').append(entry.getValue());
        }
        return sb.toString();
    }

    /** Sum of one exotic affix across everything the player has equipped. */
    public static double affixSum(Player player, Affix affix) {
        double sum = affixOn(player.getInventory().getItemInMainHand(), affix);
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            sum += affixOn(armor, affix);
        }
        return sum;
    }

    private static double affixOn(ItemStack item, Affix affix) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        String encoded = item.getItemMeta().getPersistentDataContainer()
                .get(Keys.GEAR_AFFIXES, PersistentDataType.STRING);
        if (encoded == null) {
            return 0;
        }
        for (String part : encoded.split(";")) {
            String[] kv = part.split(":");
            if (kv.length == 2 && kv[0].equals(affix.name())) {
                try {
                    return Double.parseDouble(kv[1]);
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }
}
