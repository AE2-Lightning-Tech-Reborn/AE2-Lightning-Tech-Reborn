package com.moakiee.ae2lt.mixin.recipeviewer.jeisupply;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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
    private IRecipeTransferError ae2lt$supply(IRecipeTransferHandler<AbstractContainerMenu, Object> handler,
            @Coerce Object context, boolean doTransfer, Operation<IRecipeTransferError> original) {
        var access = (JeiWirelessSupplyContextAccessor) context;
        return JeiWirelessSupplyClient.transfer(handler, access.ae2lt$getContainer(), access.ae2lt$getRecipe(),
                access.ae2lt$getRecipeSlots(), access.ae2lt$getPlayer(), access.ae2lt$isMaxTransfer(), doTransfer,
                (maximum, take, partialSlots) -> original.call(handler,
                        partialSlots == null ? context : ae2lt$withSlots(context, partialSlots, maximum), take));
    }

    private static Object ae2lt$withSlots(Object context, mezz.jei.api.gui.ingredient.IRecipeSlotsView slots, boolean maximum) {
        try {
            var api = Class.forName("mezz.jei.api.recipe.transfer.IRecipeTransferContext");
            var source = java.lang.reflect.Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[]{api},
                    (proxy, method, args) -> method.getName().equals("isMaxTransfer")
                            ? maximum : method.invoke(context, args));
            return context.getClass().getMethod("copyWithRecipeSlots",
                    api, mezz.jei.api.gui.ingredient.IRecipeSlotsView.class).invoke(null, source, slots);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot preserve JEI transfer context for partial refill", error);
        }
    }
}
