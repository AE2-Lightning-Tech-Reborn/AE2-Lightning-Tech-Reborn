package com.moakiee.ae2lt.util;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.ToLongFunction;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** Transactional NeoForge 26 capability views over the machines' existing inventories. */
public final class LegacyTransferBridge {
    // Capabilities on different faces expose the same backing object. Sharing its journal
    // prevents separate views from promising the same amount within one root transaction.
    // Weak keys use identity; weak values avoid retaining unloaded machines through the view.
    private static final LoadingCache<IItemHandlerModifiable, Items> ITEMS = CacheBuilder.newBuilder()
            .weakKeys().weakValues().build(CacheLoader.from(Items::new));
    private static final LoadingCache<IFluidHandler, Fluids> FLUIDS = CacheBuilder.newBuilder()
            .weakKeys().weakValues().build(CacheLoader.from(Fluids::new));
    private static final LoadingCache<IEnergyStorage, Energy> ENERGY = CacheBuilder.newBuilder()
            .weakKeys().weakValues().build(CacheLoader.from(Energy::new));

    private LegacyTransferBridge() {}

    public static ResourceHandler<ItemResource> items(IItemHandlerModifiable handler) {
        return handler == null ? null : ITEMS.getUnchecked(handler);
    }

    public static ResourceHandler<FluidResource> fluids(IFluidHandler handler) {
        return handler == null ? null : FLUIDS.getUnchecked(handler);
    }

    public static EnergyHandler energy(IEnergyStorage storage) {
        return storage == null ? null : ENERGY.getUnchecked(storage);
    }

    public static EnergyHandler itemEnergy(ItemAccess access, DataComponentType<Long> component,
                                           ToLongFunction<ItemStack> capacity) {
        return new ItemEnergy(access, component, capacity);
    }

    private static final class Items extends SnapshotJournal<ItemStack[]> implements ResourceHandler<ItemResource> {
        private final IItemHandlerModifiable source;
        private ItemStack[] staged;

