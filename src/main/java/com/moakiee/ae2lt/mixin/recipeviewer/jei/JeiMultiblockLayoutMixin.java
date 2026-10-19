package com.moakiee.ae2lt.mixin.recipeviewer.jei;

import java.util.List;

import com.moakiee.ae2lt.integration.jei.category.MultiblockStructureCategory;

import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipeGuiLogic;
import mezz.jei.gui.recipes.layouts.IRecipeLayoutList;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets AE2LT's structure preview use the space JEI normally distributes as blank margins. */
@Mixin(value = RecipeGuiLogic.class, remap = false)
public abstract class JeiMultiblockLayoutMixin {
    @Shadow
    private IRecipeLayoutList cachedRecipeLayoutsWithButtons;

    @Shadow
    public abstract IRecipeCategory<?> getSelectedRecipeCategory();

    @Inject(method = "getVisibleRecipeLayoutsWithButtons", at = @At("HEAD"), require = 0)
    private void ae2lt$fitMultiblockPreview(
            int availableHeight,
            int minRecipePadding,
            AbstractContainerMenu container,
            CallbackInfoReturnable<List<RecipeLayoutWithButtons<?>>> cir) {
        if (getSelectedRecipeCategory() instanceof MultiblockStructureCategory category
                && category.setAvailableHeight(availableHeight, minRecipePadding)) {
            // JEI reuses layouts across screen resizes. Rebuild their bounds, widgets,
            // and side buttons together before calculating pagination for the new size.
            cachedRecipeLayoutsWithButtons = null;
        }
    }
}
