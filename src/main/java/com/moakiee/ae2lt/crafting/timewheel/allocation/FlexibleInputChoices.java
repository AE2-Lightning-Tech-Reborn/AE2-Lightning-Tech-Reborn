package com.moakiee.ae2lt.crafting.timewheel.allocation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import com.moakiee.ae2lt.crafting.timewheel.allocation.CraftingInputAllocation.Choices;

/**
 * Adjusts a feasible material assignment through residual paths. Consuming a candidate is allowed
 * exactly when the remaining finite demands can still be assigned, within the search budget.
 * Every augmentation transfers a capacity, never individual items. A budget miss retains the known
 * feasible allowance; it cannot release another demand's material without a proof.
 */
final class FlexibleInputChoices implements Choices {
    record Demand(int selectedSlot, Map<AEKey, Long> options, Map<AEKey, Long> assigned) { }

    /** One component's feasible witness. Topology is built once; quantities change only between
     * extraction transactions, on the CPU thread. Exact consumers contribute fixed reserves. */
    static final class Graph {
        final List<Map<AEKey, Long>> options = new ArrayList<>();
        final Map<AEKey, List<Integer>> users = new HashMap<>();
        // Insertion-ordered positive edges only. No historical zero entries to scan.
        final List<LinkedHashMap<AEKey, Long>> assigned = new ArrayList<>();
        final Map<AEKey, Long> stock;
        final Map<AEKey, Long> totals = new HashMap<>();
        final java.util.Set<AEKey> deficits = new java.util.HashSet<>();
        final long[] required;
        final InputCapacityBuckets buckets;
        final Map<Integer, Map<AEKey, Long>> recentlyConsumed = new HashMap<>();
        long revision;
        long trimVisits;

        Graph(List<Demand> demands, Map<AEKey, Long> stock) {
            this.buckets = new InputCapacityBuckets(demands.stream().map(Demand::options).toList(), stock);
            this.stock = new LinkedHashMap<>(buckets.capacity);
            this.required = new long[demands.size()];
            for (var demand : demands) {
                int index = options.size();
                options.add(Map.copyOf(buckets.options.get(index)));
                var positive = new LinkedHashMap<>(buckets.compress(demand.assigned));
                positive.values().removeIf(amount -> amount <= 0);
                assigned.add(positive);
                assigned.get(index).forEach((key, amount) -> {
                    totals.merge(key, amount, Math::addExact);
                    required[index] = Math.addExact(required[index], amount);
                });
                if (options.get(index).size() > 1) {
                    for (var key : options.get(index).keySet()) {
                        users.computeIfAbsent(key, ignored -> new ArrayList<>()).add(index);
                    }
                }
            }
        }

        Supplier<Choices> factory(Map<Integer, Integer> selected, long workLimit) {
            var slots = Map.copyOf(selected);
            return () -> new FlexibleInputChoices(this, slots, new Budget(workLimit));
        }

        void setStock(AEKey key, long amount) {
            var bucket = buckets.bucketOf.get(key);
            if (bucket == null) return;
            long old = buckets.physicalStock.getOrDefault(key, 0L);
            if (old == amount) return;
            revision++;
            buckets.physicalStock.put(key, amount);
            buckets.liveKeys.get(bucket).set(key, buckets.usable(key, amount) > 0);
            long updated = Math.addExact(stock.get(bucket), buckets.usable(key, amount) - buckets.usable(key, old));
            stock.put(bucket, updated);
            checkDeficit(bucket);
        }

        private void checkDeficit(AEKey key) {
            if (totals.getOrDefault(key, 0L) > stock.getOrDefault(key, 0L)) deficits.add(key);
            else deficits.remove(key);
        }

