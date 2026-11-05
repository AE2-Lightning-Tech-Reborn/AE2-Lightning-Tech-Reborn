package com.moakiee.ae2lt.compat.gto.jei;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import com.google.common.primitives.Ints;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Adapted from AE2's FluidIngredientConverter (TeamAppliedEnergistics, LGPL-3.0).
 * Relocated for GTO; display saturation does not modify the source GenericStack's long amount.
 * See META-INF/ae2-legacy-jei-attribution.txt.
 */
public final class GtoFluidIngredientConverter implements GtoIngredientConverter<FluidStack> {
    @Override public IIngredientType<FluidStack> getIngredientType() { return ForgeTypes.FLUID_STACK; }

    @Override @Nullable public FluidStack getIngredientFromStack(GenericStack stack) {
        return stack.what() instanceof AEFluidKey key
                ? key.toStack(Math.max(1, Ints.saturatedCast(stack.amount()))) : null;
    }

    @Override @Nullable public GenericStack getStackFromIngredient(FluidStack ingredient) {
        return GenericStack.fromFluidStack(ingredient);
    }
}
