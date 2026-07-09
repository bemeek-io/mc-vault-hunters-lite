package com.evensteven.vhlite.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/** Small text helpers: legacy-color strings to Components, and back. */
public final class Text {

    private Text() {
    }

    /** "§aHello" -> Component, for chat and menu titles. */
    public static Component c(String legacy) {
        return LegacyComponentSerializer.legacySection().deserialize(legacy);
    }

    /**
     * Like {@link #c} but with italics forced off — item display names and
     * lore render italic by default, which reads as "renamed in an anvil".
     */
    public static Component item(String legacy) {
        return c(legacy).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> lore(String... lines) {
        List<Component> out = new ArrayList<>(lines.length);
        for (String line : lines) {
            out.add(item(line));
        }
        return out;
    }

    public static String plain(Component component) {
        return component == null ? "" : PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** "world,x,y,z,yaw,pitch" round-trip for YAML storage. */
    public static String encodeLocation(Location loc) {
        return loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + ","
                + loc.getZ() + "," + loc.getYaw() + "," + loc.getPitch();
    }

    public static Location decodeLocation(String encoded) {
        String[] parts = encoded.split(",");
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        return new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
    }
}
