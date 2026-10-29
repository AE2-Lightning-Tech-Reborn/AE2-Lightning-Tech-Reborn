package com.moakiee.ae2lt.mixin.recipeviewer.jei;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.client.compat.JeiRecipeTransferMetadata;
import com.moakiee.ae2lt.client.tianshu.TianshuDirectUploadClient;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/**
 * The universal transfer API does not expose the displayed category or the success/close callback.
 * Keep only those bridges here. All handlers are static and use invocation operands: optimized JEI
 * jars may reuse local slot 0 (this). JEI 15.68 introduced the separate button controller.
 */
@Pseudo
@Mixin(targets = "mezz.jei.gui.recipes.RecipeTransferButtonController", remap = false)
public abstract class JeiRecipeTransferButtonControllerMixin {
    @WrapOperation(method = {"transferRecipe(ZZ)Z"},
            at = @At(value = "INVOKE", target = "Lmezz/jei/common/transfer/RecipeTransferService;transferRecipe(Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;Lmezz/jei/api/gui/IRecipeLayoutDrawable;Lnet/minecraft/world/entity/player/Player;Z)Z"),
            require = 1)
    private static boolean ae2lt$contextTransfer(@Coerce Object service, AbstractContainerScreen<?> screen,
            IRecipeLayoutDrawable<?> layout, Player player, boolean maximum, Operation<Boolean> original) {
        return JeiRecipeTransferMetadata.withLayout(screen.getMenu(), layout,
                () -> original.call(service, screen, layout, player, maximum));
    }

    @WrapOperation(method = {"transferRecipe(ZZ)Z"},
            at = @At(value = "INVOKE", target = "Ljava/lang/Runnable;run()V"), require = 1)
    private static void ae2lt$keepRecipePageForDirectUpload(Runnable callback, Operation<Void> original) {
        if (!ae2lt$holdRecipePage(Minecraft.getInstance().screen)) original.call(callback);
    }

    @Unique
    private static boolean ae2lt$holdRecipePage(Screen recipeScreen) {
        var player = Minecraft.getInstance().player;
        // Pinned recipes have their own success callback; always preserve it outside RecipesGui.
        return recipeScreen instanceof RecipesGui
                && player != null
                && player.containerMenu instanceof TianshuPatternEncodingTermMenu tianshuMenu
                && TianshuDirectUploadClient.holdRecipeScreen(tianshuMenu, recipeScreen);
    }
}