        /** Ordinary accepted copies only shrink their own row. Prefer keys whose physical stock
         * fell; if the witness cannot accommodate a substitution, the caller rebuilds it. */
        boolean trim(int row, long remaining) {
            if (remaining < 0 || remaining > required[row]) return false;
            long excess = required[row] - remaining;
            if (excess == 0) return true;
            revision++;
            long quantum = options.get(row).values().iterator().next();
            // Normally all deficits are among the last extraction's concrete buckets. This also
            // handles partial rejection: only remove the deficit actually left after reinjection.
            var recent = recentlyConsumed.remove(row);
            if (recent != null) {
                for (var key : recent.keySet()) {
                    if (excess == 0) break;
                    excess -= trimDeficit(row, key, excess, quantum);
                }
            }
            // Compatibility path for callers which do not publish extraction proofs.
            if (excess > 0 && !deficits.isEmpty()) {
                var keys = assigned.get(row).size() < deficits.size()
                        ? List.copyOf(assigned.get(row).keySet()) : List.copyOf(deficits);
                for (var key : keys) {
                    if (excess == 0) break;
                    excess -= trimDeficit(row, key, excess, quantum);
                }
            }
            while (excess > 0 && !assigned.get(row).isEmpty()) {
                var entry = assigned.get(row).firstEntry();
                trimVisits++;
                long remove = Math.min(excess, entry.getValue());
                remove(row, entry.getKey(), remove);
                excess -= remove;
            }
            required[row] = remaining;
            return excess == 0;
        }

        private long trimDeficit(int row, AEKey key, long excess, long quantum) {
            trimVisits++;
            long deficit = Math.max(0, totals.getOrDefault(key, 0L) - stock.getOrDefault(key, 0L));
            long units = deficit == 0 ? 0 : 1 + (deficit - 1) / quantum;
            long rounded = units > Long.MAX_VALUE / quantum ? Long.MAX_VALUE : units * quantum;
            long remove = Math.min(Math.min(excess, rounded), assigned.get(row).getOrDefault(key, 0L));
            if (remove > 0) remove(row, key, remove);
            return remove;
        }

        private void remove(int row, AEKey key, long amount) {
            long left = assigned.get(row).get(key) - amount;
            if (left == 0) assigned.get(row).remove(key);
            else assigned.get(row).put(key, left);
            totals.merge(key, -amount, Long::sum);
            checkDeficit(key);
        }
    }

    private final Graph graph;
    private final Map<Integer, Integer> selected;
    // Sparse transaction overlays: opening a transaction never clones the component.
    private final Map<Integer, Map<AEKey, Long>> assigned = new HashMap<>();
    private final Map<Integer, Map<AEKey, Long>> consumed = new HashMap<>();
    private final Map<AEKey, Long> stock = new HashMap<>();
    private final Map<AEKey, Long> totals = new HashMap<>();
    private final Map<AEKey, Long> physicalStock = new HashMap<>();
    private final List<Runnable> undo = new ArrayList<>();
    private int checkpoints;
    private final Budget budget;
    private final long baseRevision;
    private boolean published;
    private final Map<AEKey, PositiveKeyIndex.Link> keyCursors = new HashMap<>();
    private long candidateVisits;

    static Supplier<Choices> factory(List<Demand> demands, Map<AEKey, Long> stock, long workLimit) {
        var selected = new HashMap<Integer, Integer>();
        for (int i = 0; i < demands.size(); i++) {
            if (demands.get(i).selectedSlot >= 0) selected.put(demands.get(i).selectedSlot, i);
        }
        return new Graph(demands, stock).factory(selected, workLimit);
    }

    static Supplier<Choices> combine(List<Supplier<Choices>> factories) {
        if (factories.isEmpty()) return null;
        if (factories.size() == 1) return factories.getFirst();
        var immutable = List.copyOf(factories);
        return () -> new Combined(immutable.stream().map(Supplier::get).toList());
    }

    private FlexibleInputChoices(Graph graph, Map<Integer, Integer> selected, Budget budget) {
        this(graph, selected, budget, graph.revision);
    }

    private FlexibleInputChoices(Graph graph, Map<Integer, Integer> selected, Budget budget, long baseRevision) {
        this.graph = graph;
        this.selected = selected;
        this.budget = budget;
        this.baseRevision = baseRevision;
    }

