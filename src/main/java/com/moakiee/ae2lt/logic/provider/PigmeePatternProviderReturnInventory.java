package com.moakiee.ae2lt.logic.provider;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.helpers.externalstorage.GenericStackInv;
import com.moakiee.ae2lt.logic.ResourceIngressPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class PigmeePatternProviderReturnInventory extends GenericStackInv {
    public static final int SLOT_COUNT = 9;

    private boolean draining;
    private BooleanSupplier onlineSupplier = () -> true;

    public PigmeePatternProviderReturnInventory(Runnable listener) {
        super(listener, SLOT_COUNT);
        useRegisteredCapacities();
    }

    public void setOnlineSupplier(BooleanSupplier onlineSupplier) {
        this.onlineSupplier = Objects.requireNonNull(onlineSupplier, "onlineSupplier");
    }

    @Override
    public boolean isAllowed(AEKey what) {
        return what != null && super.isAllowed(what)
                && ResourceIngressPolicy.canAccept(what, onlineSupplier.getAsBoolean());
    }

    @Override
    public long insert(int slot, AEKey what, long amount, Actionable mode) {
        if (!canInsert() || what == null || !isAllowed(what)) return 0;
        return super.insert(slot, what, amount, mode);
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canInsert() {
        return !draining;
    }

    public boolean drainInto(MEStorage storage, IActionSource source) {
        if (draining) return false;
        boolean changed = false;
        draining = true;
        try {
            for (int slot = 0; slot < size(); slot++) {
                var stack = getStack(slot);
                if (stack == null) {
                    continue;
                }
                long inserted = storage.insert(stack.what(), stack.amount(), Actionable.MODULATE, source);
                if (inserted <= 0) {
                    continue;
                }
                long remaining = stack.amount() - inserted;
                setStack(slot, remaining > 0 ? new GenericStack(stack.what(), remaining) : null);
                changed = true;
            }
        } finally {
            draining = false;
        }
        return changed;
    }

    public void addDrops(List<ItemStack> drops, Level level, BlockPos pos) {
        for (int slot = 0; slot < size(); slot++) {
            var stack = getStack(slot);
            if (stack != null) {
                stack.what().addDrops(stack.amount(), drops, level, pos);
            }
        }
    }
}
