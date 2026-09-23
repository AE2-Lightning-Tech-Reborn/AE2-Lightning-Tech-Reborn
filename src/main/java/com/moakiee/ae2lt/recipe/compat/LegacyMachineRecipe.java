package com.moakiee.ae2lt.recipe.compat;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;

/** Preserves the machine recipe contract while adapting Minecraft's 26.1 recipe interface. */
public interface LegacyMachineRecipe<T extends RecipeInput> extends Recipe<T> {
    ItemStack assemble(T input, HolderLookup.Provider registries);

    boolean canCraftInDimensions(int width, int height);

    ItemStack getResultItem(HolderLookup.Provider registries);

    NonNullList<Ingredient> getIngredients();

    default boolean isIncomplete() {
        return false;
    }

    @Override
    default ItemStack assemble(T input) {
        return assemble(input, null);
    }

    @Override
    default boolean showNotification() {
        return false;
    }

    @Override
    default String group() {
        return "";
    }

    @Override
    default PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    default RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }
}
