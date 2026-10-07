package com.moakiee.ae2lt.logic.craft.migration;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.parts.AEBasePart;
import appeng.helpers.patternprovider.PatternContainer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** A bounded view over real slots; the anchor can be a multiblock's aggregate network node. */
final class PatternMigrationSource {
    final Object owner;
    final IGridNode anchor;
    final InternalInventory inventory;
    final Supplier<InternalInventory> currentInventory;
    final BooleanSupplier busy;
    final int priority;
    final int firstSlot;
    final int count;
    final String sortKey;
    final boolean craftingHint;
    final Runnable beginBatch;
    final Runnable endBatch;
    boolean disabled;

    PatternMigrationSource(Object owner, IGridNode anchor, InternalInventory inventory,
            Supplier<InternalInventory> currentInventory, BooleanSupplier busy, int priority,
            int firstSlot, int count, boolean craftingHint, Runnable beginBatch, Runnable endBatch) {
        this.owner = owner;
        this.anchor = anchor;
        this.inventory = inventory;
        this.currentInventory = currentInventory;
        this.busy = busy;
        this.priority = priority;
        this.firstSlot = firstSlot;
        this.count = count;
        this.craftingHint = craftingHint;
        this.beginBatch = beginBatch;
        this.endBatch = endBatch;
        BlockEntity be = blockEntity(owner);
        var pos = be.getBlockPos();
        sortKey = be.getLevel().dimension().location() + "/"
                + String.format(java.util.Locale.ROOT, "%011d/%011d/%011d/%02d",
                        (long) pos.getX() - Integer.MIN_VALUE, (long) pos.getY() - Integer.MIN_VALUE,
                        (long) pos.getZ() - Integer.MIN_VALUE, owner instanceof AEBasePart part ? part.getSide().ordinal() : 6);
    }

    boolean valid(IGrid grid) {
        var be = blockEntity(owner);
        return !disabled && be != null && !be.isRemoved() && be.getLevel() != null
                && be.getLevel().isLoaded(be.getBlockPos())
                && be.getLevel().getBlockEntity(be.getBlockPos()) == be
                && (!(owner instanceof AEBasePart part) || part.getHost().getPart(part.getSide()) == part)
                && anchor.isActive() && anchor.getGrid() == grid
                && (!(owner instanceof PatternContainer container) || container.getGrid() == grid)
                && currentInventory.get() == inventory && firstSlot + count <= inventory.size();
    }

    PatternMigrationTransfer.Slot slot(int index, BooleanSupplier idle) {
        int physical = firstSlot + index;
        return new PatternMigrationTransfer.Slot() {
            @Override public ItemStack read() { return inventory.getStackInSlot(physical); }
            @Override public ItemStack extract(boolean simulate) { return inventory.extractItem(physical, 1, simulate); }
            @Override public ItemStack restore(ItemStack stack) { return inventory.insertItem(physical, stack, false); }
            @Override public boolean canExtract() { return valid(anchor.getGrid()) && !busy.getAsBoolean() && idle.getAsBoolean(); }
        };
    }

    static BlockEntity blockEntity(Object owner) {
        if (owner instanceof BlockEntity be) return be;
        if (owner instanceof AEBasePart part) return part.getBlockEntity();
        return null;
    }
}
