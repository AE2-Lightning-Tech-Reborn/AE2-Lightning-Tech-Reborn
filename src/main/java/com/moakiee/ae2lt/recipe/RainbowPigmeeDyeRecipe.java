package com.moakiee.ae2lt.recipe;

import com.moakiee.ae2lt.registry.ModFumos;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

/** Visible shaped recipes with absolute positions: mirroring may select another dye. */
public final class RainbowPigmeeDyeRecipe extends ShapedRecipe {
    private RainbowPigmeeDyeRecipe(ShapedRecipe recipe) {
        super(new net.minecraft.world.item.crafting.Recipe.CommonInfo(recipe.showNotification()),
                new net.minecraft.world.item.crafting.CraftingRecipe.CraftingBookInfo(recipe.category(), recipe.group()), recipe.pattern, recipe.result);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.width() != 3 || input.height() != 3 || getWidth() != 3 || getHeight() != 3) {
            return false;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (!Ingredient.testOptionalIngredient(getIngredients().get(slot), input.getItem(slot))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        var remaining = super.getRemainingItems(input);
        for (int slot = 0; slot < input.size(); slot++) {
            var stack = input.getItem(slot);
            if (stack.is(ModFumos.RAINBOW_PIGMEE_FUMO_ITEM.get())) {
                remaining.set(slot, stack.copyWithCount(1));
            }
        }
        return remaining;
    }

    @Override
    public RecipeSerializer<ShapedRecipe> getSerializer() {
        return (RecipeSerializer<ShapedRecipe>) (RecipeSerializer<?>) ModRecipeTypes.RAINBOW_PIGMEE_DYE_SERIALIZER.get();
    }

    public static final class Serializer {
        private static final MapCodec<RainbowPigmeeDyeRecipe> CODEC =
                ShapedRecipe.MAP_CODEC.xmap(RainbowPigmeeDyeRecipe::new, recipe -> recipe);
        private static final StreamCodec<RegistryFriendlyByteBuf, RainbowPigmeeDyeRecipe> STREAM_CODEC =
                ShapedRecipe.STREAM_CODEC.map(RainbowPigmeeDyeRecipe::new, recipe -> recipe);

        public static final RecipeSerializer<RainbowPigmeeDyeRecipe> INSTANCE = new RecipeSerializer<>(CODEC, STREAM_CODEC);
    }
}
