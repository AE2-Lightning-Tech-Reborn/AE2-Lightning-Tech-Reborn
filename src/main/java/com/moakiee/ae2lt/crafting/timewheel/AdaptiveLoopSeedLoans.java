package com.moakiee.ae2lt.crafting.timewheel;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.moakiee.thunderbolt.core.crafting.planner.Sat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToLongFunction;

/** Optional whole-cycle loans from spare host stock, after the minimum job inputs are secured. */
final class AdaptiveLoopSeedLoans {
    record Request(UUID group, boolean shared, Map<AEKey, Long> unit, int maximumMultiplier) {
        Request {
            unit = Map.copyOf(unit);
            if (maximumMultiplier < 1 || unit.isEmpty()
                    || unit.values().stream().anyMatch(amount -> amount <= 0)) {
                throw new IllegalArgumentException("invalid seed loan request");
            }
        }
    }

    interface Stock {
        long extract(AEKey key, long amount, Actionable mode);

        /** Preserves every returned unit, including storage changes between simulation and commit. */
        void refund(AEKey key, long amount);
    }

    record Result(Map<UUID, Integer> multipliers, KeyCounter borrowed) { }

    static Result borrow(List<Request> requests, ToLongFunction<AEKey> initialShared, Stock stock) {
        var shared = new LinkedHashMap<AEKey, Long>();
        var multipliers = new LinkedHashMap<UUID, Integer>();
        var borrowed = new KeyCounter();
        for (var request : requests) {
            var held = new LinkedHashMap<AEKey, Long>();
            long multiplier = request.maximumMultiplier();
            for (var seed : request.unit().entrySet()) {
                long current = request.shared()
                        ? shared.computeIfAbsent(seed.getKey(), initialShared::applyAsLong)
                        : seed.getValue();
                held.put(seed.getKey(), current);
                long limit = Sat.mul(seed.getValue(), request.maximumMultiplier());
                long wanted = Math.max(0L, limit - current);
                long available = wanted > 0 ? Math.max(0L, Math.min(wanted,
                        stock.extract(seed.getKey(), wanted, Actionable.SIMULATE))) : 0L;
                multiplier = Math.min(multiplier, Sat.add(current, available) / seed.getValue());
            }
            // The planner already secured one complete seed set. Optional stock never changes
            // that admission decision, and a short optional loan must not surface as missing input.
            multiplier = Math.max(1L, multiplier);
            var taken = new LinkedHashMap<AEKey, Long>();
            for (var seed : request.unit().entrySet()) {
                long wanted = Math.max(0L,
                        Sat.mul(seed.getValue(), multiplier) - held.get(seed.getKey()));
                long amount = wanted > 0 ? stock.extract(seed.getKey(), wanted, Actionable.MODULATE) : 0L;
                taken.put(seed.getKey(), Math.max(0L, amount));
            }
            // A host may have less stock than its simulation promised. Only complete sets become
            // protected seeds; return the extra members of any incomplete set to the host.
            for (var seed : request.unit().entrySet()) {
                multiplier = Math.min(multiplier,
                        Sat.add(held.get(seed.getKey()), taken.get(seed.getKey())) / seed.getValue());
            }
            multiplier = Math.max(1L, multiplier);
            for (var seed : request.unit().entrySet()) {
                long target = Sat.mul(seed.getValue(), multiplier);
                long keep = Math.max(0L, target - held.get(seed.getKey()));
                long refund = taken.get(seed.getKey()) - keep;
                if (refund > 0) stock.refund(seed.getKey(), refund);
                if (keep > 0) borrowed.add(seed.getKey(), keep);
                if (request.shared()) shared.merge(seed.getKey(), target, Math::max);
            }
            multipliers.put(request.group(), (int) multiplier);
        }
        return new Result(Map.copyOf(multipliers), borrowed);
    }

    private AdaptiveLoopSeedLoans() { }
}
