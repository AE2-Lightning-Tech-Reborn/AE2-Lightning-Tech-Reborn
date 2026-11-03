package com.moakiee.ae2lt.compat.gto.jei;

import appeng.api.stacks.GenericStack;
import mezz.jei.api.ingredients.IIngredientType;
import org.jetbrains.annotations.Nullable;

/**
 * LT-local form of AE2's legacy JEI conversion protocol. Adapted from Applied Energistics 2,
 * TeamAppliedEnergistics, LGPL-3.0; see META-INF/ae2-legacy-jei-attribution.txt.
 */
public interface GtoIngredientConverter<T> {
    IIngredientType<T> getIngredientType();
    @Nullable T getIngredientFromStack(GenericStack stack);
    @Nullable GenericStack getStackFromIngredient(T ingredient);
}
