package com.moakiee.ae2lt.mixin.recipeviewer.jeisupply;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.client.compat.JeiWirelessSupplyClient;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Wraps the legacy handler call used before JEI 15.57 introduced transfer contexts. */
@Pseudo
@Mixin(targets = "mezz.jei.common.transfer.RecipeTransferUtil", remap = false)
public abstract class JeiWirelessSupplyMixin {
    @WrapOperation(method = "transferRecipe(Lmezz/jei/api/recipe/transfer/IRecipeTransferManager;Lnet/minecraft/world/inventory/AbstractContainerMenu;Lmezz/jei/api/gui/IRecipeLayoutDrawable;Lnet/minecraft/world/entity/player/Player;ZZ)Ljava/util/Optional;",
            remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lmezz/jei/api/recipe/transfer/IRecipeTransferHandler;transferRecipe(Lnet/minecraft/world/inventory/AbstractContainerMenu;Ljava/lang/Object;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/world/entity/player/Player;ZZ)Lmezz/jei/api/recipe/transfer/IRecipeTransferError;"), require = 1)
    private static IRecipeTransferError ae2lt$supply(IRecipeTransferHandler<AbstractContainerMenu, Object> handler,
            AbstractContainerMenu menu, Object recipe, IRecipeSlotsView slots, Player player,
            boolean maximum, boolean doTransfer, Operation<IRecipeTransferError> original) {
        return JeiWirelessSupplyClient.transfer(handler, menu, recipe, slots, player, maximum, doTransfer,
                (max, take, partialSlots) -> original.call(handler, menu, recipe,
                        partialSlots == null ? slots : partialSlots, player, max, take));
    }
}
