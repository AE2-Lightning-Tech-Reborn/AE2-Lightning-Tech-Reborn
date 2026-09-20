package com.moakiee.ae2lt.crafting.big;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;

import com.moakiee.ae2lt.blockentity.*;
import com.moakiee.thunderbolt.core.crafting.planner.*;
import com.moakiee.thunderbolt.core.storage.big.BigStorageOps;

import net.minecraft.world.level.Level;

import java.math.BigInteger;
import java.util.*;

/**
 * Target-rooted capture from connected multidimensional matrices, using real single-copy assembly.
 */
public record BigGraphExport(
        CraftGraph<AEKey> graph, Map<AEKey, BigInteger> stock, int recipeCount) {
    public static List<MatrixControllerBlockEntity> matrices(IGrid grid) {
        var result = new ArrayList<MatrixControllerBlockEntity>();
        for (var port : grid.getActiveMachines(MatrixPortBlockEntity.class)) {
            var matrix = port.getController();
            if (port.getGrid() == grid
                    && matrix != null
                    && matrix.isBigCraftingAvailable()
                    && !result.contains(matrix)) result.add(matrix);
        }
        result.sort(Comparator.comparingLong(m -> m.getBlockPos().asLong()));
        return result;
    }

    public static BigGraphExport capture(
            IGrid grid, Level level, AEKey target, IActionSource source) {
        var stock = BigStorageOps.snapshot(grid.getStorageService().getInventory(), source);
        var builder = CraftGraph.<AEKey>builder();
        stock.forEach(
                (k, n) -> {
                    if (!k.equals(target)) builder.stockExact(k, n);
                });
        var byOutput = new LinkedHashMap<AEKey, List<IPatternDetails>>();
        var definitions = new HashSet<AEItemKey>();
        for (var matrix : matrices(grid))
            for (var pattern : matrix.getAvailablePatterns()) {
                PlanningCancellation.check();
                if (definitions.add(pattern.getDefinition()))
                    byOutput.computeIfAbsent(
                                    pattern.getPrimaryOutput().what(), k -> new ArrayList<>())
                            .add(pattern);
            }
        var candidates = new LinkedHashSet<AEKey>(stock.keySet());
        candidates.addAll(byOutput.keySet());
        var variants = new HashMap<AEKey, List<AEKey>>();
        for (var candidate : candidates)
            variants.computeIfAbsent(candidate.dropSecondary(), k -> new ArrayList<>())
                    .add(candidate);
        var seen = new HashSet<AEKey>();
        var queue = new ArrayDeque<AEKey>();
        seen.add(target);
        queue.add(target);
        int recipes = 0;
        while (!queue.isEmpty()) {
            PlanningCancellation.check();
            var key = queue.removeFirst();
            for (var details : byOutput.getOrDefault(key, List.of())) {
                var choices = new ArrayList<List<Map<AEKey, Long>>>();
                boolean valid = true;
                for (var input : details.getInputs()) {
                    var alternatives = new LinkedHashMap<AEKey, Long>();
                    for (var template : input.getPossibleInputs()) {
                        if (input.isValid(template.what(), level))
                            alternatives.putIfAbsent(template.what(), template.amount());
                        for (var candidate :
                                variants.getOrDefault(template.what().dropSecondary(), List.of()))
                            if (input.isValid(candidate, level))
                                alternatives.putIfAbsent(candidate, template.amount());
                    }
                    var keys = new ArrayList<>(alternatives.keySet());
                    keys.sort(
                            (a, b) ->
                                    stock.getOrDefault(b, BigInteger.ZERO)
                                            .compareTo(stock.getOrDefault(a, BigInteger.ZERO)));
                    if (keys.size() > 32) keys.subList(32, keys.size()).clear();
                    var slot = new ArrayList<Map<AEKey, Long>>();
                    for (var option : keys) {
                        if (slot.size() == 64) break;
                        slot.add(
                                Map.of(
                                        option,
                                        Math.multiplyExact(
                                                alternatives.get(option),
                                                Math.max(1, input.getMultiplier()))));
                    }
                    // Molecular recipes have small multipliers; include mixed substitutions with
                    // bounded work.
                    if (keys.size() > 1 && input.getMultiplier() > 1 && slot.size() < 64)
                        mixed(
                                keys,
                                alternatives,
                                0,
                                Math.max(1, input.getMultiplier()),
                                new LinkedHashMap<>(),
                                slot);
                    if (slot.isEmpty()) {
                        valid = false;
                        break;
                    }
                    choices.add(slot);
                }
                if (!valid) continue;
                var combinations = new ArrayList<List<Map<AEKey, Long>>>();
                combine(choices, 0, new ArrayList<>(), combinations);
                for (var slots : combinations) {
                    PlanningCancellation.check();
                    var recipe = BigMatrixRecipe.capture(details, slots, level);
                    if (recipe == null || !recipe.pattern().output().equals(key)) continue;
                    builder.pattern(recipe.pattern());
                    if (++recipes > 16384)
                        throw new IllegalArgumentException("Too many exact recipe alternatives");
                    for (var input : recipe.pattern().inputs())
                        if (seen.add(input.key())) queue.add(input.key());
                }
            }
        }
        return new BigGraphExport(builder.build(), stock, recipes);
    }

    private static void combine(
            List<List<Map<AEKey, Long>>> choices,
            int i,
            List<Map<AEKey, Long>> selected,
            List<List<Map<AEKey, Long>>> out) {
        PlanningCancellation.check();
        if (out.size() >= 64) return;
        if (i == choices.size()) {
            out.add(List.copyOf(selected));
            return;
        }
        for (var choice : choices.get(i)) {
            selected.add(choice);
            combine(choices, i + 1, selected, out);
            selected.remove(selected.size() - 1);
            if (out.size() >= 64) break;
        }
    }

    private static void mixed(
            List<AEKey> keys,
            Map<AEKey, Long> amounts,
            int i,
            long remaining,
            Map<AEKey, Long> selected,
            List<Map<AEKey, Long>> out) {
        PlanningCancellation.check();
        if (out.size() >= 64) return;
        if (i == keys.size() - 1) {
            if (remaining > 0)
                selected.put(keys.get(i), Math.multiplyExact(amounts.get(keys.get(i)), remaining));
            if (selected.size() > 1) out.add(Map.copyOf(selected));
            selected.remove(keys.get(i));
            return;
        }
        for (long n = remaining; n >= 0 && out.size() < 64; n--) {
            if (n > 0) selected.put(keys.get(i), Math.multiplyExact(amounts.get(keys.get(i)), n));
            else selected.remove(keys.get(i));
            mixed(keys, amounts, i + 1, remaining - n, selected, out);
            if (n == 0) break;
        }
        selected.remove(keys.get(i));
    }
}