        private Items(IItemHandlerModifiable source) { this.source = Objects.requireNonNull(source); }
        private ItemStack[] copyLive() {
            ItemStack[] result = new ItemStack[source.getSlots()];
            for (int i = 0; i < result.length; i++) result[i] = source.getStackInSlot(i).copy();
            return result;
        }
        private static ItemStack[] copy(ItemStack[] contents) {
            return Arrays.stream(contents).map(ItemStack::copy).toArray(ItemStack[]::new);
        }
        private ItemStack slot(int index) {
            Objects.checkIndex(index, size());
            return isInTransaction() ? staged[index] : source.getStackInSlot(index);
        }
        @Override protected ItemStack[] createSnapshot() {
            if (staged == null) staged = copyLive();
            return copy(staged);
        }
        @Override protected void revertToSnapshot(ItemStack[] snapshot) { staged = snapshot; }
        @Override protected void onRootCommit(ItemStack[] original) {
            try {
                for (int i = 0; i < staged.length; i++) {
                    ItemStack before = original[i];
                    ItemStack after = staged[i];
                    if (ItemStack.matches(before, after)) continue;
                    boolean same = ItemStack.isSameItemSameComponents(before, after);
                    int remove = before.isEmpty() ? 0
                            : same ? Math.max(0, before.getCount() - after.getCount()) : before.getCount();
                    if (remove > 0) {
                        ItemStack extracted = source.extractItem(i, remove, false);
                        if (extracted.getCount() != remove
                                || !ItemStack.isSameItemSameComponents(extracted, before)) {
                            throw new IllegalStateException("Item capability extraction changed before commit at slot " + i);
                        }
                    }
                    int add = after.isEmpty() ? 0
                            : same ? Math.max(0, after.getCount() - before.getCount()) : after.getCount();
                    if (add > 0 && !source.insertItem(i, after.copyWithCount(add), false).isEmpty()) {
                        throw new IllegalStateException("Item capability insertion changed before commit at slot " + i);
                    }
                }
            } finally { staged = null; }
        }
        @Override public int size() { return source.getSlots(); }
        @Override public ItemResource getResource(int index) { return ItemResource.of(slot(index)); }
        @Override public long getAmountAsLong(int index) { return slot(index).getCount(); }
        @Override public boolean isValid(int index, ItemResource resource) {
            Objects.checkIndex(index, size());
            return source.isItemValid(index, resource.toStack(1));
        }
        @Override public long getCapacityAsLong(int index, ItemResource resource) {
            Objects.checkIndex(index, size());
            return source.isItemValid(index, resource.toStack(1)) ? source.getSlotLimit(index) : 0;
        }
        @Override public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            Objects.checkIndex(index, size());
            if (amount <= 0 || resource.isEmpty()) return 0;
            if (!isInTransaction()) staged = null;
            ItemStack current = slot(index);
            if (!current.isEmpty() && !resource.matches(current)) return 0;
            if (!source.isItemValid(index, resource.toStack(1))) return 0;
            int room = Math.max(0, source.getSlotLimit(index) - current.getCount());
            int request = Math.min(amount, room);
            if (request == 0) return 0;
            // getSlotLimit is only an upper bound: a handler can impose a smaller
            // resource-specific limit. Include previous reservations when probing
            // the still-unmodified backing slot, then subtract their receipt.
            int reserved = Math.max(0, current.getCount() - source.getStackInSlot(index).getCount());
            int offered = request + reserved;
            int simulated = offered - source.insertItem(index, resource.toStack(offered), true).getCount();
            int allowed = Math.max(0, Math.min(request, simulated - reserved));
            if (allowed <= 0) return 0;
            updateSnapshots(transaction);
            staged[index] = resource.toStack(current.getCount() + allowed);
            return allowed;
        }
        @Override public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            Objects.checkIndex(index, size());
            if (amount <= 0 || resource.isEmpty()) return 0;
            if (!isInTransaction()) staged = null;
            ItemStack current = slot(index);
            if (!resource.matches(current)) return 0;
            int allowed = source.extractItem(index, Math.min(amount, current.getCount()), true).getCount();
            if (allowed <= 0) return 0;
            updateSnapshots(transaction);
            int remaining = current.getCount() - allowed;
            staged[index] = remaining == 0 ? ItemStack.EMPTY : resource.toStack(remaining);
            return allowed;
        }
    }

    private static final class Fluids extends SnapshotJournal<FluidStack[]> implements ResourceHandler<FluidResource> {
        private final IFluidHandler source;
        private FluidStack[] staged;
        private Fluids(IFluidHandler source) {
            this.source = Objects.requireNonNull(source);
            if (source.getTanks() != 1 && !(source instanceof IndexedFluidHandler)) {
                throw new IllegalArgumentException("Multiple tanks require indexed fluid operations");
            }
        }
        private int fill(int index, FluidStack stack, IFluidHandler.FluidAction action) {
            return source instanceof IndexedFluidHandler indexed
                    ? indexed.fillTank(index, stack, action) : source.fill(stack, action);
        }
        private FluidStack drain(int index, FluidStack stack, IFluidHandler.FluidAction action) {
            return source instanceof IndexedFluidHandler indexed
                    ? indexed.drainTank(index, stack, action) : source.drain(stack, action);
        }
        private FluidStack[] copyLive() {
            FluidStack[] result = new FluidStack[source.getTanks()];
            for (int i = 0; i < result.length; i++) result[i] = source.getFluidInTank(i).copy();
            return result;
        }
        private static FluidStack[] copy(FluidStack[] contents) {
            return Arrays.stream(contents).map(FluidStack::copy).toArray(FluidStack[]::new);
        }
        private FluidStack tank(int index) {
            Objects.checkIndex(index, size());
            return isInTransaction() ? staged[index] : source.getFluidInTank(index);
        }
        @Override protected FluidStack[] createSnapshot() {
            if (staged == null) staged = copyLive();
            return copy(staged);
        }
        @Override protected void revertToSnapshot(FluidStack[] snapshot) { staged = snapshot; }
        @Override protected void onRootCommit(FluidStack[] original) {
            try {
                for (int i = 0; i < staged.length; i++) {
                    int before = original[i].getAmount(), after = staged[i].getAmount();
                    if (after > before && fill(i, staged[i].copyWithAmount(after - before),
                            IFluidHandler.FluidAction.EXECUTE) != after - before) {
                        throw new IllegalStateException("Fluid capability insertion changed before commit at tank " + i);
                    }
                    if (after < before) {
                        var extracted = drain(i, original[i].copyWithAmount(before - after),
                                IFluidHandler.FluidAction.EXECUTE);
                        if (extracted.getAmount() != before - after
                                || !FluidStack.isSameFluidSameComponents(extracted, original[i])) {
                            throw new IllegalStateException("Fluid capability extraction changed before commit at tank " + i);
                        }
                    }
                }
            } finally { staged = null; }
        }
        @Override public int size() { return source.getTanks(); }
        @Override public FluidResource getResource(int index) { return FluidResource.of(tank(index)); }
        @Override public long getAmountAsLong(int index) { return tank(index).getAmount(); }
        @Override public boolean isValid(int index, FluidResource resource) {
            Objects.checkIndex(index, size());
            return source.isFluidValid(index, resource.toStack(1));
        }
        @Override public long getCapacityAsLong(int index, FluidResource resource) {
            Objects.checkIndex(index, size());
            return source.isFluidValid(index, resource.toStack(1)) ? source.getTankCapacity(index) : 0;
        }
        @Override public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            Objects.checkIndex(index, size());
            if (amount <= 0 || resource.isEmpty() || !source.isFluidValid(index, resource.toStack(1))) return 0;
            if (!isInTransaction()) staged = null;
            FluidStack current = tank(index);
            if (!current.isEmpty() && !resource.matches(current)) return 0;
            int request = Math.min(amount, Math.max(0, source.getTankCapacity(index) - current.getAmount()));
            int allowed = fill(index, resource.toStack(request), IFluidHandler.FluidAction.SIMULATE);
            allowed = Math.min(request, allowed);
            if (allowed <= 0) return 0;
            updateSnapshots(transaction);
            staged[index] = resource.toStack(current.getAmount() + allowed);
            return allowed;
        }
        @Override public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            Objects.checkIndex(index, size());
            if (amount <= 0 || resource.isEmpty()) return 0;
            if (!isInTransaction()) staged = null;
            FluidStack current = tank(index);
            if (!resource.matches(current)) return 0;
            int request = Math.min(amount, current.getAmount());
            int allowed = drain(index, resource.toStack(request), IFluidHandler.FluidAction.SIMULATE).getAmount();
            if (allowed <= 0) return 0;
            updateSnapshots(transaction);
            int remaining = current.getAmount() - allowed;
            staged[index] = remaining == 0 ? FluidStack.EMPTY : resource.toStack(remaining);
            return allowed;
        }
    }

    private static final class Energy extends SnapshotJournal<Long> implements EnergyHandler {
        private final IEnergyStorage source;
        private long staged;
        private Energy(IEnergyStorage source) { this.source = source; }
        private long liveAmount() {
            return source instanceof LongEnergyStorage extended
                    ? extended.getStoredEnergyLong() : source.getEnergyStored();
        }
        private long amount() { return isInTransaction() ? staged : liveAmount(); }
        @Override protected Long createSnapshot() { return staged; }
        @Override protected void revertToSnapshot(Long snapshot) { staged = snapshot; }
        @Override protected void onRootCommit(Long original) {
            long delta = staged - original;
            while (delta != 0) {
                int request = (int) Math.min(Integer.MAX_VALUE, Math.abs(delta));
                int got = delta > 0 ? source.receiveEnergy(request, false) : source.extractEnergy(request, false);
                if (got <= 0 || got > request) {
                    throw new IllegalStateException("Energy capability changed before commit");
                }
                delta += delta > 0 ? -got : got;
            }
        }
        @Override public long getAmountAsLong() { return amount(); }
        @Override public long getCapacityAsLong() {
            return source instanceof LongEnergyStorage extended
                    ? extended.getCapacityLong() : source.getMaxEnergyStored();
        }
        @Override public int insert(int amount, TransactionContext transaction) {
            if (amount <= 0 || !source.canReceive()) return 0;
            if (!isInTransaction()) staged = liveAmount();
            int accepted = (int) Math.min(source.receiveEnergy(amount, true), Math.max(0L, getCapacityAsLong() - staged));
            if (accepted > 0) { updateSnapshots(transaction); staged += accepted; }
            return accepted;
        }
        @Override public int extract(int amount, TransactionContext transaction) {
            if (amount <= 0 || !source.canExtract()) return 0;
            if (!isInTransaction()) staged = liveAmount();
            int accepted = (int) Math.min(source.extractEnergy(amount, true), staged);
            if (accepted > 0) { updateSnapshots(transaction); staged -= accepted; }
            return accepted;
        }
    }

    private record ItemEnergy(ItemAccess access, DataComponentType<Long> component,
                              ToLongFunction<ItemStack> capacity) implements EnergyHandler {
        private ItemStack stack() { return access.getResource().toStack(1); }
        @Override public long getAmountAsLong() {
            return Math.max(0L, access.getResource().getOrDefault(component, 0L));
        }
        @Override public long getCapacityAsLong() { return capacity.applyAsLong(stack()); }
        @Override public int insert(int amount, TransactionContext transaction) {
            if (amount <= 0 || access.getAmount() != 1) return 0;
            int accepted = (int) Math.min(amount, Math.max(0L, getCapacityAsLong() - getAmountAsLong()));
            if (accepted == 0) return 0;
            return accepted * access.exchange(access.getResource().with(component, getAmountAsLong() + accepted), 1, transaction);
        }
        @Override public int extract(int amount, TransactionContext transaction) { return 0; }
    }
}
