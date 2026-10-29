package com.moakiee.ae2lt.mixin.recipeviewer.jeisupply;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.client.compat.JeiTransferContextBridge;
import com.moakiee.ae2lt.client.compat.JeiWirelessSupplyClient;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Preserves the context and handler chain used by newer JEI transfer services. */
@Pseudo
@Mixin(targets = "mezz.jei.common.transfer.RecipeTransferService", remap = false)
public abstract class JeiWirelessSupplyServiceMixin {
    @WrapOperation(method = "transferRecipe(Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;Lmezz/jei/api/gui/IRecipeLayoutDrawable;Lnet/minecraft/world/entity/player/Player;ZZ)Ljava/util/Optional;",
            remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lmezz/jei/api/recipe/transfer/IRecipeTransferHandler;transferRecipe(Lmezz/jei/api/recipe/transfer/IRecipeTransferContext;Z)Lmezz/jei/api/recipe/transfer/IRecipeTransferError;"), require = 1)
    private static IRecipeTransferError ae2lt$supply(IRecipeTransferHandler<AbstractContainerMenu, Object> handler,
            @Coerce Object context, boolean doTransfer, Operation<IRecipeTransferError> original) {
        return JeiWirelessSupplyClient.transfer(handler,
                JeiTransferContextBridge.container(context), JeiTransferContextBridge.recipe(context),
                JeiTransferContextBridge.slots(context), JeiTransferContextBridge.player(context),
                JeiTransferContextBridge.maximum(context), doTransfer,
                (maximum, take, partialSlots) -> original.call(handler,
                        JeiTransferContextBridge.withSlots(context, partialSlots, maximum), take));
    }
}
