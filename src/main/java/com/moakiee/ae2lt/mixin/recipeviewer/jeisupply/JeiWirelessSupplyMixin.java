package com.moakiee.ae2lt.mixin.recipeviewer.jeisupply;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.client.compat.JeiWirelessSupplyClient;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.*;
import mezz.jei.common.transfer.RecipeTransferService;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Preserves JEI 11's transfer context and completion lifecycle while supplying missing inputs. */
@Mixin(value = RecipeTransferService.class, remap = false)
public abstract class JeiWirelessSupplyMixin {
    @WrapOperation(method = "transferRecipe(Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;Lmezz/jei/api/gui/IRecipeLayoutDrawable;Lnet/minecraft/world/entity/player/Player;ZZ)Ljava/util/Optional;",
            at = @At(value = "INVOKE", target = "Lmezz/jei/api/recipe/transfer/IRecipeTransferHandler;transferRecipe(Lmezz/jei/api/recipe/transfer/IRecipeTransferContext;Z)Lmezz/jei/api/recipe/transfer/IRecipeTransferError;"), require = 1)
    private IRecipeTransferError ae2lt$supply(IRecipeTransferHandler<AbstractContainerMenu, Object> handler,
            IRecipeTransferContext<Object, AbstractContainerMenu> context, boolean doTransfer,
            Operation<IRecipeTransferError> original) {
        return JeiWirelessSupplyClient.transfer(handler, context.getContainer(), context.getRecipe(),
                context.getRecipeSlots(), context.getPlayer(), context.isMaxTransfer(), doTransfer,
                (max, take, slots) -> original.call(handler, new IRecipeTransferContext<Object, AbstractContainerMenu>() {
                    public int getTransferId() { return context.getTransferId(); }
                    public void completeRecipeTransfer(RecipeTransferResult result) { context.completeRecipeTransfer(result); }
                    public Object getRecipe() { return context.getRecipe(); }
                    public mezz.jei.api.recipe.RecipeType<Object> getRecipeType() { return context.getRecipeType(); }
                    public AbstractContainerMenu getContainer() { return context.getContainer(); }
                    public net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<AbstractContainerMenu> getScreen() { return context.getScreen(); }
                    public IRecipeSlotsView getRecipeSlots() { return slots == null ? context.getRecipeSlots() : slots; }
                    public net.minecraft.world.entity.player.Player getPlayer() { return context.getPlayer(); }
                    public boolean isMaxTransfer() { return max; }
                }, take));
    }
}
