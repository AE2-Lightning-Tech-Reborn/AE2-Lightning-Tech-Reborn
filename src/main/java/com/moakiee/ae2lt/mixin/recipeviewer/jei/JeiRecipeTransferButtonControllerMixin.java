package com.moakiee.ae2lt.mixin.recipeviewer.jei;

import com.moakiee.ae2lt.client.JeiRecipeTransferMetadata;
import com.moakiee.ae2lt.client.TianshuDirectUploadClient;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.gui.recipes.RecipeTransferButton;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * JEI closes its recipe page after every successful transfer. Alt direct-upload keeps that page
 * open so multiple patterns can be encoded consecutively; an ambiguous target still falls back to
 * the normal terminal picker.
 */
@Mixin(value = RecipeTransferButton.class, remap = false)
public abstract class JeiRecipeTransferButtonControllerMixin {
    @Accessor("recipeLayout")
    protected abstract IRecipeLayoutDrawable<?> ae2lt$getRecipeLayout();

    @Inject(
            method = {
                    "onMouseClicked(Lmezz/jei/gui/input/UserInput;)Z",
                    "onMouseClicked(Lmezz/jei/common/input/UserInput;)Z"
            },
            at = {@At("HEAD"), @At("RETURN")},
            require = 1)
    private void ae2lt$clearRecipeTransferMetadata(CallbackInfoReturnable<Boolean> cir) {
        JeiRecipeTransferMetadata.clear();
    }

    @Inject(
            method = {
                    "onMouseClicked(Lmezz/jei/gui/input/UserInput;)Z",
                    "onMouseClicked(Lmezz/jei/common/input/UserInput;)Z"
            },
            at = @At(value = "FIELD",
                    target = "Lmezz/jei/gui/recipes/RecipeTransferButton;recipeLayout:Lmezz/jei/api/gui/IRecipeLayoutDrawable;"),
            require = 1)
    private void ae2lt$beginRecipeTransferMetadata(CallbackInfoReturnable<Boolean> cir) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof TianshuPatternEncodingTermMenu tianshuMenu) {
            JeiRecipeTransferMetadata.begin(tianshuMenu, ae2lt$getRecipeLayout());
        }
    }

    @Redirect(
            method = {
                    "onMouseClicked(Lmezz/jei/gui/input/UserInput;)Z",
                    "onMouseClicked(Lmezz/jei/common/input/UserInput;)Z"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Runnable;run()V"),
            require = 1)
    private void ae2lt$keepRecipePageForDirectUpload(Runnable onClose) {
        var player = Minecraft.getInstance().player;
        var recipeScreen = Minecraft.getInstance().screen;
        // The successful transfer handler has already converted Alt into an authoritative
        // pending-direct-upload request. Do not sample the physical modifier again here: JEI
        // may run this close callback after its input state has advanced, which would close the
        // recipe page even though the direct upload is already armed.
        if (recipeScreen instanceof RecipesGui
                && player != null
                && player.containerMenu instanceof TianshuPatternEncodingTermMenu tianshuMenu
                && TianshuDirectUploadClient.holdRecipeScreen(tianshuMenu, recipeScreen)) {
            return;
        }
        onClose.run();
    }
}