    private FlexibleInputChoices(FlexibleInputChoices source) {
        this(source.graph, source.selected, source.budget, source.baseRevision);
        source.assigned.forEach((row, amounts) -> assigned.put(row, new HashMap<>(amounts)));
        source.consumed.forEach((row, amounts) -> consumed.put(row, new HashMap<>(amounts)));
        stock.putAll(source.stock);
        totals.putAll(source.totals);
        physicalStock.putAll(source.physicalStock);
        keyCursors.putAll(source.keyCursors);
    }

    private long amount(int row, AEKey key) {
        var changed = assigned.get(row);
        Long amount = changed == null ? null : changed.get(key);
        return amount != null ? amount : graph.assigned.get(row).getOrDefault(key, 0L);
    }

    private long stock(AEKey key) { return stock.getOrDefault(key, graph.stock.getOrDefault(key, 0L)); }
    private long total(AEKey key) { return totals.getOrDefault(key, graph.totals.getOrDefault(key, 0L)); }
    private long physical(AEKey key) {
        return physicalStock.getOrDefault(key, graph.buckets.physicalStock.getOrDefault(key, 0L));
    }

    private void put(Map<AEKey, Long> target, AEKey key, long value) {
        var old = target.put(key, value);
        if (checkpoints > 0) undo.add(() -> {
            if (old == null) target.remove(key);
            else target.put(key, old);
        });
    }

    @Override
    public Runnable checkpoint() {
        int mark = undo.size();
        checkpoints++;
        return () -> {
            for (int i = undo.size() - 1; i >= mark; i--) undo.remove(i).run();
            checkpoints--;
        };
    }

    // Operation-count tests verify that untouched rows are not materialized by a transaction.
    int touchedRows() { return assigned.size(); }
    int bucketCount() { return graph.stock.size(); }
    long remainingWork() { return budget.remaining; }
    long candidateVisits() { return candidateVisits; }

    @Override public boolean managesSlot(int slot) { return selected.containsKey(slot); }
    @Override public boolean exhausted() { return budget.remaining <= 0; }
    @Override public Choices copy() { return new FlexibleInputChoices(this); }

    @Override
    public long assigned(int slot, AEKey key) {
        var row = selected.get(slot);
        var bucket = graph.buckets.bucketOf.get(key);
        return row == null || bucket == null ? 0 : Math.min(physical(key), amount(row, bucket));
    }

    @Override
    public long available(int slot, AEKey key, long requested) {
        var row = selected.get(slot);
        if (row == null || requested <= 0) return 0;
        var bucket = graph.buckets.bucketOf.get(key);
        if (bucket == null) return 0;
        long unit = graph.options.get(row).getOrDefault(bucket, 0L);
        if (unit <= 0) return 0;
        requested = Math.min(requested, physical(key));
        long target = requested - requested % unit;
        while (amount(row, bucket) < target && !exhausted()) {
            var path = findPath(row, bucket, unit);
            if (path == null) break;
            long amount = Math.min(target - amount(row, bucket), path.capacity);
            amount -= amount % unit;
            if (amount <= 0) break;
            // Release the selected row's old key first. A terminal may be exactly that quota, so
            // this also frees room for the displaced consumer at the end of the residual path.
            change(row, path.release, -amount);
            for (var step : path.steps) {
                change(step.row, step.from, -amount);
                change(step.row, step.to, amount);
            }
            change(row, bucket, amount);
        }
        return Math.min(target, amount(row, bucket));
    }

    @Override
    public void consume(int slot, AEKey key, long amount) {
        if (amount <= 0) return;
        var row = selected.get(slot);
        var bucket = graph.buckets.bucketOf.get(key);
        if (row == null || bucket == null || amount > physical(key) || amount > amount(row, bucket)) {
            throw new IllegalStateException("Input consumed beyond its proven allocation");
        }
        long old = physical(key);
        change(row, bucket, -amount);
        put(stock, bucket, stock(bucket) - (graph.buckets.usable(key, old) - graph.buckets.usable(key, old - amount)));
        put(physicalStock, key, old - amount);
        var used = rowChanges(consumed, row);
        put(used, bucket, Math.addExact(used.getOrDefault(bucket, 0L), amount));
    }

