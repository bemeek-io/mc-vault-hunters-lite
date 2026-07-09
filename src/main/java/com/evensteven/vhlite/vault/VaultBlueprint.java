package com.evensteven.vhlite.vault;

import com.evensteven.vhlite.vault.generation.LayoutType;
import com.evensteven.vhlite.vault.generation.Theme;
import com.evensteven.vhlite.vault.modifier.VaultModifier;
import com.evensteven.vhlite.vault.objective.VaultObjective;

import java.util.List;
import java.util.UUID;

/**
 * Everything that determines a run before a single block is placed. The
 * seed drives every random pick downstream, so a blueprint (plus config)
 * reproduces its vault exactly — that's what /vhadmin testgen leans on.
 */
public record VaultBlueprint(
        UUID id,
        long seed,
        int level,
        int partySize,
        Theme theme,
        LayoutType layout,
        VaultObjective objective,
        List<VaultModifier> modifiers,
        int slot,
        int originX,
        int originZ,
        /** True: victory pulls the party out on the spot, no walk to a pad. */
        boolean instantExtract) {

    public double modifierProduct(java.util.function.ToDoubleFunction<VaultModifier> getter) {
        double product = 1.0;
        for (VaultModifier modifier : modifiers) {
            product *= getter.applyAsDouble(modifier);
        }
        return product;
    }
}
