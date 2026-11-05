package com.moakiee.ae2lt.crafting.timewheel;

import appeng.api.stacks.AEKey;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A bounded, optimistic stock proof for input slots sharing alternatives. Every physical
 * allocation maps to a flow when each key is charged its cheapest declared unit across all
 * slots. Failure proves a shortage; success still requires the native input allocator.
 */
final class SharedInputAvailability {
    static final int MAX_INPUTS = 32;
    private static final int MAX_KEYS = 128;

    private SharedInputAvailability() { }

    record Demand(long count, Map<AEKey, Long> units) { }

    static boolean hasShortage(List<Demand> inputs, Map<AEKey, Long> stock) {
        if (inputs.size() < 2 || inputs.size() > MAX_INPUTS) return false;
        var cheapestUnits = new HashMap<AEKey, Long>();
        boolean alternatives = false;
        boolean shared = false;
        for (var input : inputs) {
            alternatives |= input.units().size() > 1;
            for (var entry : input.units().entrySet()) {
                shared |= cheapestUnits.containsKey(entry.getKey());
                cheapestUnits.merge(entry.getKey(), entry.getValue(), Math::min);
            }
            // Custom patterns may advertise arbitrarily many candidates. An inconclusive
            // preflight must leave the normal allocation path available at bounded cost.
            if (cheapestUnits.size() > MAX_KEYS) return false;
        }
        if (!alternatives || !shared) return false;

        int firstInput = cheapestUnits.size() + 1;
        int sink = firstInput + inputs.size();
        var residual = new long[sink + 1][sink + 1];
        var keyNodes = new HashMap<AEKey, Integer>();
        int nextKey = 1;
        for (var entry : cheapestUnits.entrySet()) {
            int node = nextKey++;
            keyNodes.put(entry.getKey(), node);
            residual[0][node] = stock.getOrDefault(entry.getKey(), 0L) / entry.getValue();
        }
        for (int i = 0; i < inputs.size(); i++) {
            var input = inputs.get(i);
            int node = firstInput + i;
            residual[node][sink] = input.count();
            for (var key : input.units().keySet()) residual[keyNodes.get(key)][node] = input.count();
        }

        var parent = new int[sink + 1];
        var queue = new int[sink + 1];
        while (findPath(residual, sink, parent, queue)) {
            long sent = Long.MAX_VALUE;
            for (int node = sink; node != 0; node = parent[node])
                sent = Math.min(sent, residual[parent[node]][node]);
            for (int node = sink; node != 0; node = parent[node]) {
                int previous = parent[node];
                residual[previous][node] -= sent;
                residual[node][previous] += sent;
            }
        }
        // Never sum flow or demand: several individually valid long quantities can total
        // more than Long.MAX_VALUE. Each residual edge stays within its own long bound.
        for (int node = firstInput; node < sink; node++) {
            if (residual[node][sink] > 0) return true;
        }
        return false;
    }

    private static boolean findPath(long[][] residual, int sink, int[] parent, int[] queue) {
        Arrays.fill(parent, -1);
        parent[0] = 0;
        queue[0] = 0;
        int head = 0, tail = 1;
        while (head < tail) {
            int node = queue[head++];
            for (int next = 1; next <= sink; next++) {
                if (parent[next] != -1 || residual[node][next] <= 0) continue;
                parent[next] = node;
                if (next == sink) return true;
                queue[tail++] = next;
            }
        }
        return false;
    }
}
