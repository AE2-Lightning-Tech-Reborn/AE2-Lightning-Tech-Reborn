package com.moakiee.ae2lt.mixin.recipeviewer.jei;

import com.moakiee.ae2lt.client.compat.JeiRecipeTransferMetadata;
import com.moakiee.ae2lt.client.tianshu.TianshuDirectUploadClient;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
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
@Pseudo
@Mixin(targets = "mezz.jei.gui.recipes.RecipeTransferButtonController", remap = false)
public abstract class JeiRecipeTransferButtonControllerMixin {
    @Accessor("recipeLayout")
    protected abstract IRecipeLayoutDrawable<?> ae2lt$getRecipeLayout();

    @Inject(
            method = "transferRecipe(ZZ)Z",
            at = {@At("HEAD"), @At("RETURN")},
            require = 1)
    private void ae2lt$clearRecipeTransferMetadata(CallbackInfoReturnable<Boolean> cir) {
        JeiRecipeTransferMetadata.clear();
    }

    // JEI 15.68 routes button and keyboard input through transferRecipe. Read the layout
    // only for the real transfer, after simulated clicks and missing targets are rejected.
    @Inject(
            method = "transferRecipe(ZZ)Z",
            at = @At(value = "INVOKE",
                    target = "Lmezz/jei/common/transfer/RecipeTransferService;transferRecipe(Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;Lmezz/jei/api/gui/IRecipeLayoutDrawable;Lnet/minecraft/world/entity/player/Player;Z)Z"),
            require = 1)
    private void ae2lt$beginRecipeTransferMetadata(CallbackInfoReturnable<Boolean> cir) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof TianshuPatternEncodingTermMenu tianshuMenu) {
            JeiRecipeTransferMetadata.begin(tianshuMenu, ae2lt$getRecipeLayout());
        }
    }

    @Redirect(
            method = "transferRecipe(ZZ)Z",
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
        // Pinned recipes have their own success callback, which must still run.
        if (recipeScreen instanceof RecipesGui
                && player != null
                && player.containerMenu instanceof TianshuPatternEncodingTermMenu tianshuMenu
                && TianshuDirectUploadClient.holdRecipeScreen(tianshuMenu, recipeScreen)) {
            return;
        }
        onClose.run();
    }
}
