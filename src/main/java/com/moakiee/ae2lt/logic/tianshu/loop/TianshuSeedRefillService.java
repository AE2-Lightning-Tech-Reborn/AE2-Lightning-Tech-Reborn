package com.moakiee.ae2lt.logic.tianshu.loop;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import net.minecraft.network.chat.Component;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerPortBlockEntity;
import com.moakiee.thunderbolt.core.crafting.planner.Sat;
import java.util.LinkedHashMap;
import java.util.Map;

/** Server-authoritative implementation of the manual "refill seeds" actions. */
public final class TianshuSeedRefillService {
    public static RefillResult refillAll(TianshuSupercomputerPortBlockEntity target) {
        if (target == null) return RefillResult.UNAVAILABLE;
        var repository = target.getClosedLoopPatternRepository();
        if (repository == null) return RefillResult.UNAVAILABLE;
        return refill(target, requirements(repository.patterns()));
    }

    static Map<AEKey, Long> requirements(Iterable<ClosedLoopPatternPayload> patterns) {
        var required = new LinkedHashMap<AEKey, Long>();
        for (var payload : patterns) {
            if (!payload.enabled()) continue;
            for (var entry : requirements(payload).entrySet()) {
                // The storage is shared and seeds are allocated to jobs only when they start.
                // A seed already present for one pattern can also start another pattern later.
                required.merge(entry.getKey(), entry.getValue(), Math::max);
            }
        }
        return Map.copyOf(required);
    }

    public static Map<AEKey, Long> requirements(ClosedLoopPatternPayload payload) {
        var result = new LinkedHashMap<AEKey, Long>();
        if (payload != null) {
            for (var seed : payload.seeds()) {
                long perTask = Sat.mul(seed.amount(), payload.executionSeedMultiplier());
                result.merge(seed.what(), Sat.mul(perTask, payload.storedTaskMultiplier()), Sat::add);
            }
        }
        return Map.copyOf(result);
    }

    private static RefillResult refill(
            TianshuSupercomputerPortBlockEntity target, Map<AEKey, Long> required) {
        var grid = target.getGrid();
        if (!target.isFormed() || grid == null
                || !target.getFunctionProfile().supportsClosedLoopSeeds()) {
            return RefillResult.UNAVAILABLE;
        }
        var controller = target.getController();
        if (controller == null) return RefillResult.UNAVAILABLE;
        var seeds = new MEStorage() {
            @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                return controller.insertReusableSeed(key, amount, mode);
            }
            @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
                return controller.extractReusableSeed(key, amount, mode);
            }
            @Override public void getAvailableStacks(KeyCounter out) {
                out.addAll(controller.reusableSeedSnapshot());
            }
            @Override public Component getDescription() { return controller.getDisplayName(); }
        };
        return reconcile(required, seeds, grid.getStorageService().getInventory(), target.getActionSource());
    }

    /** Reconcile only seeds physically in storage; running CPUs hold their loans separately. */
    static RefillResult reconcile(Map<AEKey, Long> required, MEStorage seeds,
                                  MEStorage network, IActionSource source) {
        var moved = new LinkedHashMap<AEKey, Long>();
        var returned = new LinkedHashMap<AEKey, Long>();
        var networkMissing = new LinkedHashMap<AEKey, Long>();
        var storageBlocked = new LinkedHashMap<AEKey, Long>();
        var returnBlocked = new LinkedHashMap<AEKey, Long>();
        // Return obsolete/excess keys first so they cannot occupy the cells needed by new seeds.
        for (var entry : seeds.getAvailableStacks()) {
            long excess = Math.max(0L, entry.getLongValue()
                    - required.getOrDefault(entry.getKey(), 0L));
            if (excess <= 0) continue;
            long accepted = move(seeds, network, entry.getKey(), excess, source);
            if (accepted > 0) returned.put(entry.getKey(), accepted);
            if (accepted < excess) returnBlocked.put(entry.getKey(), excess - accepted);
        }
        for (var entry : required.entrySet()) {
            long current = seeds.extract(entry.getKey(), Long.MAX_VALUE, Actionable.SIMULATE, source);
            long need = Math.max(0L, entry.getValue() - current);
            if (need <= 0) continue;
            long canStore = seeds.insert(entry.getKey(), need, Actionable.SIMULATE, source);
            long available = canStore > 0
                    ? network.extract(entry.getKey(), canStore, Actionable.SIMULATE, source) : 0L;
            long inserted = move(network, seeds, entry.getKey(), available, source);
            if (inserted > 0) moved.put(entry.getKey(), inserted);
            long unavailableFromNetwork = Math.max(0L, canStore - available);
            long rejectedByStorage = Math.max(0L, need - canStore) + Math.max(0L, available - inserted);
            if (unavailableFromNetwork > 0) networkMissing.put(entry.getKey(), unavailableFromNetwork);
            if (rejectedByStorage > 0) storageBlocked.put(entry.getKey(), rejectedByStorage);
        }
        return new RefillResult(true, Map.copyOf(moved), Map.copyOf(returned),
                Map.copyOf(networkMissing), Map.copyOf(storageBlocked), Map.copyOf(returnBlocked));
    }

    private static long move(MEStorage from, MEStorage to, AEKey key, long amount, IActionSource source) {
        if (amount <= 0) return 0L;
        long accepted = to.insert(key, amount, Actionable.SIMULATE, source);
        if (accepted <= 0) return 0L;
        long extracted = from.extract(key, accepted, Actionable.MODULATE, source);
        if (extracted <= 0) return 0L;
        long inserted = to.insert(key, extracted, Actionable.MODULATE, source);
        if (inserted < extracted) {
            from.insert(key, extracted - inserted, Actionable.MODULATE, source);
        }
        return inserted;
    }

    public record RefillResult(
            boolean available,
            Map<AEKey, Long> moved,
            Map<AEKey, Long> returned,
            Map<AEKey, Long> networkMissing,
            Map<AEKey, Long> storageBlocked,
            Map<AEKey, Long> returnBlocked) {
        private static final RefillResult UNAVAILABLE =
                new RefillResult(false, Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

        public boolean complete() {
            return available && networkMissing.isEmpty() && storageBlocked.isEmpty() && returnBlocked.isEmpty();
        }
    }

    private TianshuSeedRefillService() { }
}
