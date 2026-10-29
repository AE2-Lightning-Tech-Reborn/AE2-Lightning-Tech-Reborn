package com.moakiee.ae2lt.mixin.recipeviewer.jei;

import com.moakiee.ae2lt.client.compat.JeiRecipeTransferMetadata;
import com.moakiee.ae2lt.client.tianshu.TianshuDirectUploadClient;
import com.moakiee.ae2lt.client.tianshu.TianshuUploadAliasEditScreen;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * JEI closes its recipe page after every successful transfer. Alt direct-upload keeps that page
 * open so multiple patterns can be encoded consecutively; an ambiguous target still falls back to
 * the normal terminal picker.
 */
@Pseudo
@Mixin(targets = "mezz.jei.gui.recipes.RecipeTransferButtonController", remap = false)
public abstract class JeiRecipeTransferButtonControllerMixin {
    @Shadow
    private IRecipeTransferError recipeTransferError;

    @Accessor("recipeLayout")
    protected abstract IRecipeLayoutDrawable<?> ae2lt$getRecipeLayout();

    @Unique
    private static TianshuUploadAliasEditScreen<?> ae2lt$aliasEditor() {
        var mc = Minecraft.getInstance();
        var parent = mc.screen instanceof RecipesGui recipesGui
                ? ((JeiRecipesGuiAccessor) recipesGui).ae2lt$getParentScreen() : mc.screen;
        return parent instanceof TianshuUploadAliasEditScreen<?> editor
                && mc.player != null && mc.player.containerMenu == editor.getMenu() ? editor : null;
    }

    @Inject(method = "updateRecipeTransferError", at = @At("HEAD"), cancellable = true)
    private void ae2lt$enableAliasSourceSelection(CallbackInfoReturnable<IRecipeTransferError> cir) {
        if (ae2lt$aliasEditor() == null) return;
        recipeTransferError = null;
        cir.setReturnValue(null);
    }

    @Inject(method = "getTooltips(Lmezz/jei/api/gui/builder/ITooltipBuilder;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void ae2lt$aliasSourceTooltip(ITooltipBuilder tooltip, CallbackInfo ci) {
        if (ae2lt$aliasEditor() == null) return;
        tooltip.add(Component.translatable("ae2lt.tianshu.aliases.recipe_source"));
        ci.cancel();
    }

    @Inject(method = "onPress(Lmezz/jei/api/gui/inputs/IJeiUserInput;)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void ae2lt$selectAliasSource(IJeiUserInput input, CallbackInfoReturnable<Boolean> cir) {
        var editor = ae2lt$aliasEditor();
        if (editor == null) return;
        if (!input.isSimulate()) {
            var layout = ae2lt$getRecipeLayout();
            var type = layout.getRecipeCategory().getRecipeType();
            editor.setRecipeSource(layout.getRecipe(), type == null || type.getUid() == null
                    ? "" : type.getUid().toString());
            if (Minecraft.getInstance().screen instanceof RecipesGui recipesGui) recipesGui.onClose();
        }
        cir.setReturnValue(true);
    }

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
