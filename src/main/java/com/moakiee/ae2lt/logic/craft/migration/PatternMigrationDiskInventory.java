package com.moakiee.ae2lt.logic.craft.migration;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;

/** Optional disk API adapter. Its guarded take/restore protocol also accounts for blank patterns. */
final class PatternMigrationDiskInventory implements InternalInventory {
    private static final String API = "io.github.lounode.ae2pattern.api.PatternDiskApi";
    private static final String HOST = "io.github.lounode.ae2pattern.api.IPatternDiskHost";
    private final InternalInventory rows;

    PatternMigrationDiskInventory(InternalInventory rows) {
        this.rows = rows;
    }

    static boolean isDiskHost(Object owner) {
        try {
            return Class.forName(HOST).isInstance(owner);
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }

    static PatternMigrationDiskInventory create(Object owner, InternalInventory disks, Supplier<IGrid> grid) {
        if (!(owner instanceof IActionHost machine)) return null;
        try {
            Class<?> api = Class.forName(API);
            Method factory = api.getMethod("terminalView", InternalInventory.class, Supplier.class,
                    IActionHost.class, Runnable.class, Supplier.class);
            var be = PatternMigrationSource.blockEntity(owner);
            Object terminal = factory.invoke(null, disks, grid, machine, (Runnable) be::setChanged,
                    (Supplier<net.minecraft.world.level.Level>) be::getLevel);
            return new PatternMigrationDiskInventory((InternalInventory) MigrationReflection.call(terminal, "view"));
        } catch (ClassNotFoundException absent) {
            return null;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unsupported pattern disk migration API", e);
        }
    }

    @Override public int size() { return rows.size(); }
    @Override public ItemStack getStackInSlot(int slot) { return rows.getStackInSlot(slot); }
    @Override public void setItemDirect(int slot, ItemStack stack) { rows.setItemDirect(slot, stack); }

    PatternMigrationTransfer.Slot slot(int index, BooleanSupplier idle) {
        // Keep this exact guard: the disk API remembers the removed row here for a swap rollback.
        // extractItem/insertItem are the terminal's split/upload paths and cannot perform that rollback.
        var guard = rows.getSlotInv(index);
        return new PatternMigrationTransfer.Slot() {
            @Override public ItemStack read() { return rows.getStackInSlot(index); }
            @Override public ItemStack extract(boolean simulate) {
                var sample = guard.getStackInSlot(0).copy();
                if (sample.isEmpty() || sample.getCount() != 1) return ItemStack.EMPTY;
                if (simulate) return sample;
                guard.setItemDirect(0, ItemStack.EMPTY);
                return read().isEmpty() ? sample : ItemStack.EMPTY;
            }
            @Override public ItemStack restore(ItemStack stack) {
                guard.setItemDirect(0, stack.copy());
                return PatternMigrationTransfer.same(read(), stack) ? ItemStack.EMPTY : stack;
            }
            @Override public boolean canExtract() { return idle.getAsBoolean(); }
        };
    }
}
