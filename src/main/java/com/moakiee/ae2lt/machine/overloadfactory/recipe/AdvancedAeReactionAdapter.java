package com.moakiee.ae2lt.machine.overloadfactory.recipe;

import java.util.List;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.pedroksl.advanced_ae.recipes.ReactionChamberRecipe;

/** Loaded only when AdvancedAE is installed. */
final class AdvancedAeReactionAdapter {
    private AdvancedAeReactionAdapter() {
    }

    static List<RecipeHolder<ReactionChamberRecipe>> sourceRecipes(RecipeManager manager) {
        // Use the exact type object used by the upstream machine instead of relying
        // on a registry ID lookup to expose that same object in every AAE version.
        return com.moakiee.ae2lt.recipe.compat.LegacyRecipeAccess.recipesOfType(manager,
                net.pedroksl.advanced_ae.recipes.AAERecipeTypes.REACTION_CHAMBER);
    }

    static RecipeHolder<OverloadProcessingRecipe> convert(RecipeHolder<?> holder) {
        if (holder.value().getClass() != ReactionChamberRecipe.class) {
            throw new IllegalArgumentException("unsupported reaction recipe implementation");
        }
        var source = (ReactionChamberRecipe) holder.value();
        var inputs = source.getInputs().stream()
                .map(input -> new OverloadProcessingIngredient(input.getIngredient(), input.getAmount()))
                .toList();
        var sourceFluid = source.getFluid();
        var fluid = sourceFluid == null ? null
                : new SizedFluidIngredient(sourceFluid.getIngredient(), sourceFluid.getAmount());
        var output = source.isItemOutput()
                ? appeng.api.stacks.GenericStack.fromItemStack(source.getResultItem())
                : new appeng.api.stacks.GenericStack(AEFluidKey.of(source.getResultFluid()), source.getResultFluid().getAmount());
        if (output == null || output.amount() <= 0 || output.amount() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("reaction output amount is outside the factory range");
        }
        int amount = (int) output.amount();
        OverloadProcessingRecipe recipe;
        if (output.what() instanceof AEItemKey item) {
            recipe = OverloadProcessingRecipe.borrowed(
                    inputs, fluid, List.of(item.toStack(amount)), FluidStack.EMPTY, source.getEnergy());
        } else if (output.what() instanceof AEFluidKey resultFluid) {
            recipe = OverloadProcessingRecipe.borrowed(
                    inputs, fluid, List.of(), resultFluid.toStack(amount), source.getEnergy());
        } else {
            throw new IllegalArgumentException("reaction output is neither an item nor a fluid");
        }
        return new RecipeHolder<>(holder.id(), recipe);
    }
}
