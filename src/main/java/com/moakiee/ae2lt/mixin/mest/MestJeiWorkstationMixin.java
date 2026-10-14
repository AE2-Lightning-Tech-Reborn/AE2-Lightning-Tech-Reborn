package com.moakiee.ae2lt.mixin.mest;

import com.lhy.mest.terminal.MESTMenu;
import com.moakiee.ae2lt.integration.jei.TianshuCraftingTransferHandler;
import com.moakiee.ae2lt.integration.mest.client.MestTianshuClient;
import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** MEST's universal handler also exposes the focused native anvil, smithing and stonecutting pages. */
@Mixin(targets = "com.lhy.mest.integration.MestJeiPlugin$MestPatternEncodingTransferHandler", remap = false)
public abstract class MestJeiWorkstationMixin {
    @Shadow @Final private IRecipeTransferHandlerHelper helper;

    @Inject(method = "transferRecipe(Lcom/lhy/mest/terminal/MESTMenu;Ljava/lang/Object;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/world/entity/player/Player;ZZ)Lmezz/jei/api/recipe/transfer/IRecipeTransferError;",
            at = @At("HEAD"), cancellable = true)
    private void ae2lt$workstation(MESTMenu outer, Object recipe, IRecipeSlotsView slots, Player player,
            boolean max, boolean transfer, CallbackInfoReturnable<IRecipeTransferError> cir) {
        var panel = MestTianshuClient.recipePanel(outer);
        if (panel == null || !(panel.source.getMenu() instanceof TianshuCraftingTermMenu menu)) return;
        var handler = new TianshuCraftingTransferHandler<>(TianshuCraftingTermMenu.class, null, helper);
        cir.setReturnValue(MestTianshuClient.input(menu,
                () -> handler.transferRecipe(menu, recipe, slots, player, max, transfer)));
    }
}
