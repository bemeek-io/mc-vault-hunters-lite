package com.evensteven.vhlite.vault.modifier;

/**
 * Run-wide twists rolled onto a vault (0-2 per run) or forced by catalysts.
 * Pure data: multipliers the instance, spawner, and loot roller read.
 */
public enum VaultModifier {

    RUSH("§eRush", "Less time on the clock, quicker feet.",
            0.60, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.15),
    PLENTIFUL("§aPlentiful", "Loot chests roll half again as much.",
            1.0, 1.5, 1.0, 1.0, 1.0, 1.0, 1.0, 0.0),
    CHAOS("§cChaos", "Far more monsters, each a little weaker.",
            1.0, 1.0, 1.6, 0.8, 1.0, 1.0, 1.0, 0.0),
    GILDED("§6Gilded", "Twice the loot chests are hidden inside.",
            1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 2.0, 0.0),
    FRAGILE("§7Fragile", "Weaker monsters, but slimmer pickings.",
            1.0, 0.75, 1.0, 0.7, 1.0, 1.0, 1.0, 0.0),
    FRENZY("§4Frenzy", "The monsters are faster and hit harder.",
            1.0, 1.0, 1.0, 1.0, 1.25, 1.25, 1.0, 0.0);

    public final String displayName;
    public final String description;
    public final double timeMult;
    public final double lootMult;
    public final double mobCapMult;
    public final double mobHealthMult;
    public final double mobDamageMult;
    public final double mobSpeedMult;
    public final double chestMult;
    public final double playerSpeedBonus;

    VaultModifier(String displayName, String description, double timeMult, double lootMult,
            double mobCapMult, double mobHealthMult, double mobDamageMult, double mobSpeedMult,
            double chestMult, double playerSpeedBonus) {
        this.displayName = displayName;
        this.description = description;
        this.timeMult = timeMult;
        this.lootMult = lootMult;
        this.mobCapMult = mobCapMult;
        this.mobHealthMult = mobHealthMult;
        this.mobDamageMult = mobDamageMult;
        this.mobSpeedMult = mobSpeedMult;
        this.chestMult = chestMult;
        this.playerSpeedBonus = playerSpeedBonus;
    }
}
