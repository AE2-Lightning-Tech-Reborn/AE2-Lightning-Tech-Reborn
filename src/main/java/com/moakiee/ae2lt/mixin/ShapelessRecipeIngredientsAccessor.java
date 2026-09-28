package com.moakiee.ae2lt.mixin;

import java.util.List;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Raw ingredients are safe before reload tags bind; PlacementInfo is not. */
@Mixin(ShapelessRecipe.class)
public interface ShapelessRecipeIngredientsAccessor {
    @Accessor("ingredients") List<Ingredient> ae2lt$ingredients();
}
