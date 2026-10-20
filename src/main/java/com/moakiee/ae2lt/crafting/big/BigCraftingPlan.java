package com.moakiee.ae2lt.crafting.big;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.*;
import appeng.menu.me.crafting.*;

import com.moakiee.thunderbolt.ae2.crafting.*;
import com.moakiee.thunderbolt.core.crafting.big.BigCraftingPlanner;
import com.moakiee.thunderbolt.core.crafting.planner.CraftPattern;
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
        var firings = new IdentityHashMap<CraftPattern<AEKey>, BigInteger>();
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
                    var recipe = (BigCraftingRecipe) step.recipe().source();
                    var copies = step.copies().multiply(block.repetitions());
                    firings.merge(recipe.pattern(), copies, BigInteger::add);
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
        var reportBytes = reportBytes(firings);
        var summary = new CraftingPlanSummary(
                BigAmounts.project(reportBytes), simulation(), List.copyOf(entries));
        ExactPlanReports.attach(
                summary,
                new ExactPlanReport(reportBytes, precise, exact == null || !exact.verified()));
        return summary;
    }

    /** Logical plan size for the report, independent of the CPU's zero byte reservation. */
    private BigInteger reportBytes(Map<CraftPattern<AEKey>, BigInteger> firings) {
        if (exact == null || exact.program() == null) return BigInteger.ZERO;

        var demand = new LinkedHashMap<AEKey, BigInteger>();
        var seeds = new LinkedHashMap<AEKey, BigInteger>();
        demand.put(target, amount);
        BigInteger executionBytes = BigInteger.ZERO;
        for (var entry : firings.entrySet()) {
            var copies = entry.getValue();
            executionBytes = executionBytes.add(copies);
            for (var input : entry.getKey().inputs()) {
                if (input.returned() && input.uses() == Long.MAX_VALUE) {
                    // An unchanged catalyst is acquired once and reused across recipe firings.
                    seeds.merge(input.key(), input.exactAmount(), BigInteger::max);
                } else {
                    demand.merge(input.key(), input.unitsForExact(copies), BigInteger::add);
                }
                if (input.returned() || input.remainder() != null) {
                    executionBytes = executionBytes.add(input.exactAmount().multiply(copies));
                }
            }
        }
        seeds.forEach((key, count) -> demand.merge(key, count, BigInteger::add));

        // Sum resource-specific byte fractions exactly and round up only the final total.
        BigInteger numerator = BigInteger.ZERO;
        BigInteger denominator = BigInteger.ONE;
        BigInteger eight = BigInteger.valueOf(8);
        for (var entry : demand.entrySet()) {
            BigInteger divisor = BigInteger.valueOf(
                    Math.max(1, entry.getKey().getType().getAmountPerByte()));
            BigInteger gcd = denominator.gcd(divisor);
            BigInteger factor = divisor.divide(gcd);
            numerator = numerator.multiply(factor).add(
                    entry.getValue().multiply(eight).multiply(denominator.divide(gcd)));
            denominator = denominator.multiply(factor);
        }
        return numerator.add(denominator).subtract(BigInteger.ONE).divide(denominator)
                .add(eight.multiply(BigInteger.valueOf(demand.size())))
                .add(executionBytes);
    }
}
