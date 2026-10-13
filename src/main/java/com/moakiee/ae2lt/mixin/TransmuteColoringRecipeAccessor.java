package com.moakiee.ae2lt.mixin;

import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Native transmutation recipes replace the former special shulker coloring recipe. */
@Mixin(TransmuteRecipe.class)
public interface TransmuteColoringRecipeAccessor {
    @Accessor("input") Ingredient ae2lt$colorInput();
    @Accessor("material") Ingredient ae2lt$colorMaterial();
    @Accessor("materialCount") MinMaxBounds.Ints ae2lt$colorMaterialCount();
    @Accessor("result") ItemStackTemplate ae2lt$colorResult();
    @Accessor("addMaterialCountToResult") boolean ae2lt$addsColorMaterialCount();
}
