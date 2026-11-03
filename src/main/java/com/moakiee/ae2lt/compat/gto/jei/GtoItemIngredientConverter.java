package com.moakiee.ae2lt.compat.gto.jei;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.google.common.primitives.Ints;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Adapted from AE2's ItemIngredientConverter (TeamAppliedEnergistics, LGPL-3.0).
 * Relocated for GTO; the legacy display count and exact key/NBT behavior are unchanged.
 * See META-INF/ae2-legacy-jei-attribution.txt.
 */
public final class GtoItemIngredientConverter implements GtoIngredientConverter<ItemStack> {
    @Override public IIngredientType<ItemStack> getIngredientType() { return VanillaTypes.ITEM_STACK; }

    @Override @Nullable public ItemStack getIngredientFromStack(GenericStack stack) {
        return stack.what() instanceof AEItemKey key
                ? key.toStack(Math.max(1, Ints.saturatedCast(stack.amount()))) : null;
    }

    @Override @Nullable public GenericStack getStackFromIngredient(ItemStack ingredient) {
        return GenericStack.fromItemStack(ingredient);
    }
}
