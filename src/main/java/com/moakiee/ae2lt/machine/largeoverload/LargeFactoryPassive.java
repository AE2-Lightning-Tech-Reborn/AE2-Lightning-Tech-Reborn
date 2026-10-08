package com.moakiee.ae2lt.machine.largeoverload;

import java.util.LinkedHashMap;
import java.util.Map;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;

/** Seed-first passive execution. A real extracted sample initializes a binding and counts as copy one. */
public final class LargeFactoryPassive {
    private LargeFactoryPassive() { }
    public static boolean step(LargeFactoryHatchBlockEntity hatch) {
        if (!hatch.passive() || hatch.processing()) return false;
        var entry = hatch.nextPassiveEntry();
        if (entry == null || !hatch.enabled(entry)) return false;
        if (hatch.cachedMissing(entry)) { hatch.status("missing_inputs"); return false; }
        var account = hatch.account();
        if (account == null || !account.resources.isEmpty()) return false;
        var controller = hatch.controller();
        if (controller == null) return false;
        long remaining = controller.budget().remainingOperations(hatch.getLevel().getGameTime());
        if (remaining == 0 || hatch.minimumOperations(entry) > remaining) return false;
        if (!hatch.ready()) return false;
        var grid = hatch.getMainNode().getGrid();
        var origin = hatch.origin(grid);
        if (origin == null) return false;
        try {
            // Plan a concrete one-copy sample without treating simulations as owned inputs.
            Map<AEKey, Long> sample = entry.exactInputs;
            if (sample != null) {
                for (var input : sample.entrySet()) if (hatch.extract(grid, input.getKey(), input.getValue(), Actionable.SIMULATE) < input.getValue()) {
                    hatch.status("missing_inputs"); return false;
                }
            } else {
                sample = new LinkedHashMap<>();
                for (var options : entry.inputOptions) {
                    boolean found = false;
                    for (var possible : options) {
                        long total = Math.addExact(sample.getOrDefault(possible.what(), 0L), possible.amount());
                        if (hatch.extract(grid, possible.what(), total, Actionable.SIMULATE) < total) continue;
                        LargeFactoryAmounts.add(sample, possible.what(), possible.amount()); found = true; break;
                    }
                    if (!found) { hatch.status("missing_inputs"); return false; }
                }
            }
            if (sample.isEmpty()) return false;
            account.origin = origin;
            long ownedCopies = 0;
            if (!entry.hasBoundSignature(sample)) {
                hatch.processing(true);
                try { if (!extract(hatch, sample, grid)) return false; }
                finally { hatch.processing(false); }
                // Only a first real receipt may initialize a new binding/signature.
                if (hatch.controller() != controller || hatch.getMainNode().getGrid() != grid || hatch.bindRecipe(entry, sample) == null) return false;
                ownedCopies = 1;
            }
            long fairShare = Math.max(1, controller.budget().remainingOperations(hatch.getLevel().getGameTime())
                    / controller.passiveHatchCount() / entry.operations());
            var quote = LargeFactoryExecutor.quote(hatch, entry, sample, fairShare);
            if (quote == null) return false;
            long copies = quote.copies();
            for (var input : sample.entrySet()) {
                long wanted = Math.multiplyExact(input.getValue(), copies - ownedCopies);
                long available = hatch.extract(grid, input.getKey(), wanted, Actionable.SIMULATE);
                copies = Math.min(copies, ownedCopies + available / input.getValue());
            }
            if (copies <= 0) return false;
            if (copies > ownedCopies) {
                hatch.processing(true);
                try { if (!extract(hatch, LargeFactoryAmounts.scale(sample, copies - ownedCopies), grid)) return false; }
                finally { hatch.processing(false); }
            }
            long accepted = LargeFactoryExecutor.executePrepared(hatch, entry, sample, quote.limitCopies(copies));
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
