package com.moakiee.ae2lt.recipe.compat;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Exposes a 26.1 transactional item capability to unchanged slot-oriented transfer logic. */
public record LegacyItemHandlerView(ResourceHandler<ItemResource> handler) implements IItemHandler {
    @Override
    public int getSlots() {
        return handler.size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        ItemResource resource = handler.getResource(slot);
        return resource.isEmpty() ? ItemStack.EMPTY : resource.toStack(handler.getAmountAsInt(slot));
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        try (var transaction = Transaction.openRoot()) {
            int inserted = handler.insert(slot, ItemResource.of(stack), stack.getCount(), transaction);
            if (!simulate) transaction.commit();
            return stack.copyWithCount(stack.getCount() - inserted);
        }
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemResource resource = handler.getResource(slot);
        if (resource.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        try (var transaction = Transaction.openRoot()) {
            int extracted = handler.extract(slot, resource, amount, transaction);
            if (!simulate) transaction.commit();
            return resource.toStack(extracted);
        }
    }

    @Override
    public int getSlotLimit(int slot) {
        ItemResource resource = handler.getResource(slot);
        return resource.isEmpty() ? 64 : handler.getCapacityAsInt(slot, resource);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return handler.isValid(slot, ItemResource.of(stack));
    }
}
