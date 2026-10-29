package com.moakiee.ae2lt.mixin.recipeviewer.jei;

import com.moakiee.ae2lt.client.compat.JeiRecipeTransferMetadata;
import com.moakiee.ae2lt.client.tianshu.TianshuDirectUploadClient;
import com.moakiee.ae2lt.client.tianshu.TianshuUploadAliasEditScreen;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.gui.recipes.RecipeTransferButtonController;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserves recipe metadata and keeps the JEI recipe page open for Alt direct uploads. */
@Mixin(value = RecipeTransferButtonController.class, remap = false)
public abstract class JeiRecipeTransferButtonControllerMixin {
    @Shadow
    private IRecipeTransferError recipeTransferError;

    @Accessor("recipeLayout")
    protected abstract IRecipeLayoutDrawable<?> ae2lt$getRecipeLayout();

    @Unique
    private static TianshuUploadAliasEditScreen<?> ae2lt$aliasEditor() {
        var mc = Minecraft.getInstance();
        var parent = mc.screen instanceof RecipesGui recipesGui
                ? recipesGui.getParentScreen().orElse(null) : mc.screen;
        return parent instanceof TianshuUploadAliasEditScreen<?> editor
                && mc.player != null && mc.player.containerMenu == editor.getMenu() ? editor : null;
    }

    @Inject(method = "updateState", at = @At("HEAD"), cancellable = true, require = 0)
    private void ae2lt$enableAliasSourceSelection(IButtonState state, CallbackInfo ci) {
        if (ae2lt$aliasEditor() == null) return;
        recipeTransferError = null;
        state.setActive(true);
        state.setVisible(true);
        ci.cancel();
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
            method = "onPress(Lmezz/jei/api/gui/inputs/IJeiUserInput;)Z",
            at = {@At("HEAD"), @At("RETURN")},
            require = 0)
    private void ae2lt$clearRecipeTransferMetadata(CallbackInfoReturnable<Boolean> cir) {
        JeiRecipeTransferMetadata.clear();
    }

    // Reading the layout happens only after JEI rejects simulated clicks and missing menus.
    // Do not bind to its private recipesGui/parentContainer fields or input class: new JEI
    // versions replaced those fields with suppliers and moved UserInput to another package.
    @Inject(
            method = "onPress(Lmezz/jei/api/gui/inputs/IJeiUserInput;)Z",
            at = @At(value = "FIELD",
                    target = "Lmezz/jei/gui/recipes/RecipeTransferButtonController;recipeLayout:Lmezz/jei/api/gui/IRecipeLayoutDrawable;"),
            require = 0)
    private void ae2lt$beginRecipeTransferMetadata(CallbackInfoReturnable<Boolean> cir) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof TianshuPatternEncodingTermMenu menu) {
            JeiRecipeTransferMetadata.begin(menu, ae2lt$getRecipeLayout());
        }
    }

    // Older JEI versions close the recipe screen directly.
    @Redirect(
            method = "onPress(Lmezz/jei/api/gui/inputs/IJeiUserInput;)Z",
            at = @At(value = "INVOKE", target = "Lmezz/jei/gui/recipes/RecipesGui;onClose()V"),
            require = 0)
    private void ae2lt$keepLegacyRecipePageForDirectUpload(RecipesGui recipesGui) {
        if (!ae2lt$holdRecipePage(recipesGui)) recipesGui.onClose();
    }

    @Redirect(
            method = "onPress(Lmezz/jei/api/gui/inputs/IJeiUserInput;)Z",
            at = @At(value = "INVOKE", target = "Ljava/lang/Runnable;run()V"),
            require = 0)
    private void ae2lt$keepRecipePageForDirectUpload(Runnable onSuccessfulTransfer) {
        if (!ae2lt$holdRecipePage(Minecraft.getInstance().screen)) onSuccessfulTransfer.run();
    }

    @Unique
    private static boolean ae2lt$holdRecipePage(Screen recipeScreen) {
        var player = Minecraft.getInstance().player;
        // Pinned recipes have their own success callback, which must still run when no
        // RecipesGui is open. Retain the existing Alt guard for normal recipe pages.
        return recipeScreen instanceof RecipesGui
                && Screen.hasAltDown()
                && player != null
                && player.containerMenu instanceof TianshuPatternEncodingTermMenu menu
                && TianshuDirectUploadClient.holdRecipeScreen(menu, recipeScreen);
    }
}