    @Override
    public void retainAssignments() {
        if (checkpoints != 0) throw new IllegalStateException("Cannot publish a speculative checkpoint");
        if (published || graph.revision != baseRevision) return;
        published = true;
        // Restore extracted quantities in the certificate, leaving its original supply/demand.
        // Any subset of the extraction may now be accepted; the rest can be returned unchanged.
        var restoredTotals = new HashMap<>(totals);
        for (var row : assigned.entrySet()) {
            var used = consumed.getOrDefault(row.getKey(), Map.of());
            var target = graph.assigned.get(row.getKey());
            row.getValue().forEach((key, amount) -> {
                long restored = Math.addExact(amount, used.getOrDefault(key, 0L));
                if (restored == 0) target.remove(key);
                else target.put(key, restored);
            });
            used.forEach((key, amount) -> restoredTotals.merge(key, amount, Math::addExact));
        }
        restoredTotals.forEach((key, amount) -> {
            graph.totals.put(key, amount);
            graph.checkDeficit(key);
        });
        graph.recentlyConsumed.clear();
        consumed.forEach((row, amounts) -> graph.recentlyConsumed.put(row, Map.copyOf(amounts)));
        graph.revision++;
    }

    /** Iterate changed positive edges first, then the base positive list. No full row copy. */
    private Iterator<AEKey> positiveBuckets(int row) {
        var changed = assigned.getOrDefault(row, Map.of());
        // Capture keys only for the sparse overlay: consumption may mutate it while iterating.
        var extra = List.copyOf(changed.keySet()).iterator();
        var base = graph.assigned.get(row).keySet().iterator();
        return new Iterator<>() {
            AEKey next;
            public boolean hasNext() {
                while (next == null && (extra.hasNext() || base.hasNext())) {
                    boolean overlay = extra.hasNext();
                    var key = overlay ? extra.next() : base.next();
                    if (!overlay && changed.containsKey(key)) continue;
                    if (amount(row, key) > 0) next = key;
                }
                return next != null;
            }
            public AEKey next() {
                if (!hasNext()) throw new NoSuchElementException();
                var result = next; next = null; return result;
            }
        };
    }

    private PositiveKeyIndex.Link firstLiveKey(AEKey bucket) {
        var link = keyCursors.containsKey(bucket) ? keyCursors.get(bucket) : graph.buckets.liveKeys.get(bucket).first();
        var old = link;
        while (link != null && graph.buckets.usable(link.key, physical(link.key)) == 0) {
            candidateVisits++;
            link = link.next;
        }
        if (link != old) {
            boolean present = keyCursors.containsKey(bucket);
            var before = keyCursors.put(bucket, link);
            if (checkpoints > 0) undo.add(() -> {
                if (present) keyCursors.put(bucket, before); else keyCursors.remove(bucket);
            });
        }
        return link;
    }

    @Override
    public Iterable<GenericStack> preferredInputs(int slot) {
        var row = selected.get(slot);
        if (row == null) return List.of();
        return () -> new Iterator<>() {
            final Iterator<AEKey> buckets = positiveBuckets(row);
            AEKey bucket;
            PositiveKeyIndex.Link key;
            GenericStack next;
            public boolean hasNext() {
                while (next == null) {
                    if (key == null || amount(row, bucket) == 0) {
                        if (!buckets.hasNext()) return false;
                        bucket = buckets.next();
                        key = firstLiveKey(bucket);
                        if (key == null) continue;
                    }
                    var candidate = key.key;
                    key = key.next;
                    candidateVisits++;
                    long unit = graph.options.get(row).get(bucket);
                    if (physical(candidate) >= unit && amount(row, bucket) >= unit)
                        next = new GenericStack(candidate, unit);
                }
                return true;
            }
            public GenericStack next() {
                if (!hasNext()) throw new NoSuchElementException();
                var result = next; next = null; return result;
            }
        };
    }

