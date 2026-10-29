package com.moakiee.ae2lt.mixin.recipeviewer.jei;

import com.moakiee.ae2lt.integration.jei.category.MultiblockStructureCategory;
import java.util.List;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.recipes.layouts.IRecipeLayoutList;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The size-based JEI layout API; selected from the actual target method descriptor. */
@Mixin(value = mezz.jei.gui.recipes.RecipeGuiLogic.class, remap = false)
public abstract class JeiModernMultiblockLayoutMixin {
    @Shadow private IRecipeLayoutList cachedRecipeLayoutsWithButtons;
    @Shadow public abstract IRecipeCategory<?> getSelectedRecipeCategory();

    @Inject(method = "getVisibleRecipeLayoutsWithButtons(Lmezz/jei/common/util/ImmutableSize2i;Lnet/minecraft/world/inventory/AbstractContainerMenu;)Ljava/util/List;", at = @At("HEAD"))
    private void ae2lt$fitModernMultiblockPreview(
            @Coerce Object availableSize, AbstractContainerMenu container,
            CallbackInfoReturnable<List<?>> cir) throws ReflectiveOperationException {
        if (getSelectedRecipeCategory() instanceof MultiblockStructureCategory category
                && category.setAvailableHeight((int) availableSize.getClass().getMethod("height").invoke(availableSize), 0)) {
            cachedRecipeLayoutsWithButtons = null;
        }
    }
}
