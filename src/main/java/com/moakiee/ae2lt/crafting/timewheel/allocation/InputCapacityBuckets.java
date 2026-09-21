package com.moakiee.ae2lt.crafting.timewheel.allocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import appeng.api.stacks.AEKey;

/** Job-local compression of ACTUAL accepted-key edges, never subsets of possible consumers.
 * A representative is an internal bucket identifier; physical extraction always uses the original
 * full key. Amounts are part of the signature, including exact consumers and their unit sizes. */
final class InputCapacityBuckets {
    private record Acceptance(int row, long unit) { }
    final Map<AEKey, AEKey> bucketOf = new HashMap<>();
    final Map<AEKey, Long> unitOf = new HashMap<>();
    final List<Map<AEKey, Long>> options = new ArrayList<>();
    final Map<AEKey, Long> capacity = new LinkedHashMap<>();
    final Map<AEKey, Long> physicalStock;
    final Map<AEKey, PositiveKeyIndex> liveKeys = new HashMap<>();
    final long edges;

    InputCapacityBuckets(List<? extends Map<AEKey, Long>> rows, Map<AEKey, Long> stock) {
        physicalStock = new LinkedHashMap<>(stock);
        var users = new LinkedHashMap<AEKey, List<Acceptance>>();
        long edgeCount = 0;
        for (int row = 0; row < rows.size(); row++) {
            for (var entry : rows.get(row).entrySet()) {
                users.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>())
                        .add(new Acceptance(row, entry.getValue()));
                edgeCount++;
            }
        }
        edges = edgeCount;
        var signatures = new HashMap<List<Acceptance>, AEKey>();
        for (var entry : users.entrySet()) {
            var key = entry.getKey();
            var signature = List.copyOf(entry.getValue());
            var representative = signatures.computeIfAbsent(signature, ignored -> key);
            bucketOf.put(key, representative);
            // Multi-key buckets must not join fragments smaller than one template unit.
            // Differing row units can only occur at a singleton exact key in the supported model.
            long unit = signature.get(0).unit;
            for (var acceptance : signature) if (acceptance.unit != unit) { unit = 1; break; }
            unitOf.put(key, unit);
            long amount = Math.max(0, stock.getOrDefault(key, 0L));
            capacity.merge(representative, amount - amount % unit, Math::addExact);
            liveKeys.computeIfAbsent(representative, ignored -> new PositiveKeyIndex()).set(key, amount >= unit);
        }
        for (var row : rows) {
            var compressed = new LinkedHashMap<AEKey, Long>();
            row.forEach((key, amount) -> compressed.putIfAbsent(bucketOf.get(key), amount));
            options.add(compressed);
        }
    }

    long usable(AEKey key, long amount) {
        long unit = unitOf.get(key);
        return amount - amount % unit;
    }

    Map<AEKey, Long> compress(Map<AEKey, Long> amounts) {
        var result = new LinkedHashMap<AEKey, Long>();
        amounts.forEach((key, amount) -> result.merge(bucketOf.get(key), amount, Math::addExact));
        return result;
    }
}
