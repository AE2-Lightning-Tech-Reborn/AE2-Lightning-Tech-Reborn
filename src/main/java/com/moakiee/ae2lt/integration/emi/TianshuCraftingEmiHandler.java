package com.moakiee.ae2lt.integration.emi;

import appeng.integration.modules.emi.EmiUseCraftingRecipeHandler;
import com.moakiee.ae2lt.menu.TianshuCraftingTermMenu;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;

/** Native AE2 crafting transfer plus the terminal's server-validated workstation actions. */
final class TianshuCraftingEmiHandler<M extends TianshuCraftingTermMenu> implements EmiRecipeHandler<M> {
    private final EmiUseCraftingRecipeHandler<M> crafting;
    TianshuCraftingEmiHandler(Class<M> menuClass) { crafting = new EmiUseCraftingRecipeHandler<>(menuClass); }

    @Override public EmiPlayerInventory getInventory(AbstractContainerScreen<M> screen) { return crafting.getInventory(screen); }
    @Override public boolean supportsRecipe(EmiRecipe recipe) {
        var category = recipe.getCategory();
        return crafting.supportsRecipe(recipe) || category == VanillaEmiRecipeCategories.SMITHING
                || category == VanillaEmiRecipeCategories.STONECUTTING || category == VanillaEmiRecipeCategories.ANVIL_REPAIRING;
    }
    private Recipe<?> nativeRecipe(EmiRecipe recipe) {
        var level = Minecraft.getInstance().level;
        return level == null || recipe.getId() == null ? null : level.getRecipeManager().byKey(recipe.getId()).orElse(null);
    }
    private ItemStack input(EmiRecipe recipe, int slot) {
        if (slot >= recipe.getInputs().size()) return ItemStack.EMPTY;
        return recipe.getInputs().get(slot).getEmiStacks().stream().map(stack -> stack.getItemStack())
                .filter(stack -> !stack.isEmpty()).findFirst().orElse(ItemStack.EMPTY).copy();
    }
    @Override public boolean canCraft(EmiRecipe recipe, EmiCraftContext<M> context) {
        var menu = context.getScreenHandler();
        if (!menu.canUseWorkstations()) return false;
        if (crafting.supportsRecipe(recipe)) return crafting.canCraft(recipe, context);
        if (recipe.getCategory() == VanillaEmiRecipeCategories.ANVIL_REPAIRING)
            return menu.getAnvilRecipeAvailability(input(recipe, 0), input(recipe, 1)).canTransfer();
        var nativeRecipe = nativeRecipe(recipe);
        return (nativeRecipe instanceof SmithingRecipe || nativeRecipe instanceof StonecutterRecipe)
                && menu.getWorkRecipeAvailability(nativeRecipe).canTransfer();
    }
    @Override public boolean craft(EmiRecipe recipe, EmiCraftContext<M> context) {
        if (!canCraft(recipe, context)) return false;
        var menu = context.getScreenHandler();
        if (crafting.supportsRecipe(recipe)) {
            menu.prepareCraftingTransfer();
            return crafting.craft(recipe, context);
        }
        boolean craftMissing = AbstractContainerScreen.hasControlDown();
        if (recipe.getCategory() == VanillaEmiRecipeCategories.ANVIL_REPAIRING)
            menu.fillAnvilRecipe(input(recipe, 0), input(recipe, 1), craftMissing);
        else menu.fillWorkRecipe(nativeRecipe(recipe).getId().toString(), craftMissing);
        return true;
    }
}
