package com.moakiee.ae2lt.mixin.recipeviewer.jei;

import java.util.List;

import com.moakiee.ae2lt.integration.jei.category.MultiblockStructureCategory;

import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.common.util.ImmutableSize2i;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.recipes.IRecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipeGuiLogic;
import mezz.jei.gui.recipes.RecipesGui;
import mezz.jei.gui.recipes.layouts.IRecipeLayoutList;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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

    @Inject(method = "getVisibleRecipeLayoutsWithButtons(IILnet/minecraft/world/inventory/AbstractContainerMenu;Lmezz/jei/gui/bookmarks/BookmarkList;Lmezz/jei/gui/recipes/RecipesGui;)Ljava/util/List;", at = @At("HEAD"), require = 0)
    private void ae2lt$fitMultiblockPreview(
            int availableHeight,
            int minRecipePadding,
            AbstractContainerMenu container,
            BookmarkList bookmarkList,
            RecipesGui recipesGui,
            CallbackInfoReturnable<List<IRecipeLayoutWithButtons<?>>> cir) {
        ae2lt$fitPreview(availableHeight, minRecipePadding);
    }

    // New JEI supplies a two-dimensional area instead of height and padding separately.
    @Inject(method = "getVisibleRecipeLayoutsWithButtons(Lmezz/jei/common/util/ImmutableSize2i;Lnet/minecraft/world/inventory/AbstractContainerMenu;Lmezz/jei/gui/bookmarks/BookmarkList;Lmezz/jei/gui/recipes/RecipesGui;)Ljava/util/List;", at = @At("HEAD"), require = 0)
    private void ae2lt$fitMultiblockPreviewModern(ImmutableSize2i availableSize, AbstractContainerMenu container,
            BookmarkList bookmarkList, RecipesGui recipesGui,
            CallbackInfoReturnable<List<IRecipeLayoutWithButtons<?>>> cir) {
        ae2lt$fitPreview(availableSize.height(), 0);
    }

    @Unique
    private void ae2lt$fitPreview(int availableHeight, int minRecipePadding) {
        if (getSelectedRecipeCategory() instanceof MultiblockStructureCategory category
                && category.setAvailableHeight(availableHeight, minRecipePadding)) {
            // JEI reuses layouts across screen resizes. Rebuild their bounds, widgets,
            // and side buttons together before calculating pagination for the new size.
            cachedRecipeLayoutsWithButtons = null;
        }
    }
}