    private Path findPath(int selectedRow, AEKey requested, long unit) {
        AEKey releasable = null;
        var releases = positiveBuckets(selectedRow);
        while (releases.hasNext()) {
            var key = releases.next();
            if (!budget.visit()) return null;
            if (!key.equals(requested) && amount(selectedRow, key) >= unit) {
                releasable = key;
                break;
            }
        }
        if (releasable == null) return null;
        var parent = new HashMap<AEKey, Step>();
        var visitedRows = new java.util.HashSet<Integer>();
        var queue = new ArrayDeque<AEKey>();
        parent.put(requested, null);
        queue.add(requested);
        while (!queue.isEmpty() && budget.visit()) {
            var key = queue.removeFirst();
            long own = key.equals(requested) ? 0 : amount(selectedRow, key);
            long free = stock(key) - total(key);
            if (own >= unit || free >= unit) {
                AEKey release = own >= unit ? key : releasable;
                long capacity = own >= unit ? own : Math.min(free, amount(selectedRow, release));
                var steps = new ArrayList<Step>();
                for (var step = parent.get(key); step != null; step = parent.get(step.from)) {
                    if (!budget.visit()) return null;
                    steps.add(step); // Reverse order: the far end has room first.
                    capacity = Math.min(capacity, amount(step.row, step.from));
                }
                return new Path(release, capacity, steps);
            }
            for (int row : graph.users.getOrDefault(key, List.of())) {
                if (!budget.visit()) return null;
                if (row == selectedRow || visitedRows.contains(row) || amount(row, key) < unit) continue;
                visitedRows.add(row);
                for (var alternative : graph.options.get(row).keySet()) {
                    if (!budget.visit()) return null;
                    if (parent.containsKey(alternative)) continue;
                    parent.put(alternative, new Step(row, key, alternative));
                    queue.addLast(alternative);
                }
            }
        }
        return null;
    }

    private void change(int row, AEKey key, long delta) {
        long current = amount(row, key);
        var changes = rowChanges(assigned, row);
        put(changes, key, current + delta);
        put(totals, key, total(key) + delta);
    }

    private Map<AEKey, Long> rowChanges(Map<Integer, Map<AEKey, Long>> target, int row) {
        var changes = target.get(row);
        if (changes == null) {
            changes = new HashMap<>();
            target.put(row, changes);
            if (checkpoints > 0) undo.add(() -> target.remove(row));
        }
        return changes;
    }

    private record Step(int row, AEKey from, AEKey to) { }
    private record Path(AEKey release, long capacity, List<Step> steps) { }
    private static final class Budget {
        long remaining;
        Budget(long remaining) { this.remaining = remaining; }
        boolean visit() {
            if (remaining <= 0) return false;
            remaining--;
            return true;
        }
    }

    private static final class Combined implements Choices {
        private final List<Choices> components;
        private final Map<Integer, Choices> owners = new HashMap<>();

        Combined(List<Choices> components) {
            this.components = components;
            for (var component : components) {
                var slots = component instanceof FlexibleInputChoices flexible
                        ? flexible.selected.keySet() : ((Combined) component).owners.keySet();
                for (int slot : slots) {
                    if (owners.put(slot, component) != null)
                        throw new IllegalArgumentException("Slot belongs to multiple allocation components");
                }
            }
        }
        private Choices owner(int slot) {
            return owners.get(slot);
        }
        @Override public boolean managesSlot(int slot) { return owner(slot) != null; }
        @Override public long assigned(int slot, AEKey key) { return owner(slot).assigned(slot, key); }
        @Override public long available(int slot, AEKey key, long requested) { return owner(slot).available(slot, key, requested); }
        @Override public void consume(int slot, AEKey key, long amount) { owner(slot).consume(slot, key, amount); }
        @Override public Iterable<GenericStack> preferredInputs(int slot) {
            var component = owner(slot);
            return component == null ? List.of() : component.preferredInputs(slot);
        }
        @Override public void retainAssignments() { components.forEach(Choices::retainAssignments); }
        @Override public Choices copy() { return new Combined(components.stream().map(Choices::copy).toList()); }
        @Override public Runnable checkpoint() {
            var rollbacks = components.stream().map(Choices::checkpoint).toList();
            return () -> { for (int i = rollbacks.size() - 1; i >= 0; i--) rollbacks.get(i).run(); };
        }
        @Override public boolean exhausted() { return components.stream().anyMatch(Choices::exhausted); }
    }
}
