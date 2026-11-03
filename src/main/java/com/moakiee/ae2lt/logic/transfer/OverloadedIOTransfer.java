package com.moakiee.ae2lt.logic.transfer;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;

/** One native storage batch. Work never scales with the number of items or buckets. */
public final class OverloadedIOTransfer {
    private OverloadedIOTransfer() {}

    public record Result(long inserted, long remainder, boolean powerBlocked) {}
    private static final Result NONE = new Result(0, 0, false);
    private static final Result NO_POWER = new Result(0, 0, true);

    public static Result move(MEStorage source, MEStorage destination, AEKey key,
                              IActionSource actionSource, IEnergySource energy) {
        return move(source, destination, key, actionSource, energy, Long.MAX_VALUE);
    }

    public static Result move(MEStorage source, MEStorage destination, AEKey key,
                              IActionSource actionSource, IEnergySource energy, long cap) {
        if (cap <= 0) return NONE;
        long available = source.extract(key, cap, Actionable.SIMULATE, actionSource);
        if (available <= 0) return NONE;
        long accepted = destination.insert(key, available, Actionable.SIMULATE, actionSource);
        if (accepted <= 0) return NONE;
        // GTO's StorageHelper does not gate transfers on power. Keep native AE2 pricing,
        // but check both the simulation and the real payment before inserting resources.
        int unitsPerAe = Math.max(1, key.getAmountPerOperation());
        long requested = Math.min(available, accepted);
        long affordable = affordableAmount(requested, unitsPerAe,
                energy.extractAEPower(requested / (double) unitsPerAe, Actionable.SIMULATE, PowerMultiplier.CONFIG));
        if (affordable <= 0) return NO_POWER;
        long extracted = source.extract(key, affordable, Actionable.MODULATE, actionSource);
        if (extracted <= 0) return NONE;
        long paid = affordableAmount(extracted, unitsPerAe,
                energy.extractAEPower(extracted / (double) unitsPerAe, Actionable.MODULATE, PowerMultiplier.CONFIG));
        long inserted = paid > 0 ? destination.insert(key, paid, Actionable.MODULATE, actionSource) : 0;
        long remainder = extracted - inserted;
        if (remainder > 0) remainder -= source.insert(key, remainder, Actionable.MODULATE, actionSource);
        // IEnergySource cannot refund a payment. A destination that refuses its simulated
        // receipt still costs the paid attempt; its resources are returned or remain owned.
        return new Result(inserted, remainder, affordable < requested || paid < extracted);
    }

    private static long affordableAmount(long requested, int unitsPerAe, double availableAe) {
        if (!(availableAe > 0)) return 0;
        // Compare before multiplying to preserve full native-long batches when their AE
        // price is rounded to double, and never grant an unpaid fractional native unit.
        if (availableAe >= requested / (double) unitsPerAe) return requested;
        long affordable = Math.min(requested, (long) (availableAe * unitsPerAe));
        // Recheck the native boundary instead of adding a rounding allowance: for example,
        // (1001 / 125.0) * 125 rounds below 1001 even though its exact AE price was paid.
        if (affordable > 0 && affordable / (double) unitsPerAe > availableAe) return affordable - 1;
        if (affordable < requested && (affordable + 1) / (double) unitsPerAe <= availableAe) return affordable + 1;
        return affordable;
    }
}
