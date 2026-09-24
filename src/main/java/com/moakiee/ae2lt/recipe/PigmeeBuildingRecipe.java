package com.moakiee.ae2lt.recipe;

import com.moakiee.ae2lt.registry.ModFumos;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

/** A normal, visible shaped recipe whose Pigmee catalyst is returned with its components intact. */
public final class PigmeeBuildingRecipe extends ShapedRecipe {
    private PigmeeBuildingRecipe(ShapedRecipe recipe) {
        super(new net.minecraft.world.item.crafting.Recipe.CommonInfo(recipe.showNotification()),
                new net.minecraft.world.item.crafting.CraftingRecipe.CraftingBookInfo(recipe.category(), recipe.group()),
                recipe.pattern, recipe.result);
    }

    @Override
    public RecipeSerializer<ShapedRecipe> getSerializer() {
        return ModRecipeTypes.PIGMEE_BUILDING_SERIALIZER.get();
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        var remaining = super.getRemainingItems(input);
        for (int slot = 0; slot < input.size(); slot++) {
            var stack = input.getItem(slot);
            if (stack.is(ModFumos.PIGMEE_FUMO_ITEM.get())) {
                remaining.set(slot, stack.copyWithCount(1));
            }
        }
        return remaining;
    }

    public static RecipeSerializer<ShapedRecipe> serializer() {
        MapCodec<ShapedRecipe> codec = ShapedRecipe.MAP_CODEC.xmap(PigmeeBuildingRecipe::new, recipe -> recipe);
        StreamCodec<RegistryFriendlyByteBuf, ShapedRecipe> streamCodec =
                ShapedRecipe.STREAM_CODEC.map(PigmeeBuildingRecipe::new, recipe -> recipe);
        return new RecipeSerializer<>(codec, streamCodec);
    }
}
