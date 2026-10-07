package com.moakiee.ae2lt.machine.largeoverload;

import java.util.LinkedHashMap;
import java.util.Map;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;

/** Seed-first passive execution. A real extracted sample initializes a binding and counts as copy one. */
public final class LargeFactoryPassive {
    private LargeFactoryPassive() { }
    public static boolean step(LargeFactoryHatchBlockEntity hatch) {
        if (!hatch.passive() || !hatch.ready() || hatch.processing()) return false;
        var account = hatch.account();
        if (account == null || !account.resources.isEmpty()) return false;
        var entry = hatch.nextPassiveEntry();
        if (entry == null || !hatch.enabled(entry)) return false;
        var grid = hatch.getMainNode().getGrid();
        var controller = hatch.controller();
        var origin = hatch.origin(grid);
        if (origin == null) return false;
        var storage = grid.getStorageService().getInventory();
        var sample = new LinkedHashMap<AEKey, Long>();
        try {
            // Plan a concrete one-copy sample without treating simulations as owned inputs.
            for (var input : entry.pattern.getInputs()) {
                boolean found = false;
                int alternatives = 0;
                for (var possible : input.getPossibleInputs()) {
                    if (++alternatives > 64) break;
                    if (possible == null || possible.amount() <= 0 || input.getRemainingKey(possible.what()) != null) continue;
                    long amount = Math.multiplyExact(possible.amount(), input.getMultiplier());
                    long total = Math.addExact(sample.getOrDefault(possible.what(), 0L), amount);
                    if (hatch.extract(grid, possible.what(), total, Actionable.SIMULATE) < total) continue;
                    LargeFactoryAmounts.add(sample, possible.what(), amount);
                    found = true;
                    break;
                }
                if (!found) { hatch.status("missing_inputs"); return false; }
            }
            if (sample.isEmpty()) return false;
            account.origin = origin;
            hatch.ledgerChanged();
            hatch.processing(true);
            try {
                if (!extract(hatch, sample, grid)) return false;
            } finally { hatch.processing(false); }
            // Only now is matching/cache initialization allowed for this passive sample.
            if (hatch.controller() != controller || hatch.getMainNode().getGrid() != grid || hatch.bindRecipe(entry, sample) == null) return false;
            long fairShare = Math.max(1, controller.budget().remainingOperations(hatch.getLevel().getGameTime())
                    / Math.max(1, controller.hatches().size()) / entry.operations());
            var quote = LargeFactoryExecutor.quote(hatch, entry, sample, fairShare);
            if (quote == null) return false;
            long copies = quote.copies();
            for (var input : sample.entrySet()) {
                long wanted = Math.multiplyExact(input.getValue(), copies - 1);
                long available = hatch.extract(grid, input.getKey(), wanted, Actionable.SIMULATE);
                copies = Math.min(copies, 1 + available / input.getValue());
            }
            if (copies > 1) {
                hatch.processing(true);
                try { if (!extract(hatch, LargeFactoryAmounts.scale(sample, copies - 1), grid)) return false; }
                finally { hatch.processing(false); }
            }
            long accepted = LargeFactoryExecutor.execute(hatch, entry, sample, copies, null, true);
            return accepted > 0;
        } catch (ArithmeticException overflow) {
            hatch.status("cost_overflow");
            return false;
        } finally {
            // Rejected samples/partial extractions stay owned until actual reinsertion succeeds.
            hatch.flushRetained();
        }
    }
    private static boolean extract(LargeFactoryHatchBlockEntity hatch, Map<AEKey, Long> requested, appeng.api.networking.IGrid grid) {
        for (var entry : requested.entrySet()) {
            if (!hatch.ready() || hatch.getMainNode().getGrid() != grid) return false;
            long actual = hatch.extract(grid, entry.getKey(), entry.getValue(), Actionable.MODULATE);
            if (actual < 0 || actual > entry.getValue()) throw new IllegalStateException("Invalid passive input receipt");
            if (actual > 0) { LargeFactoryAmounts.add(hatch.account().resources, entry.getKey(), actual); hatch.ledgerChanged(); }
            if (actual != entry.getValue()) return false;
        }
        return true;
    }
}
