package com.moakiee.ae2lt.mixin.recipeviewer.jei;

import com.moakiee.ae2lt.client.compat.JeiRecipeTransferMetadata;
import com.moakiee.ae2lt.client.tianshu.TianshuDirectUploadClient;
import com.moakiee.ae2lt.client.tianshu.TianshuUploadAliasEditScreen;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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
@Mixin(targets = "mezz.jei.gui.recipes.RecipeTransferButton", remap = false)
public abstract class JeiLegacyRecipeTransferButtonMixin {
    @Unique private static java.lang.reflect.Field ae2lt$legacyButtonField;

    @Unique
    private static TianshuUploadAliasEditScreen<?> ae2lt$aliasEditor() {
        var mc = Minecraft.getInstance();
        var parent = mc.screen instanceof RecipesGui recipesGui
                ? ((JeiRecipesGuiAccessor) recipesGui).ae2lt$getParentScreen() : mc.screen;
        return parent instanceof TianshuUploadAliasEditScreen<?> editor
                && mc.player != null && mc.player.containerMenu == editor.getMenu() ? editor : null;
    }

    @Inject(method = "update", at = @At("RETURN"))
    private void ae2lt$enableAliasSourceSelection(CallbackInfo ci) {
        if (ae2lt$aliasEditor() != null) {
            try {
                // GuiIconButton was removed in newer JEI 15. Keep this legacy type out of
                // the shared compile API while using its verified vanilla widget base.
                if (ae2lt$legacyButtonField == null) {
                    ae2lt$legacyButtonField = getClass().getSuperclass().getDeclaredField("button");
                    ae2lt$legacyButtonField.setAccessible(true);
                }
                var button = (net.minecraft.client.gui.components.AbstractWidget) ae2lt$legacyButtonField.get(this);
                button.active = true;
                button.visible = true;
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot access JEI 15 legacy transfer button", failure);
            }
        }
    }

    @Inject(method = {"onMouseClicked(Lmezz/jei/gui/input/UserInput;)Z",
            "onMouseClicked(Lmezz/jei/common/input/UserInput;)Z"},
            at = @At("HEAD"), cancellable = true, require = 1)
    private void ae2lt$selectAliasSource(@org.spongepowered.asm.mixin.injection.Coerce
            mezz.jei.api.gui.inputs.IJeiUserInput input, CallbackInfoReturnable<Boolean> cir) {
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

    @Inject(method = {"getTooltips(Lmezz/jei/common/gui/JeiTooltip;)V",
            "getTooltips(Lmezz/jei/api/gui/builder/ITooltipBuilder;)V"},
            at = @At("HEAD"), cancellable = true, require = 1)
    private void ae2lt$aliasSourceTooltip(@org.spongepowered.asm.mixin.injection.Coerce
            mezz.jei.api.gui.builder.ITooltipBuilder tooltip, CallbackInfo ci) {
        if (ae2lt$aliasEditor() == null) return;
        tooltip.add(net.minecraft.network.chat.Component.translatable("ae2lt.tianshu.aliases.recipe_source"));
        ci.cancel();
    }

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

    // JEI 15 moved UserInput and replaced parentContainer with a screen supplier. The layout
    // is read only after simulated clicks and missing transfer targets have been rejected.
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
