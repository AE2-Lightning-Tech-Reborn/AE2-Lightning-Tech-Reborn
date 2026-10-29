package com.moakiee.ae2lt.logic.provider;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import com.moakiee.ae2lt.logic.ResourceIngressPolicy;

/**
 * Return inventory (9 slots, or pages*9 for extended providers) with unlimited
 * per-type capacity and automatic same-type merging. Replaces the vanilla
 * 9-slot return inventory.
 * <p>
 * Insert-only: extraction is blocked to prevent players from using it
 * as storage. Items leave via {@link #injectIntoNetwork} (AE2 doWork)
 * or {@link #addDrops} (block broken).
 */
public class UnlimitedReturnInventory extends PatternProviderReturnInventory {

    @Nullable
    private ReturnSlotFilter slotFilter;
    private Predicate<AEKey> admissionPolicy = key -> true;
    private boolean inserting;

    private UnlimitedReturnInventory(Runnable listener) {
        super(listener);
    }

    public static UnlimitedReturnInventory create(Runnable listener,
                                                  @Nullable ReturnSlotFilter filter) {
        var inv = new UnlimitedReturnInventory(listener);
        inv.slotFilter = filter;
        return inv;
    }

    public void setAdmissionPolicy(Predicate<AEKey> admissionPolicy) {
        this.admissionPolicy = Objects.requireNonNull(admissionPolicy, "admissionPolicy");
    }

    public void setOnlineSupplier(BooleanSupplier online) {
        Objects.requireNonNull(online, "online");
        setAdmissionPolicy(key -> ResourceIngressPolicy.canAccept(key, online.getAsBoolean()));
    }

    public boolean canAcceptNewResource(AEKey key) {
        return isAllowed(key);
    }

    @Override
    public boolean isAllowed(AEKey key) {
        return key != null && super.isAllowed(key) && admissionPolicy.test(key);
    }

    public static UnlimitedReturnInventory create(Runnable listener,
                                                  @Nullable ReturnSlotFilter filter,
                                                  int slots) {
        // AE2 reads this global value from the super constructor. Keep the
        // mutation window minimal and always restore it, even if construction fails.
        UnlimitedReturnInventory inv;
        int saved = PatternProviderReturnInventory.NUMBER_OF_SLOTS;
        try {
            PatternProviderReturnInventory.NUMBER_OF_SLOTS = slots;
            inv = new UnlimitedReturnInventory(listener);
        } finally {
            PatternProviderReturnInventory.NUMBER_OF_SLOTS = saved;
        }
        inv.slotFilter = filter;
        return inv;
    }

    /**
     * Merge-insert: find an existing slot holding the same key and stack onto
     * it; otherwise use the first empty slot. If all 18 type-slots are
     * occupied by different keys, reject the insert.
     */
    @Override
    public long insert(int slot, AEKey what, long amount, Actionable mode) {
        if (slot < 0 || slot >= size()) return 0;
        return insertMerged(slot, what, amount, mode);
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return insertMerged(-1, what, amount, mode);
    }

    private long insertMerged(int requestedSlot, AEKey what, long amount, Actionable mode) {
        if (what == null || amount <= 0 || inserting || !canInsert()) return 0;
        Objects.requireNonNull(mode, "mode");
        inserting = true;
        try {
            if (!canAcceptNewResource(what)) return 0;
            if (slotFilter != null) {
                if (requestedSlot >= 0) {
                    if (!slotFilter.isAllowed(requestedSlot, what)) return 0;
                } else {
                    boolean allowed = false;
                    for (int i = 0; i < size(); i++) {
                        if (slotFilter.isAllowed(i, what)) {
                            allowed = true;
                            break;
                        }
                    }
                    if (!allowed) return 0;
                }
            }

            for (int i = 0; i < size(); i++) {
                if (what.equals(getKey(i))) {
                    long stored = getAmount(i);
                    long accepted = Math.min(amount, Long.MAX_VALUE - stored);
                    if (accepted > 0 && mode == Actionable.MODULATE) {
                        setStack(i, new GenericStack(what, stored + accepted));
                    }
                    return accepted;
                }
            }

            for (int i = 0; i < size(); i++) {
                if (getKey(i) == null) {
                    if (mode == Actionable.MODULATE) {
                        setStack(i, new GenericStack(what, amount));
                    }
                    return amount;
                }
            }
            return 0;
        } finally {
            inserting = false;
        }
    }

    @Override
    public long extract(int slot, AEKey what, long amount, Actionable mode) {
        return 0;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public long getMaxAmount(AEKey key) {
        return Long.MAX_VALUE;
    }

    @Override
    public long getCapacity(AEKeyType space) {
        return Long.MAX_VALUE;
    }
}
