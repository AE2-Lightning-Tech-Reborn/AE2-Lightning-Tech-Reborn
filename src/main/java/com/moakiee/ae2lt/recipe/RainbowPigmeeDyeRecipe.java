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
import net.minecraft.world.level.Level;

/** Visible shaped recipes with absolute positions: mirroring may select another dye. */
public final class RainbowPigmeeDyeRecipe extends ShapedRecipe {
    private RainbowPigmeeDyeRecipe(ShapedRecipe recipe) {
        super(recipe.getGroup(), recipe.category(), recipe.pattern, recipe.getResultItem(null),
                recipe.showNotification());
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.width() != 3 || input.height() != 3 || getWidth() != 3 || getHeight() != 3) {
            return false;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (!getIngredients().get(slot).test(input.getItem(slot))) {
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
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeTypes.RAINBOW_PIGMEE_DYE_SERIALIZER.get();
    }

    public static final class Serializer implements RecipeSerializer<RainbowPigmeeDyeRecipe> {
        private static final MapCodec<RainbowPigmeeDyeRecipe> CODEC =
                ShapedRecipe.Serializer.CODEC.xmap(RainbowPigmeeDyeRecipe::new, recipe -> recipe);
        private static final StreamCodec<RegistryFriendlyByteBuf, RainbowPigmeeDyeRecipe> STREAM_CODEC =
                ShapedRecipe.Serializer.STREAM_CODEC.map(RainbowPigmeeDyeRecipe::new, recipe -> recipe);

        @Override
        public MapCodec<RainbowPigmeeDyeRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, RainbowPigmeeDyeRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
