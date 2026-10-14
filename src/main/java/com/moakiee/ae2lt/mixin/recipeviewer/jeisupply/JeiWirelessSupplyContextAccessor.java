package com.moakiee.ae2lt.mixin.recipeviewer.jeisupply;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Accesses the newer JEI context without linking its API on older JEI versions. */
@Pseudo
@Mixin(targets = "mezz.jei.common.transfer.RecipeTransferContext", remap = false)
public interface JeiWirelessSupplyContextAccessor {
    @Invoker("getContainer")
    AbstractContainerMenu ae2lt$getContainer();

    @Invoker("getRecipe")
    Object ae2lt$getRecipe();

    @Invoker("getRecipeSlots")
    IRecipeSlotsView ae2lt$getRecipeSlots();

    @Invoker("getPlayer")
    Player ae2lt$getPlayer();

    @Invoker("isMaxTransfer")
    boolean ae2lt$isMaxTransfer();
}
