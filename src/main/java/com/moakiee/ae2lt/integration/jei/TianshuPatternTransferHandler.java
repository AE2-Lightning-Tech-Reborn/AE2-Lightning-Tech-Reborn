package com.moakiee.ae2lt.integration.jei;

/*
 * Transfer behavior adapted from ExtendedAE Plus [ClientPlus] at revision
 * 07f8373c590c0c6d845f794e7c25090e5ef5703e. SPDX-License-Identifier: LGPL-3.0-only
 */

import com.moakiee.ae2lt.client.compat.JeiRecipeTransferMetadata;
import com.moakiee.ae2lt.client.tianshu.TianshuRecipeTransferContext;
import com.moakiee.ae2lt.logic.tianshu.terminal.TianshuEncodingMode;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.util.Optional;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/** Decorates only LT's registered handlers through JEI's stable public transfer API. */
public final class TianshuPatternTransferHandler<M extends TianshuPatternEncodingTermMenu>
        implements IUniversalRecipeTransferHandler<M> {
    private final IUniversalRecipeTransferHandler<M> delegate;

    public TianshuPatternTransferHandler(IUniversalRecipeTransferHandler<M> delegate) {
        this.delegate = delegate;
    }

    @Override public Class<? extends M> getContainerClass() { return delegate.getContainerClass(); }
    @Override public Optional<MenuType<M>> getMenuType() { return delegate.getMenuType(); }

    @Override
    public IRecipeTransferError transferRecipe(M menu, Object recipeBase, IRecipeSlotsView slotsView,
            Player player, boolean maxTransfer, boolean doTransfer) {
        if (!doTransfer) return delegate.transferRecipe(menu, recipeBase, slotsView, player, maxTransfer, false);
        var tianshuMenu = menu;
        // An actual transfer starts a new metadata generation. Clear before every early return so
        // recipes without a discoverable type/ID can never inherit the previous recipe's ID.
        TianshuRecipeTransferContext.clear(tianshuMenu);
        if (tianshuMenu.tianshuMode == TianshuEncodingMode.CLOSED_LOOP) {
            var output = firstDisplayedItemOutput(slotsView);
            if (tianshuMenu.markClosedLoopPrimaryOutput(output)) {
                tianshuMenu.autoFillClosedLoop();
                return null;
            }
            return delegate.transferRecipe(menu, recipeBase, slotsView, player, maxTransfer, true);
        }
        tianshuMenu.resetProcessingEncoding();
        var fallback = JeiRecipeTransferMetadata.snapshotFor(tianshuMenu);
        TianshuRecipeTransferContext.captureVanillaRecipe(
                tianshuMenu,
                recipeBase,
                fallback.sourceKey(),
                fallback.defaultAliases());
        var error = delegate.transferRecipe(menu, recipeBase, slotsView, player, maxTransfer, true);
        if (error == null && !ModList.get().isLoaded("emi") && Screen.hasAltDown()) {
            tianshuMenu.encodeAndUploadDirectly();
        }
        return error;
    }

    private static ItemStack firstDisplayedItemOutput(IRecipeSlotsView slotsView) {
        if (slotsView == null) return ItemStack.EMPTY;
        return slotsView.getSlotViews(RecipeIngredientRole.OUTPUT).stream()
                .map(slot -> slot.getDisplayedIngredient(VanillaTypes.ITEM_STACK)
                        .orElse(ItemStack.EMPTY))
                .filter(stack -> !stack.isEmpty())
                .findFirst()
                .map(ItemStack::copy)
                .orElse(ItemStack.EMPTY);
    }
}
