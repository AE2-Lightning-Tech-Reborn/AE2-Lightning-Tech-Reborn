package com.moakiee.ae2lt.recipe.compat;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.helpers.ResourceConversion;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** Bridges a proxied AE inventory without treating its displayed stacks as owned slot contents. */
public final class ActionableInventoryTransfer extends SnapshotJournal<List<ActionableInventoryTransfer.Operation>> {
    public record Operation(int slot, AEKey key, int amount, boolean insertion) {}

    @FunctionalInterface
    public interface Budget {
        long limit(int slot, AEKey key, long requested, boolean insertion, List<Operation> pending);
    }

    private final GenericInternalInventory inventory;
    private final Budget budget;
    private List<Operation> pending = List.of();
    private boolean committing;
    private final ResourceHandler<ItemResource> items = new View<>(ResourceConversion.ITEM, ItemResource.EMPTY);
    private final ResourceHandler<FluidResource> fluids = new View<>(ResourceConversion.FLUID, FluidResource.EMPTY);

    public ActionableInventoryTransfer(GenericInternalInventory inventory, Budget budget) {
        this.inventory = Objects.requireNonNull(inventory);
        this.budget = budget;
    }

    public ResourceHandler<ItemResource> items() { return items; }
    public ResourceHandler<FluidResource> fluids() { return fluids; }

    @Override protected List<Operation> createSnapshot() { return pending; }
    @Override protected void revertToSnapshot(List<Operation> snapshot) { pending = snapshot; }

    @Override protected void onRootCommit(List<Operation> original) {
        committing = true;
        try {
            for (var operation : pending) {
                long moved = operation.insertion()
                        ? inventory.insert(operation.slot(), operation.key(), operation.amount(), Actionable.MODULATE)
                        : inventory.extract(operation.slot(), operation.key(), operation.amount(), Actionable.MODULATE);
                if (moved != operation.amount()) {
                    throw new IllegalStateException("Proxied inventory changed during transaction commit");
                }
            }
        } finally {
            pending = List.of();
            committing = false;
        }
    }

    private long reservedExtraction(int slot, AEKey key) {
        long amount = 0;
        for (var operation : pending) {
            if (!operation.insertion() && operation.slot() == slot && operation.key().equals(key)) amount += operation.amount();
        }
        return amount;
    }

    private final class View<R extends Resource> implements ResourceHandler<R> {
        private final ResourceConversion<R> conversion;
        private final R empty;
        View(ResourceConversion<R> conversion, R empty) { this.conversion = conversion; this.empty = empty; }
        @Override public int size() { return inventory.size(); }
        @Override public R getResource(int index) {
            Objects.checkIndex(index, size());
            var key = inventory.getKey(index);
            return key != null && key.getType() == conversion.getKeyType() ? conversion.getVariant(key) : empty;
        }
        @Override public long getAmountAsLong(int index) {
            var key = inventory.getKey(Objects.checkIndex(index, size()));
            return key != null && key.getType() == conversion.getKeyType()
                    ? Math.max(0, inventory.getAmount(index) - reservedExtraction(index, key)) : 0;
        }
        @Override public long getCapacityAsLong(int index, R resource) {
            Objects.checkIndex(index, size());
            return resource.isEmpty() ? 0 : inventory.getMaxAmount(conversion.getKey(resource));
        }
        @Override public boolean isValid(int index, R resource) {
            Objects.checkIndex(index, size());
            return !resource.isEmpty() && inventory.isAllowedIn(index, conversion.getKey(resource));
        }
        @Override public int insert(int index, R resource, int amount, TransactionContext transaction) {
            return transfer(index, resource, amount, transaction, true);
        }
        @Override public int extract(int index, R resource, int amount, TransactionContext transaction) {
            return transfer(index, resource, amount, transaction, false);
        }
        private int transfer(int index, R resource, int amount, TransactionContext transaction, boolean insert) {
            Objects.checkIndex(index, size());
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (amount == 0 || committing || (insert ? !inventory.canInsert() : !inventory.canExtract())) return 0;
            var key = conversion.getKey(resource);
            if (insert && !inventory.isAllowedIn(index, key)) return 0;
            long allowed;
            if (insert) {
                allowed = inventory.insert(index, key, amount, Actionable.SIMULATE);
            } else {
                allowed = inventory.extract(index, key, amount, Actionable.SIMULATE);
                if (budget == null) allowed = Math.max(0, allowed - reservedExtraction(index, key));
            }
            if (budget != null) allowed = Math.min(allowed, budget.limit(index, key, allowed, insert, pending));
            int accepted = (int) Math.min(amount, Math.max(0, allowed));
            if (accepted == 0) return 0;
            updateSnapshots(transaction);
            var next = new ArrayList<>(pending);
            next.add(new Operation(index, key, accepted, insert));
            pending = List.copyOf(next);
            return accepted;
        }
    }
}
