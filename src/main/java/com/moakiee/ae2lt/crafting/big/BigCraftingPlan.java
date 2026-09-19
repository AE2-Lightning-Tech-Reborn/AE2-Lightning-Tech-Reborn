package com.moakiee.ae2lt.crafting.big;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.*;
import appeng.menu.me.crafting.*;

import com.moakiee.thunderbolt.ae2.crafting.*;
import com.moakiee.thunderbolt.core.crafting.big.BigCraftingPlanner;
import com.moakiee.thunderbolt.core.storage.big.BigAmounts;

import java.math.BigInteger;
import java.util.*;

/** Native menu/CPU adapter. Projected long values are display-only, never execution inputs. */
public record BigCraftingPlan(
        AEKey target, BigInteger amount, BigCraftingPlanner.Result<AEKey> exact, String failure)
        implements ICraftingPlan {
    public BigCraftingPlan {
        BigAmounts.nonNegative(amount);
    }

    public GenericStack finalOutput() {
        return new GenericStack(target, BigAmounts.project(amount));
    }

    public long bytes() {
        return 0;
    } // Multidimensional CPUs have no byte reservation.

    public boolean simulation() {
        return exact == null || !exact.executable();
    }

    public boolean multiplePaths() {
        return true;
    }

    public KeyCounter emittedItems() {
        return new KeyCounter();
    }

    public Map<IPatternDetails, Long> patternTimes() {
        return Map.of();
    }

    public KeyCounter usedItems() {
        var result = new KeyCounter();
        if (exact != null && exact.program() != null)
            exact.program()
                    .required()
                    .forEach(
                            (k, n) ->
                                    result.add(
                                            k,
                                            BigAmounts.project(
                                                    n.subtract(
                                                                    exact.missing()
                                                                            .getOrDefault(
                                                                                    k,
                                                                                    BigInteger
                                                                                            .ZERO))
                                                            .max(BigInteger.ZERO))));
        return result;
    }

    public KeyCounter missingItems() {
        var result = new KeyCounter();
        if (exact != null) exact.missing().forEach((k, n) -> result.add(k, BigAmounts.project(n)));
        return result;
    }

    public CraftingPlanSummary summary() {
        var stored = new LinkedHashMap<AEKey, BigInteger>();
        var crafted = new LinkedHashMap<AEKey, BigInteger>();
        var missing = exact == null ? Map.<AEKey, BigInteger>of() : exact.missing();
        if (exact != null && exact.program() != null) {
            exact.program()
                    .required()
                    .forEach(
                            (k, n) ->
                                    stored.put(
                                            k,
                                            n.subtract(missing.getOrDefault(k, BigInteger.ZERO))
                                                    .max(BigInteger.ZERO)));
            for (var block : exact.program().blocks())
                for (var step : block.steps()) {
                    var recipe = (BigMatrixRecipe) step.recipe().source();
                    var copies = step.copies().multiply(block.repetitions());
                    crafted.merge(
                            recipe.pattern().output(),
                            recipe.pattern().exactOutputAmount().multiply(copies),
                            BigInteger::add);
                    recipe.pattern()
                            .byproducts()
                            .forEach(
                                    o ->
                                            crafted.merge(
                                                    o.key(),
                                                    o.exactAmount().multiply(copies),
                                                    BigInteger::add));
                }
        }
        var keys = new LinkedHashSet<AEKey>();
        keys.addAll(stored.keySet());
        keys.addAll(missing.keySet());
        keys.addAll(crafted.keySet());
        var entries = new ArrayList<CraftingPlanSummaryEntry>();
        var precise = new LinkedHashMap<AEKey, ExactPlanReport.Amounts>();
        for (var key : keys) {
            var s = stored.getOrDefault(key, BigInteger.ZERO);
            var m = missing.getOrDefault(key, BigInteger.ZERO);
            var c = crafted.getOrDefault(key, BigInteger.ZERO);
            entries.add(
                    new CraftingPlanSummaryEntry(
                            key,
                            BigAmounts.project(m),
                            BigAmounts.project(s),
                            BigAmounts.project(c)));
            precise.put(key, new ExactPlanReport.Amounts(s, m, c));
        }
        entries.sort(
                Comparator.comparing(
                                (CraftingPlanSummaryEntry e) -> precise.get(e.getWhat()).missing())
                        .reversed());
        var summary = new CraftingPlanSummary(0, simulation(), List.copyOf(entries));
        ExactPlanReports.attach(
                summary,
                new ExactPlanReport(BigInteger.ZERO, precise, exact == null || !exact.verified()));
        return summary;
    }
}
