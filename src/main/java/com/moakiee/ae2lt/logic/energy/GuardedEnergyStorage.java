package com.moakiee.ae2lt.logic.energy;

import java.util.function.BooleanSupplier;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import net.minecraft.network.chat.Component;

/** A captured grid inventory must not be called after a callback detaches its source node. */
final class GuardedEnergyStorage implements MEStorage {
    private final MEStorage delegate;
    private final BooleanSupplier valid;
    GuardedEnergyStorage(MEStorage delegate, BooleanSupplier valid) { this.delegate = delegate; this.valid = valid; }
    @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
        return valid.getAsBoolean() ? delegate.extract(key, amount, mode, source) : 0;
    }
    @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        return valid.getAsBoolean() ? delegate.insert(key, amount, mode, source) : 0;
    }
    @Override public Component getDescription() { return delegate.getDescription(); }
}
