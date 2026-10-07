package com.moakiee.ae2lt.recipe;

import com.moakiee.ae2lt.registry.ModFumos;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

/** The visible dye layouts also recolor four identical items, without mirroring or shifting. */
public final class RainbowPigmeeDyeRecipe extends ShapedRecipe {
    private RainbowPigmeeColoring coloring = RainbowPigmeeColoring.EMPTY;

    private RainbowPigmeeDyeRecipe(ShapedRecipe recipe) {
        super(recipe.getGroup(), recipe.category(), recipe.pattern, recipe.getResultItem(null),
                recipe.showNotification());
    }

    void setColoring(RainbowPigmeeColoring coloring) {
        this.coloring = coloring;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return matchesDyeBases(input) || !recolorResult(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return matchesDyeBases(input) ? super.assemble(input, registries) : recolorResult(input);
    }

    private boolean matchesDyeBases(CraftingInput input) {
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

    private ItemStack recolorResult(CraftingInput input) {
        if (input.width() != 3 || input.height() != 3 || getWidth() != 3 || getHeight() != 3
                || !input.getItem(4).is(ModFumos.RAINBOW_PIGMEE_FUMO_ITEM.get())
                || !(getResultItem(null).getItem() instanceof DyeItem dye)) {
            return ItemStack.EMPTY;
        }
        ItemStack target = ItemStack.EMPTY;
        int count = 0;
        for (int slot = 0; slot < 9; slot++) {
            if (slot == 4) {
                continue;
            }
            var stack = input.getItem(slot);
            if (getIngredients().get(slot).isEmpty()) {
                if (!stack.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (stack.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                if (target.isEmpty()) {
                    target = stack;
                } else if (!ItemStack.isSameItemSameComponents(target, stack)) {
                    return ItemStack.EMPTY;
                }
                count++;
            }
        }
        return count == 4 ? coloring.color(target, dye.getDyeColor(), count) : ItemStack.EMPTY;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        // The input item's container/data moves into the result, so return only the catalyst.
        var remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
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
