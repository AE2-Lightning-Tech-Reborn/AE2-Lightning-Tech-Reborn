package com.moakiee.ae2lt.machine.crystalcatalyzer.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.moakiee.ae2lt.machine.crystalcatalyzer.CrystalCatalyzerInventory;
import com.moakiee.ae2lt.recipe.RecipeContainerInput;

/**
 * Snapshot of the machine state that's fed into {@link CrystalCatalyzerRecipe#matches}.
 *
 * <p>Both the retained catalyst and available fluid participate in recipe selection.</p>
 */
public final class CrystalCatalyzerRecipeInput extends RecipeContainerInput {
    private final ItemStack catalyst;
    private final FluidStack fluid;

    public CrystalCatalyzerRecipeInput(ItemStack catalyst) {
        this(catalyst, CrystalCatalyzerRecipe.defaultFluidInput());
    }

    public CrystalCatalyzerRecipeInput(ItemStack catalyst, FluidStack fluid) {
        this.catalyst = catalyst == null ? ItemStack.EMPTY : catalyst.copy();
        this.fluid = fluid.copy();
    }

    public static CrystalCatalyzerRecipeInput fromMachine(CrystalCatalyzerInventory inventory) {
        return fromMachine(inventory, CrystalCatalyzerRecipe.defaultFluidInput());
    }

    public static CrystalCatalyzerRecipeInput fromMachine(CrystalCatalyzerInventory inventory, FluidStack fluid) {
        return new CrystalCatalyzerRecipeInput(
                inventory.getStackInSlot(CrystalCatalyzerInventory.SLOT_CATALYST), fluid);
    }

    public FluidStack fluid() {
        return fluid.copy();
    }

    public ItemStack catalyst() {
        return catalyst;
    }

    @Override
    public boolean isEmpty() {
        return catalyst.isEmpty();
    }

    @Override
    public ItemStack getItem(int slotIndex) {
        return slotIndex == 0 ? catalyst : ItemStack.EMPTY;
    }

    @Override
    public int size() {
        return 1;
    }
}
