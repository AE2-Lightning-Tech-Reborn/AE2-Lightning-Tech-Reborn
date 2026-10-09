package com.moakiee.ae2lt.machine.firmament;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * Capability-facing inventory: all sides insert inputs and extract output.
 */
public class FirmamentConversionAutomationInventory implements IItemHandlerModifiable {
    private final FirmamentConversionInventory inventory;
    private final java.util.function.BooleanSupplier enabled;

    public FirmamentConversionAutomationInventory(FirmamentConversionInventory inventory) {
        this(inventory, () -> true);
    }

    public FirmamentConversionAutomationInventory(FirmamentConversionInventory inventory, java.util.function.BooleanSupplier enabled) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.enabled = Objects.requireNonNull(enabled, "enabled");
    }

    @Override
    public int getSlots() {
        return inventory.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return inventory.getStackInSlot(slot);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (enabled.getAsBoolean()) inventory.setStackInSlot(slot, stack);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        validateSlotIndex(slot);
        Objects.requireNonNull(stack, "stack");

        if (!enabled.getAsBoolean()) return stack;
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (!inventory.isInputSlot(slot)) {
            return stack;
        }
        return inventory.insertItem(slot, stack, simulate);
    }

    public ItemStack insertItem(ItemStack stack, boolean simulate) {
        Objects.requireNonNull(stack, "stack");
        if (!enabled.getAsBoolean()) return stack;
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remainder = stack;
        for (int slot = FirmamentConversionInventory.SLOT_INPUT_0;
             slot <= FirmamentConversionInventory.SLOT_INPUT_2;
             slot++) {
            remainder = inventory.insertItem(slot, remainder, simulate);
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return remainder;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        validateSlotIndex(slot);
        if (!enabled.getAsBoolean() || !inventory.isOutputSlot(slot)) {
            return ItemStack.EMPTY;
        }
        return inventory.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return inventory.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        validateSlotIndex(slot);
        if (stack.isEmpty()) {
            return false;
        }
        return enabled.getAsBoolean() && inventory.isInputSlot(slot);
    }

    private void validateSlotIndex(int slot) {
        if (slot < 0 || slot >= inventory.getSlots()) {
            throw new IllegalArgumentException(
                    "Slot " + slot + " not in valid range - [0," + inventory.getSlots() + ")");
        }
    }
}
