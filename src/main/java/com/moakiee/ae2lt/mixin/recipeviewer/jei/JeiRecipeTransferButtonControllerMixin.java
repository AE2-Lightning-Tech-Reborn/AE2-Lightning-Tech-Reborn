package com.moakiee.ae2lt.mixin.recipeviewer.jei;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.client.compat.JeiRecipeTransferMetadata;
import com.moakiee.ae2lt.client.tianshu.TianshuDirectUploadClient;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.transfer.IRecipeTransferManager;
import mezz.jei.gui.recipes.RecipeTransferButtonController;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/**
 * The universal transfer API does not expose the displayed category or the success/close callback.
 * Keep only those bridges here. All handlers are static and use invocation operands: optimized JEI
 * jars may reuse local slot 0 (this), and 19.64+ moved the calls from onPress into transferRecipe.
 */
@Mixin(value = RecipeTransferButtonController.class, remap = false)
public abstract class JeiRecipeTransferButtonControllerMixin {
    @WrapOperation(method = {"onPress(Lmezz/jei/api/gui/inputs/IJeiUserInput;)Z", "transferRecipe(ZZ)Z"},
            at = @At(value = "INVOKE", target = "Lmezz/jei/common/transfer/RecipeTransferUtil;transferRecipe(Lmezz/jei/api/recipe/transfer/IRecipeTransferManager;Lnet/minecraft/world/inventory/AbstractContainerMenu;Lmezz/jei/api/gui/IRecipeLayoutDrawable;Lnet/minecraft/world/entity/player/Player;Z)Z"),
            require = 0)
    private static boolean ae2lt$legacyTransfer(IRecipeTransferManager manager, AbstractContainerMenu menu,
            IRecipeLayoutDrawable<?> layout, Player player, boolean maximum, Operation<Boolean> original) {
        return JeiRecipeTransferMetadata.withLayout(menu, layout,
                () -> original.call(manager, menu, layout, player, maximum));
    }

    @WrapOperation(method = {"onPress(Lmezz/jei/api/gui/inputs/IJeiUserInput;)Z", "transferRecipe(ZZ)Z"},
            at = @At(value = "INVOKE", target = "Lmezz/jei/common/transfer/RecipeTransferService;transferRecipe(Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;Lmezz/jei/api/gui/IRecipeLayoutDrawable;Lnet/minecraft/world/entity/player/Player;Z)Z"),
            require = 0)
    private static boolean ae2lt$contextTransfer(@Coerce Object service, AbstractContainerScreen<?> screen,
            IRecipeLayoutDrawable<?> layout, Player player, boolean maximum, Operation<Boolean> original) {
        return JeiRecipeTransferMetadata.withLayout(screen.getMenu(), layout,
                () -> original.call(service, screen, layout, player, maximum));
    }

    @WrapOperation(method = {"onPress(Lmezz/jei/api/gui/inputs/IJeiUserInput;)Z", "transferRecipe(ZZ)Z"},
            at = @At(value = "INVOKE", target = "Lmezz/jei/gui/recipes/RecipesGui;onClose()V"), require = 0)
    private static void ae2lt$keepLegacyRecipePageForDirectUpload(RecipesGui recipesGui, Operation<Void> original) {
        if (!ae2lt$holdRecipePage(recipesGui)) original.call(recipesGui);
    }

    @WrapOperation(method = {"onPress(Lmezz/jei/api/gui/inputs/IJeiUserInput;)Z", "transferRecipe(ZZ)Z"},
            at = @At(value = "INVOKE", target = "Ljava/lang/Runnable;run()V"), require = 0)
    private static void ae2lt$keepRecipePageForDirectUpload(Runnable callback, Operation<Void> original) {
        if (!ae2lt$holdRecipePage(Minecraft.getInstance().screen)) original.call(callback);
    }

    @Unique
    private static boolean ae2lt$holdRecipePage(Screen recipeScreen) {
        var player = Minecraft.getInstance().player;
        // Pinned recipes have their own success callback; always preserve it outside RecipesGui.
        return recipeScreen instanceof RecipesGui
                && Screen.hasAltDown()
                && player != null
                && player.containerMenu instanceof TianshuPatternEncodingTermMenu menu
                && TianshuDirectUploadClient.holdRecipeScreen(menu, recipeScreen);
    }
}
