package com.moakiee.ae2lt.logic.craft.migration;

import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;

/** One physical item at a time. The recovery slot owns an extracted item until a receipt exists. */
public final class PatternMigrationTransfer {
    private PatternMigrationTransfer() {}

    public interface Slot {
        ItemStack read();
        ItemStack extract(boolean simulate);
        ItemStack restore(ItemStack stack);
        default boolean canExtract() { return true; }
    }

    public interface Destination {
        boolean canAccept(ItemStack stack);
        ItemStack insert(ItemStack stack);
        /** Only physical destination slots can audit an insertion whose callback threw after committing. */
        default boolean received(ItemStack stack) { return false; }
    }

    public enum Result { MOVED, SOURCE_CHANGED, BLOCKED, RESTORED, RECOVERY_REQUIRED }

    public static Result move(Slot source, ItemStack snapshot, Destination target, Consumer<ItemStack> custody) {
        if (!same(source.read(), snapshot)) return Result.SOURCE_CHANGED;
        var unit = snapshot.copyWithCount(1);
        if (!target.canAccept(unit)) return Result.BLOCKED;
        var simulated = source.extract(true);
        if (!same(simulated, unit) || !same(source.read(), snapshot) || !source.canExtract()) return Result.SOURCE_CHANGED;

        ItemStack held;
        try {
            held = source.extract(false);
        } catch (RuntimeException e) {
            // Known physical inventories can throw from a listener after the removal already happened.
            var after = source.read();
            var expectedAfter = snapshot.copyWithCount(snapshot.getCount() - 1);
            if (!same(after, expectedAfter)) return Result.SOURCE_CHANGED;
            held = unit;
            custody.accept(held.copy());
            return restore(source, held, custody);
        }
        if (held.isEmpty()) return Result.SOURCE_CHANGED;
        custody.accept(held.copy());
        if (!same(held, unit)) return restore(source, held, custody);

        try {
            var remainder = target.insert(held.copy());
            if (remainder.isEmpty()) {
                custody.accept(ItemStack.EMPTY);
                return Result.MOVED;
            }
            // Partial receipts never recreate the part already accepted by a destination.
            custody.accept(remainder.copy());
            return restore(source, remainder, custody);
        } catch (RuntimeException e) {
            if (target.received(held)) {
                custody.accept(ItemStack.EMPTY);
                return Result.MOVED;
            }
            return restore(source, held, custody);
        }
    }

    private static Result restore(Slot source, ItemStack held, Consumer<ItemStack> custody) {
        var before = source.read().copy();
        try {
            var remainder = source.restore(held.copy());
            custody.accept(remainder.copy());
            return remainder.isEmpty() ? Result.RESTORED : Result.RECOVERY_REQUIRED;
        } catch (RuntimeException e) {
            var expected = before.isEmpty() ? held : before.copyWithCount(before.getCount() + held.getCount());
            if ((before.isEmpty() || ItemStack.isSameItemSameTags(before, held))
                    && same(source.read(), expected)) {
                custody.accept(ItemStack.EMPTY);
                return Result.RESTORED;
            }
            return Result.RECOVERY_REQUIRED;
        }
    }

    public static boolean same(ItemStack a, ItemStack b) {
        return a.isEmpty() && b.isEmpty()
                || a.getCount() == b.getCount() && ItemStack.isSameItemSameTags(a, b);
    }
}
