package com.moakiee.ae2lt.crafting.timewheel;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AEProcessingPattern;
import com.moakiee.ae2lt.overload.runtime.pattern.Ae2OverloadPatternDetails;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Necessary physical-stock checks, never a substitute for native input matching/allocation.
 * Unknown validity predicates are treated as accepting every same-primary variant: a world-state
 * change cannot fix a shortage even under that optimistic assumption. Unknown candidate lists are
 * refreshed by the time wheel; known immutable native lists only need inventory notifications.
 */
final class CraftingMaterialReadiness {
    private final ListCraftingInventory inventory;
    // Shared by this CPU's waiting tasks. Only primary item/fluid families are cached, never
    // per-pattern component matches. A new component variant invalidates the same family.
    private final HashMap<AEKey, Long> familyStock = new HashMap<>();

    CraftingMaterialReadiness(ListCraftingInventory inventory) {
        this.inventory = inventory;
    }

    void onInventoryChange(@Nullable AEKey key) {
        if (key == null) clear();
        else familyStock.remove(key.dropSecondary());
    }

    void clear() {
        familyStock.clear();
    }

    static boolean stableInputs(IPatternDetails details) {
        if (details.getClass() == AEProcessingPattern.class || details.getClass() == AECraftingPattern.class) {
            return true;
        }
        // Overload captures discovery anchors, but delegates multipliers to its source input.
        return details instanceof Ae2OverloadPatternDetails overload
                && stableInputs(overload.wrappedPatternDetails());
    }

    Set<AEKey> missing(IPatternDetails details) {
        boolean exact = details.getClass() == AEProcessingPattern.class
                || details.getClass() == AECraftingPattern.class
                && !((AECraftingPattern) details).canSubstitute()
                && !((AECraftingPattern) details).canSubstituteFluids();
        var missing = new HashSet<AEKey>();
        var fixedDemand = new HashMap<AEKey, Long>();
        var stock = new HashMap<AEKey, Long>();
        for (var input : details.getInputs()) {
            long multiplier = input.getMultiplier();
            if (multiplier <= 0) continue;
            var candidates = input.getPossibleInputs();
            if (candidates.length == 0) continue; // Cannot wait for an unindexable dependency.
            var units = new HashMap<AEKey, Long>();
            boolean unknown = false;
            for (var candidate : candidates) {
                if (candidate == null || candidate.what() == null || candidate.amount() <= 0) {
                    unknown = true;
                    break;
                }
                var key = exact ? candidate.what() : candidate.what().dropSecondary();
                // Same-family anchors may repeat or use different units. The smallest unit gives
                // an upper bound on craftable copies; duplicate anchors must not duplicate stock.
                units.merge(key, candidate.amount(), Math::min);
            }
            if (unknown) continue;
            long possibleUnits = 0;
            for (var entry : units.entrySet()) {
                long held = stock.computeIfAbsent(entry.getKey(), key -> exact
                        ? Math.max(0, inventory.list.get(key))
                        : familyStock.computeIfAbsent(key, this::countFamilyStock));
                possibleUnits = add(possibleUnits, held / entry.getValue());
            }
            if (possibleUnits < multiplier) missing.addAll(units.keySet());
            // Also catch several fixed slots competing for the same physical stock.
            if (units.size() == 1) {
                var entry = units.entrySet().iterator().next();
                fixedDemand.merge(entry.getKey(), multiply(multiplier, entry.getValue()),
                        CraftingMaterialReadiness::add);
            }
        }
        fixedDemand.forEach((key, amount) -> {
            if (stock.getOrDefault(key, 0L) < amount) missing.add(key);
        });
        return missing;
    }

    private long countFamilyStock(AEKey anchor) {
        long total = 0;
        for (var candidate : inventory.findFuzzyTemplates(anchor)) {
            total = add(total, Math.max(0, inventory.list.get(candidate)));
        }
        return total;
    }

    private static long add(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static long multiply(long left, long right) {
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }
}
