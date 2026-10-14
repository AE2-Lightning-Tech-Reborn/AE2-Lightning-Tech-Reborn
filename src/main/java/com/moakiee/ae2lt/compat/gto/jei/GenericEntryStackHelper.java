package com.moakiee.ae2lt.compat.gto.jei;

import java.util.List;
import java.util.Objects;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.me.key.LightningKey;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Converts the ingredient types supported by LT to the native GTO encoding API. */
public final class GenericEntryStackHelper {
    private GenericEntryStackHelper() { }
    public static GenericStack ingredientToStack(ITypedIngredient<?> ingredient) {
        Object value = ingredient.getIngredient();
        if (value instanceof ItemStack item) return GenericStack.fromItemStack(item);
        if (value instanceof FluidStack fluid && !fluid.isEmpty()) {
            return new GenericStack(AEFluidKey.of(fluid), fluid.getAmount());
        }
        if (value instanceof LightningKey lightning) return new GenericStack(lightning, 1);
        return null;
    }
    public static List<List<GenericStack>> ofInputs(IRecipeSlotsView view) {
        return view.getSlotViews(RecipeIngredientRole.INPUT).stream().map(GenericEntryStackHelper::ofSlot).toList();
    }
    public static List<GenericStack> ofOutputs(IRecipeSlotsView view) {
        return view.getSlotViews(RecipeIngredientRole.OUTPUT).stream()
                .flatMap(slot -> ofSlot(slot).stream().limit(1)).toList();
    }
    private static List<GenericStack> ofSlot(IRecipeSlotView slot) {
        return slot.getAllIngredients().map(GenericEntryStackHelper::ingredientToStack).filter(Objects::nonNull).toList();
    }
}
