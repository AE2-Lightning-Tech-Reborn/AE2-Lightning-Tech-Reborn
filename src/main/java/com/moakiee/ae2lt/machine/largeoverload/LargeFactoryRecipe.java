package com.moakiee.ae2lt.machine.largeoverload;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import appeng.api.stacks.AEKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Immutable execution view of a final, post-script source recipe. */
public record LargeFactoryRecipe(ResourceLocation id, LargeFactoryRecipeAccess.Process process,
        List<Requirement> inputs, Map<AEKey, Long> outputs, long energy,
        LargeFactoryLightningCost lightning, Predicate<ItemStack> catalyst) {
    public record Requirement(Predicate<AEKey> accepts, long amount) {
        public Requirement {
            if (accepts == null || amount <= 0) throw new IllegalArgumentException("Invalid requirement");
        }
    }

    public LargeFactoryRecipe {
        inputs = List.copyOf(inputs);
        outputs = Map.copyOf(outputs);
        if (inputs.isEmpty() || inputs.size() > 32 || outputs.isEmpty() || outputs.size() > 8 || energy < 0
                || outputs.values().stream().anyMatch(n -> n <= 0)) throw new IllegalArgumentException("Invalid recipe");
    }

    /** Determine the exact source multiplicity from every output, then account for every real input. */
    public long match(Map<AEKey, Long> actual, Map<AEKey, Long> declaredOutputs) {
        if (!outputs.keySet().equals(declaredOutputs.keySet()) || actual.size() > 64) return 0;
        long operations = 0;
        for (var entry : outputs.entrySet()) {
            long declared = declaredOutputs.get(entry.getKey());
            if (declared <= 0 || declared % entry.getValue() != 0) return 0;
            long multiple = declared / entry.getValue();
            if (operations != 0 && multiple != operations) return 0;
            operations = multiple;
        }
        try {
            return acceptsExactly(actual, operations) ? operations : 0;
        } catch (ArithmeticException overflow) {
            return 0;
        }
    }

    /** Capacitated bipartite flow handles overlapping tags without greedy failures or count-sized loops. */
    private boolean acceptsExactly(Map<AEKey, Long> actual, long operations) {
        var entries = new ArrayList<>(actual.entrySet());
        int source = 0, firstKey = 1, firstRequirement = firstKey + entries.size();
        int sink = firstRequirement + inputs.size(), size = sink + 1;
        long[][] residual = new long[size][size];
        long supplied = 0, required = 0;
        for (int k = 0; k < entries.size(); k++) {
            var entry = entries.get(k);
            if (entry.getValue() <= 0) return false;
            supplied = Math.addExact(supplied, entry.getValue());
            residual[source][firstKey + k] = entry.getValue();
            for (int r = 0; r < inputs.size(); r++) if (inputs.get(r).accepts().test(entry.getKey())) {
                residual[firstKey + k][firstRequirement + r] = entry.getValue();
            }
        }
        for (int r = 0; r < inputs.size(); r++) {
            long amount = Math.multiplyExact(inputs.get(r).amount(), operations);
            required = Math.addExact(required, amount);
            residual[firstRequirement + r][sink] = amount;
        }
        if (supplied != required) return false;
        long transferred = 0;
        int[] parent = new int[size];
        while (transferred < required) {
            Arrays.fill(parent, -1);
            parent[source] = source;
            var queue = new ArrayDeque<Integer>();
            queue.add(source);
            while (!queue.isEmpty() && parent[sink] == -1) {
                int from = queue.removeFirst();
                for (int to = 0; to < size; to++) if (parent[to] == -1 && residual[from][to] > 0) {
                    parent[to] = from;
                    queue.addLast(to);
                }
            }
            if (parent[sink] == -1) return false;
            long amount = required - transferred;
            for (int at = sink; at != source; at = parent[at]) amount = Math.min(amount, residual[parent[at]][at]);
            for (int at = sink; at != source; at = parent[at]) {
                residual[parent[at]][at] -= amount;
                residual[at][parent[at]] = Math.addExact(residual[at][parent[at]], amount);
            }
            transferred = Math.addExact(transferred, amount);
        }
        return true;
    }
}
