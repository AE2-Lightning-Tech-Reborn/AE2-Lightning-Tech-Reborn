package com.moakiee.ae2lt.crafting.timewheel.allocation;

import com.moakiee.thunderbolt.core.crafting.batch.SharedBatchInputs;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;
import java.util.function.BiPredicate;

import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;

import com.moakiee.ae2lt.crafting.timewheel.allocation.CraftingInputAllocation;
import com.moakiee.thunderbolt.core.crafting.pattern.FuzzyPatternInputs;
import com.moakiee.thunderbolt.core.crafting.support.CraftingPatternDelegates;

/**
 * Derived, CPU-local arbitration of finite consumable inputs. Plans, patterns and persistence retain
 * their native identity. Each job indexes potential conflicts once, by primary key. Only components
 * touched by the selected pattern are visited. With both change listeners, finite components update
 * only changed keys/rows; other callers revalidate snapshots. Accepted-key checks are memoized, and
 * a feasible residual assignment is trimmed and reused after ordinary dispatches.
 *
 * <p>CPU adapters that opt into inventory notifications must call {@link #onInventoryChange} for new
 * keys. Positive-stock candidates retain membership; unanchored zero-stock variants are retired.
 * The default constructor rediscovers inventory variants for callers without that notification hook.
 *
 * <p>Replenishable slots cannot reserve their entire lifetime demand against present stock. Existing
 * seed/retained-output guards must be applied before allocation. This is material arbitration, not a
 * replacement scheduler for arbitrary producer/return cycles or unequal alternative exchange rates.
 */
public final class ExecutionInputAllocator {
    private static final long MAX_PAIRS = Math.min(262_144L,
            Math.max(1_024L, Long.getLong("ae2lt.executionAllocationMaxPairs", 32_768L)));
    private static final int MAX_VARIANTS = 4_096;
    private static final int MAX_PENDING_KEYS = 4_096;
    private static final int MAX_QUERY_CACHE = 8;
    private static final long FLOW_WORK_PER_TICK = 131_072;
    private final Map<IPatternDetails, ?> tasks;
    private final ToLongFunction<Object> remaining;
    private final boolean tracksInventoryChanges;
    private final boolean tracksTaskChanges;
    private final BiPredicate<IPatternDetails, Integer> separatelyReservedInput;
    private final Map<AEKey, Family> families = new HashMap<>();
    private final Map<AEKey, Set<Output>> outputsByPrimary = new HashMap<>();
    private final Map<Output, Integer> outputReferences = new HashMap<>();
    private final Map<IPatternDetails, Set<AEKey>> outputPrimaries = new HashMap<>();
    private final Map<IPatternDetails, List<Conflict>> byPattern = new HashMap<>();
    private boolean indexed;
    private long solveCount;
    private long snapshotCount;
    private long stockReadCount;
    private long trimmedRowCount;
    private long flowTick = Long.MIN_VALUE;
    private long flowWorkRemaining;

    public <T> ExecutionInputAllocator(Map<IPatternDetails, T> tasks, ToLongFunction<? super T> remaining) {
        this(tasks, remaining, false);
    }

    public <T> ExecutionInputAllocator(Map<IPatternDetails, T> tasks,
            ToLongFunction<? super T> remaining, boolean tracksInventoryChanges) {
        this(tasks, remaining, tracksInventoryChanges, false);
    }

    /** Quantity caching requires both notification contracts. Call onTaskChange after every count
     * mutation/removal and onInventoryChange after every inventory mutation, including rollback. */
    public <T> ExecutionInputAllocator(Map<IPatternDetails, T> tasks,
            ToLongFunction<? super T> remaining, boolean tracksInventoryChanges, boolean tracksTaskChanges) {
        this(tasks, remaining, tracksInventoryChanges, tracksTaskChanges, (pattern, slot) -> false);
    }

    /** Slots protected by an independent execution ledger are not lifetime demands against another
     * task's visible stock. The caller MUST apply that ledger's inventory guard before allocate and
     * extraction. Selected protected slots still require physical stock for the current dispatch;
     * ordinary consumable slots of the same pattern must return false. Evaluated once per job slot. */
    public <T> ExecutionInputAllocator(Map<IPatternDetails, T> tasks,
            ToLongFunction<? super T> remaining, boolean tracksInventoryChanges, boolean tracksTaskChanges,
            BiPredicate<IPatternDetails, Integer> separatelyReservedInput) {
        this.tasks = tasks;
        this.remaining = value -> remaining.applyAsLong(ExecutionInputAllocator.<T>cast(value));
        this.tracksInventoryChanges = tracksInventoryChanges;
        this.tracksTaskChanges = tracksTaskChanges;
        this.separatelyReservedInput = java.util.Objects.requireNonNull(separatelyReservedInput);
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) { return (T) value; }

    /** The CPU inventory listener also fires on removal/rollback; recording a known key is O(1). */
    public void onInventoryChange(AEKey key) {
        if (key == null) return;
        var family = families.get(key.dropSecondary());
        if (family != null && family.conflict != null) {
            var conflict = family.conflict;
            conflict.revision++;
            if (conflict.capacityLimited || family.oversized || conflict.dirtyKeys.size() >= MAX_PENDING_KEYS) {
                conflict.rescan = true;
                conflict.dirtyKeys.clear();
                invalidate(conflict);
            } else if (!conflict.rescan) conflict.dirtyKeys.add(key);
        }
    }

    /** Multiple updates during a provider transaction coalesce until the next extraction. This
     * observes accepted copies after rejected inputs have been returned, never provisional counts. */
    public void onTaskChange(IPatternDetails pattern) {
        for (var conflict : byPattern.getOrDefault(pattern, List.of())) {
            conflict.dirtyTasks.add(pattern);
            conflict.revision++;
        }
        for (var primary : outputPrimaries.getOrDefault(pattern, Set.of())) {
            var family = families.get(primary);
            if (family != null && family.conflict != null) {
                family.conflict.incremental = null;
                family.conflict.revision++;
            }
        }
    }

    private long copies(IPatternDetails pattern) {
        var progress = tasks.get(pattern);
        return progress == null ? 0 : Math.max(0, remaining.applyAsLong(progress));
    }

    private void addOutput(IPatternDetails pattern, AEKey key, boolean sameId) {
        var output = new Output(pattern, key, sameId);
        outputReferences.merge(output, 1, Integer::sum);
        outputPrimaries.computeIfAbsent(pattern, ignored -> new LinkedHashSet<>()).add(key.dropSecondary());
        outputsByPrimary.computeIfAbsent(key.dropSecondary(), ignored -> new LinkedHashSet<>())
                .add(output);
    }

    private void removeOutput(Output output) {
        int references = outputReferences.getOrDefault(output, 0);
        if (references > 1) { outputReferences.put(output, references - 1); return; }
        outputReferences.remove(output);
        var primary = output.key.dropSecondary();
        var entries = outputsByPrimary.get(primary);
        if (entries == null) return;
        entries.remove(output);
        if (entries.stream().noneMatch(entry -> entry.pattern.equals(output.pattern))) {
            var primaries = outputPrimaries.get(output.pattern);
            if (primaries != null) {
                primaries.remove(primary);
                if (primaries.isEmpty()) outputPrimaries.remove(output.pattern);
            }
        }
        if (entries.isEmpty()) outputsByPrimary.remove(primary);
    }

    private static void invalidate(Conflict conflict) {
        conflict.incremental = null;
        conflict.lastFinite = null;
        conflict.signature = null;
        conflict.cached.clear();
        conflict.failed.clear();
        conflict.budgetExceeded = false;
        conflict.pending = null;
    }

    // Called lazily after job submission/load has populated the native task map. Patterns do not
    // change during a job; completed entries may disappear, and their cached rows then have zero demand.
    private void index() {
        if (indexed) return;
        indexed = true;
        var rows = new ArrayList<Row>();
        var byPrimary = new HashMap<AEKey, List<Row>>();
        for (var pattern : tasks.keySet()) {
            var provider = CraftingPatternDelegates.forProviderLookup(pattern);
            var fuzzy = provider instanceof FuzzyPatternInputs metadata ? metadata : null;
            var outputs = pattern.getOutputs();
            for (int i = 0; i < outputs.size(); i++) {
                if (outputs.get(i).amount() > 0) addOutput(pattern, outputs.get(i).what(),
                        fuzzy != null && fuzzy.producesSameIdVariants(i));
            }
            var inputs = pattern.getInputs();
            for (int slot = 0; slot < inputs.length; slot++) {
                var row = new Row(pattern, slot, inputs[slot], fuzzy != null && fuzzy.acceptsSameIdVariants(slot),
                        separatelyReservedInput.test(pattern, slot));
                for (var template : row.templates) {
                    if (template == null || template.amount() <= 0) continue;
                    var primary = template.what().dropSecondary();
                    if (row.families.putIfAbsent(primary,
                            families.computeIfAbsent(primary, Family::new)) == null) {
                        byPrimary.computeIfAbsent(primary, ignored -> new ArrayList<>()).add(row);
                        families.get(primary).rows.add(row);
                    }
                    row.anchorKeys.add(template.what());
                    var family = families.get(primary);
                    family.anchors.add(template.what());
                    if (family.keys.add(template.what())) family.version++;
                    var returned = row.input.getRemainingKey(template.what());
                    if (returned != null) addOutput(pattern, returned, false);
                }
                rows.add(row);
            }
        }
        var visited = java.util.Collections.newSetFromMap(new IdentityHashMap<Row, Boolean>());
        var visitedPrimaries = new LinkedHashSet<AEKey>();
        for (var start : rows) {
            if (!visited.add(start)) continue;
            var conflict = new Conflict();
            var queue = new ArrayDeque<Row>();
            queue.add(start);
            while (!queue.isEmpty()) {
                var row = queue.removeFirst();
                conflict.rows.add(row);
                conflict.rowsByPattern.computeIfAbsent(row.pattern, ignored -> new ArrayList<>()).add(row);
                for (var primary : row.families.keySet()) {
                    if (!visitedPrimaries.add(primary)) continue;
                    conflict.families.add(row.families.get(primary));
                    row.families.get(primary).conflict = conflict;
                    for (var neighbor : byPrimary.get(primary)) if (visited.add(neighbor)) queue.addLast(neighbor);
                }
            }
            var patterns = new LinkedHashSet<IPatternDetails>();
            for (var row : conflict.rows) patterns.add(row.pattern);
            for (var pattern : patterns) byPattern.computeIfAbsent(pattern, ignored -> new ArrayList<>()).add(conflict);
        }
    }

    public CraftingInputAllocation allocate(
            IPatternDetails selected, ICraftingInventory inventory, Level level, long maxCopies) {
        if (maxCopies <= 0) return CraftingInputAllocation.WAIT;
        long tick = level == null ? flowTick + 1 : level.getGameTime();
        if (tick != flowTick) { flowTick = tick; flowWorkRemaining = FLOW_WORK_PER_TICK; }
        index();
        var conflicts = byPattern.get(selected);
        if (conflicts == null) return CraftingInputAllocation.UNRESTRICTED;
        var allowances = new LinkedHashMap<Integer, Map<AEKey, Long>>();
        var choices = new ArrayList<java.util.function.Supplier<CraftingInputAllocation.Choices>>();
        for (var conflict : conflicts) {
            // A key family used by only one slot cannot steal any other slot's allocation.
            if (conflict.rows.size() < 2) continue;
            var allocation = allocateConflict(conflict, selected, inventory, level, maxCopies);
            if (!allocation.allowed()) return allocation;
            allowances.putAll(allocation.slotAllowances());
            if (allocation.choiceFactory() != null) choices.add(allocation.choiceFactory());
        }
        return allowances.isEmpty() ? CraftingInputAllocation.UNRESTRICTED
                : new CraftingInputAllocation(true, allowances, FlexibleInputChoices.combine(choices));
    }

    private CraftingInputAllocation allocateConflict(Conflict conflict, IPatternDetails selected,
            ICraftingInventory inventory, Level level, long maxCopies) {
        prepareChanges(conflict, inventory);
        boolean notified = tracksInventoryChanges && tracksTaskChanges && inventory instanceof ListCraftingInventory;
        var failed = conflict.failed.get(selected);
        if (notified && failed != null && failed.revision == conflict.revision
                && failed.inventory == inventory && failed.copies == maxCopies) return CraftingInputAllocation.WAIT;
        CraftingInputAllocation allocation;
        try {
            allocation = computeConflict(conflict, selected, inventory, level, maxCopies);
        } catch (ArithmeticException overflow) {
            // A saturated bucket sum would lose physical capacity information. Do not fail open.
            invalidate(conflict);
            allocation = CraftingInputAllocation.WAIT;
        } finally {
            conflict.observedStock.clear();
        }
        if (notified && !allocation.allowed() && conflict.pending == null) {
            conflict.failed.put(selected, new Failed(conflict.revision, inventory, maxCopies));
        }
        return allocation;
    }

    private CraftingInputAllocation computeConflict(Conflict conflict, IPatternDetails selected,
            ICraftingInventory inventory, Level level, long maxCopies) {
        if (conflict.budgetExceeded) return CraftingInputAllocation.WAIT;
        var pending = conflict.pending;
        if (tracksInventoryChanges && tracksTaskChanges && inventory instanceof ListCraftingInventory
                && pending != null && pending.revision == conflict.revision && pending.inventory == inventory
                && (pending.finite || pending.query.selected.equals(selected) && pending.maxCopies == maxCopies)) {
            if (!advance(pending.search)) return CraftingInputAllocation.WAIT;
            conflict.pending = null;
            return finish(conflict, pending.rows, selected, inventory, pending.stock,
                    pending.finite, new Query(selected, pending.query.copies), pending.result());
        }
        var incremental = conflict.incremental;
        if (incremental != null && incremental.inventory == inventory
                && synchronize(conflict, incremental, inventory)) {
            var allocation = incremental.allocation(conflict.rowsByPattern.get(selected));
            if (allocation != null) return allocation;
        }
        conflict.incremental = null;
        // A selected slot with only one possible concrete key has no choice to arbitrate. This
        // keeps ordinary exact recipes on the native fast path even in a huge shared-key component.
        boolean ambiguous = false;
        long selectedWork = 0;
        for (var row : conflict.rowsByPattern.get(selected)) {
            for (var family : row.families.values()) {
                refresh(family, inventory);
                if (family.oversized) return CraftingInputAllocation.WAIT;
            }
            selectedWork = add(selectedWork, candidateWork(row));
            if (selectedWork > MAX_PAIRS) {
                conflict.capacityLimited = true;
                return CraftingInputAllocation.WAIT;
            }
            updateOptions(row, level);
            ambiguous |= row.options.size() > 1;
        }
        if (!ambiguous) return CraftingInputAllocation.UNRESTRICTED;
        snapshotCount++;
        for (var family : conflict.families) {
            refresh(family, inventory);
            if (family.oversized) return CraftingInputAllocation.WAIT;
        }
        var rows = new ArrayList<Row>();
        long work = 0;
        for (var row : conflict.rows) {
            work = add(work, candidateWork(row));
            if (work > MAX_PAIRS) {
                conflict.budgetExceeded = true;
                conflict.capacityLimited = true;
                return CraftingInputAllocation.WAIT;
            }
            row.copies = copies(row.pattern);
            if (row.copies <= 0) continue;
            updateOptions(row, level);
            // A guarded inventory is private to the selected consumer. Requiring another loop's
            // already-hidden seed here double-reserves it and can block the ordinary producer that
            // this loop is waiting for, especially after the previous member has fully dispatched.
            row.replenished = row.shared || row.reservedSeparately;
            for (var primary : row.families.keySet()) {
                for (var output : outputsByPrimary.getOrDefault(primary, Set.of())) {
                    if (copies(output.pattern) > 0 && (output.sameId ? row.sameId
                            : row.input.isValid(output.key, level))) {
                        row.replenished = true;
                        break;
                    }
                }
            }
            if (!row.replenished || row.pattern.equals(selected)) rows.add(row);
        }
        if (rows.size() < 2 || rows.stream().noneMatch(row -> row.options.size() > 1)) {
            return CraftingInputAllocation.UNRESTRICTED;
        }
        // A predicate may reject its own sample until a late component/durability variant arrives.
        // Missing candidates are missing capacity, not an unsupported-model reason to bypass guards.
        if (rows.stream().anyMatch(row -> row.options.isEmpty())) return CraftingInputAllocation.WAIT;
        if (!supports(rows)) return CraftingInputAllocation.UNRESTRICTED;
        var stock = new LinkedHashMap<AEKey, Long>();
        for (var row : rows) for (var key : row.options.keySet()) {
            stock.computeIfAbsent(key, k -> readStock(inventory, k));
        }
        boolean finite = rows.stream().noneMatch(row -> row.replenished);
        var buckets = new InputCapacityBuckets(rows.stream().map(row -> row.options).toList(), stock);
        long limit = maxCopies;
        for (var row : rows) {
            if (!row.pattern.equals(selected) || !row.replenished) continue;
            long units = 0;
            for (var option : row.options.entrySet()) units = add(units, stock.get(option.getKey()) / option.getValue());
            limit = Math.min(limit, units / Math.max(1, row.input.getMultiplier()));
        }
        if (limit <= 0) return CraftingInputAllocation.WAIT;
        var signature = new ArrayList<Long>(rows.size() * 3 + stock.size());
        for (var row : conflict.rows) {
            signature.add(row.copies);
            signature.add(row.replenished ? 1L : 0L);
            signature.add(row.optionsVersion);
        }
        signature.addAll(stock.values());
        var context = new Query(selected, finite ? 0 : limit);
        if (!signature.equals(conflict.signature)) {
            conflict.signature = signature;
            conflict.cached.clear();
        } else {
            var cached = conflict.cached.get(context);
            if (cached != null) return cached;
        }
        var result = finite ? reuse(conflict.lastFinite, rows, buckets.capacity) : null;
        if (result == null) {
            pending = conflict.pending;
            // A freshly validated reservation view may be a new wrapper every call. Equal complete
            // stock/task/topology snapshots can resume the same search regardless of wrapper identity.
            if (pending == null || !pending.signature.equals(signature)
                    || (!finite || !pending.finite) && !pending.query.equals(context)) {
                solveCount++;
                var requests = new ArrayList<BucketAllocationSearch.Request>();
                long quantum = 1;
                for (int i = 0; i < rows.size(); i++) {
                    var row = rows.get(i);
                    if (row.options.size() > 1) quantum = row.options.values().iterator().next();
                    requests.add(new BucketAllocationSearch.Request(buckets.options.get(i), row.copies,
                            row.input.getMultiplier(), row.replenished && row.pattern.equals(selected)));
                }
                pending = new Pending(List.copyOf(rows), stock, finite, signature, context, maxCopies,
                        conflict.revision, inventory,
                        new BucketAllocationSearch(requests, buckets.capacity, quantum, limit, !finite));
                conflict.pending = pending;
            }
            if (!advance(pending.search)) return CraftingInputAllocation.WAIT;
            result = pending.result();
            conflict.pending = null;
        }
        return finish(conflict, rows, selected, inventory, stock, finite, context, result);
    }

    private boolean advance(BucketAllocationSearch search) {
        boolean done = search.advance(flowWorkRemaining);
        flowWorkRemaining = Math.max(0, flowWorkRemaining - search.lastWork());
        return done;
    }

    private CraftingInputAllocation finish(Conflict conflict, List<Row> rows, IPatternDetails selected,
            ICraftingInventory inventory, Map<AEKey, Long> stock, boolean finite, Query context,
            Map<Row, Map<AEKey, Long>> result) {
        conflict.pending = null;
        conflict.lastFinite = finite ? result : null;
        CraftingInputAllocation allocation;
        if (result == null) allocation = CraftingInputAllocation.WAIT;
        else {
            var selectedAmounts = new LinkedHashMap<Integer, Map<AEKey, Long>>();
            result.forEach((row, amounts) -> {
                if (row.pattern.equals(selected)) selectedAmounts.put(row.slot, Map.of());
            });
            var demands = new ArrayList<FlexibleInputChoices.Demand>();
            for (var row : rows) demands.add(new FlexibleInputChoices.Demand(
                    row.pattern.equals(selected) ? row.slot : -1, row.options, result.get(row)));
            // Reservation wrappers may change their visible quotas without an inventory event.
            // Only the native, notified inventory is eligible for persistent quantity caching.
            if (finite && tracksInventoryChanges && tracksTaskChanges && inventory instanceof ListCraftingInventory) {
                conflict.incremental = new Incremental(rows, demands, stock, inventory);
                conflict.dirtyKeys.clear();
                conflict.dirtyTasks.clear();
                allocation = conflict.incremental.allocation(conflict.rowsByPattern.get(selected));
            } else allocation = new CraftingInputAllocation(true, selectedAmounts,
                    FlexibleInputChoices.factory(demands, stock, MAX_PAIRS));
        }
        if (conflict.incremental == null) {
            if (conflict.cached.size() >= MAX_QUERY_CACHE) conflict.cached.clear();
            conflict.cached.put(context, allocation);
        }
        else {
            // The incremental witness is mutable between extractions, not a signature snapshot.
            conflict.cached.clear();
            conflict.signature = null;
        }
        return allocation;
    }

    private long readStock(ICraftingInventory inventory, AEKey key) {
        stockReadCount++;
        return Math.max(0, inventory.extract(key, Long.MAX_VALUE, Actionable.SIMULATE));
    }

    private boolean synchronize(Conflict conflict, Incremental state, ICraftingInventory inventory) {
        for (var entry : conflict.observedStock.entrySet()) state.graph.setStock(entry.getKey(), entry.getValue());
        for (var pattern : conflict.dirtyTasks) {
            long copies = copies(pattern);
            for (var row : conflict.rowsByPattern.get(pattern)) {
                var id = state.ids.get(row);
                if (id == null) return false;
                long quantum = row.options.values().iterator().next();
                long perCopy = multiply(row.input.getMultiplier(), quantum);
                if (copies > Long.MAX_VALUE / perCopy) return false;
                trimmedRowCount++;
                if (!state.graph.trim(id, copies * perCopy)) return false;
                row.copies = copies;
            }
        }
        conflict.dirtyKeys.clear();
        conflict.dirtyTasks.clear();
        state.graph.recentlyConsumed.clear();
        return state.graph.deficits.isEmpty();
    }

    private static final class Incremental {
        final ICraftingInventory inventory;
        final Map<Row, Integer> ids = new IdentityHashMap<>();
        final FlexibleInputChoices.Graph graph;

        Incremental(List<Row> rows, List<FlexibleInputChoices.Demand> demands,
                Map<AEKey, Long> stock, ICraftingInventory inventory) {
            this.inventory = inventory;
            this.graph = new FlexibleInputChoices.Graph(demands, stock);
            for (int i = 0; i < rows.size(); i++) ids.put(rows.get(i), i);
        }

        CraftingInputAllocation allocation(List<Row> rows) {
            var selected = new LinkedHashMap<Integer, Integer>();
            var amounts = new LinkedHashMap<Integer, Map<AEKey, Long>>();
            for (var row : rows) {
                var id = ids.get(row);
                if (id == null) return null;
                selected.put(row.slot, id);
                amounts.put(row.slot, Map.of());
            }
            return new CraftingInputAllocation(true, amounts, graph.factory(selected, MAX_PAIRS));
        }
    }

    private long candidateWork(Row row) {
        // Anchors already belong to their family; count each potential row/key relation once.
        long result = 0;
        for (var family : row.families.values()) result = add(result, family.keys.size());
        for (var primary : row.families.keySet()) {
            result = add(result, outputsByPrimary.getOrDefault(primary, Set.of()).size());
        }
        return result;
    }

    private void refresh(Family family, ICraftingInventory inventory) {
        if (!family.initialized || !tracksInventoryChanges) {
            // Reconcile the bounded old set before enumerating. Iterator implementations may also
            // expose zero-count entries; never turn those into permanent candidate history.
            for (var key : List.copyOf(family.keys)) {
                if (!family.anchors.contains(key) && inventory.extract(key, Long.MAX_VALUE, Actionable.SIMULATE) <= 0)
                    family.forget(key);
            }
            family.oversized = false;
            for (var key : inventory.findFuzzyTemplates(family.primary)) {
                if (inventory.extract(key, Long.MAX_VALUE, Actionable.SIMULATE) > 0) family.add(key);
                if (family.oversized) break;
            }
            family.initialized = true;
        }
    }

    private void prepareChanges(Conflict conflict, ICraftingInventory inventory) {
        conflict.observedStock.clear();
        if (conflict.rescan) {
            // Retire old zero-stock keys across the WHOLE component before re-admitting arrivals.
            // Otherwise a full earlier family could prevent us from reaching a later stale family.
            for (var family : conflict.families) {
                for (var key : List.copyOf(family.keys)) {
                    if (!family.anchors.contains(key)
                            && inventory.extract(key, Long.MAX_VALUE, Actionable.SIMULATE) <= 0) family.forget(key);
                }
                family.initialized = false;
            }
            conflict.rescan = false;
            conflict.capacityLimited = false;
            conflict.budgetExceeded = false;
        }
        // Bound pending events even when a component is never selected again. Coalesced events are
        // consumed only after extraction/rollback has finished, so transient zeros do not evict keys.
        for (var key : conflict.dirtyKeys) {
            long amount = readStock(inventory, key);
            conflict.observedStock.put(key, amount);
        }
        conflict.dirtyKeys.clear();
        // Retire first: callbacks can report arrival before departure even though the reconciled
        // inventory fits the limit. Event order must not make a live replacement undiscoverable.
        for (var entry : conflict.observedStock.entrySet()) {
            if (entry.getValue() == 0) families.get(entry.getKey().dropSecondary()).forget(entry.getKey());
        }
        for (var entry : conflict.observedStock.entrySet()) {
            if (entry.getValue() > 0) families.get(entry.getKey().dropSecondary()).add(entry.getKey());
        }
    }

    private void updateOptions(Row row, Level level) {
        long version = 0;
        for (var family : row.families.values()) version += family.version;
        if (row.optionsVersion == version) return;
        row.optionsVersion = version;
        row.options.clear();
        row.shared = false;
        var primaryAmounts = new LinkedHashMap<AEKey, Long>();
        for (var template : row.templates) {
            if (template == null || template.amount() <= 0) continue;
            addOption(row, template.what(), template.amount(), level);
            primaryAmounts.putIfAbsent(template.what().dropSecondary(), template.amount());
        }
        for (var entry : primaryAmounts.entrySet()) {
            for (var key : row.families.get(entry.getKey()).keys) addOption(row, key, entry.getValue(), level);
        }
    }

    private void addOption(Row row, AEKey key, long amount, Level level) {
        if (!row.valid.computeIfAbsent(key, k -> row.input.isValid(k, level))) return;
        row.options.putIfAbsent(key, amount);
        row.shared |= SharedBatchInputs.isSharedInput(row.pattern, row.slot, key);
        if (!row.returned.containsKey(key)) {
            var returned = row.input.getRemainingKey(key);
            var output = returned == null ? null : new Output(row.pattern, returned, false);
            row.returned.put(key, output);
            if (output != null) addOutput(row.pattern, returned, false);
        }
    }

    /** Keep an existing feasible flow after consumption. Drop each row's consumed quota, first from
     * keys whose stock fell. Only a changed competition needs another maximum-flow calculation. */
    private static Map<Row, Map<AEKey, Long>> reuse(Map<Row, Map<AEKey, Long>> previous,
            List<Row> rows, Map<AEKey, Long> stock) {
        if (previous == null) return null;
        var result = new LinkedHashMap<Row, Map<AEKey, Long>>();
        var totals = new HashMap<AEKey, Long>();
        for (var row : rows) {
            var old = previous.get(row);
            if (old == null) return null;
            result.put(row, new LinkedHashMap<>(old));
            old.forEach((key, amount) -> totals.merge(key, amount, ExecutionInputAllocator::add));
        }
        for (var row : rows) {
            long quantum = row.options.values().iterator().next();
            long required = multiply(multiply(row.copies, Math.max(1, row.input.getMultiplier())), quantum);
            var allocation = result.get(row);
            long total = 0;
            for (long amount : allocation.values()) total = add(total, amount);
            if (total < required) return null;
            long excess = total - required;
            for (int pass = 0; pass < 2 && excess > 0; pass++) {
                for (var entry : allocation.entrySet()) {
                    long remove = Math.min(excess, entry.getValue());
                    if (pass == 0) {
                        long deficit = Math.max(0, totals.get(entry.getKey()) - stock.getOrDefault(entry.getKey(), 0L));
                        long units = deficit == 0 ? 0 : 1 + (deficit - 1) / quantum;
                        remove = Math.min(remove, multiply(units, quantum));
                    }
                    if (remove == 0) continue;
                    entry.setValue(entry.getValue() - remove);
                    totals.merge(entry.getKey(), -remove, Long::sum);
                    excess -= remove;
                }
            }
        }
        for (var entry : totals.entrySet()) if (entry.getValue() > stock.getOrDefault(entry.getKey(), 0L)) return null;
        return result;
    }

    // Exposed within the package for operation-count regression tests, not a global metrics counter.
    long solveCount() { return solveCount; }
    long snapshotCount() { return snapshotCount; }
    long stockReadCount() { return stockReadCount; }
    long trimmedRowCount() { return trimmedRowCount; }
    record CacheStats(int liveVariants, int validityEntries, int returnEntries, int outputEntries,
            int pendingKeys, int allocationSnapshots, int buckets) { }
    CacheStats cacheStats() {
        int variants = 0, validity = 0, returned = 0, pending = 0, snapshots = 0, buckets = 0;
        var rows = java.util.Collections.newSetFromMap(new IdentityHashMap<Row, Boolean>());
        var conflicts = java.util.Collections.newSetFromMap(new IdentityHashMap<Conflict, Boolean>());
        for (var family : families.values()) {
            for (var key : family.keys) if (!family.anchors.contains(key)) variants++;
            rows.addAll(family.rows);
            if (family.conflict != null) conflicts.add(family.conflict);
        }
        for (var row : rows) { validity += row.valid.size(); returned += row.returned.size(); }
        for (var conflict : conflicts) {
            pending += conflict.dirtyKeys.size();
            snapshots += conflict.cached.size();
            if (conflict.incremental != null) buckets += conflict.incremental.graph.stock.size();
        }
        return new CacheStats(variants, validity, returned, outputReferences.size(), pending, snapshots, buckets);
    }

    /** Equal-sized alternative units form ordinary integral capacity matching. Exact rows may use
     * any unit size: reserve their raw amounts first. Unequal alternative exchanges retain native
     * extraction until a weighted integer allocator can prove those exchanges. */
    private static boolean supports(List<Row> rows) {
        long quantum = 0;
        for (var row : rows) {
            if (row.options.isEmpty() || row.input.getMultiplier() <= 0) return false;
            long multiplier = row.input.getMultiplier();
            if (row.copies > Long.MAX_VALUE / multiplier) return false;
            long units = row.copies * multiplier;
            for (long amount : row.options.values()) if (units > Long.MAX_VALUE / amount) return false;
            if (row.options.size() <= 1) continue;
            for (long amount : row.options.values()) {
                if (quantum == 0) quantum = amount;
                if (quantum != amount) return false;
            }
            if (row.input.getMultiplier() <= 0) return false;
        }
        long edges = 0;
        for (var row : rows) edges += row.options.size();
        return edges <= MAX_PAIRS;
    }

    private static long add(long left, long right) {
        return left >= Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
    private static long multiply(long left, long right) {
        return left <= 0 || right <= 0 ? 0 : left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }
    private record Output(IPatternDetails pattern, AEKey key, boolean sameId) { }
    private record Query(IPatternDetails selected, long copies) { }
    private record Failed(long revision, ICraftingInventory inventory, long copies) { }
    private record Pending(List<Row> rows, Map<AEKey, Long> stock, boolean finite, List<Long> signature,
            Query query, long maxCopies, long revision, ICraftingInventory inventory, BucketAllocationSearch search) {
        Map<Row, Map<AEKey, Long>> result() {
            var amounts = search.result();
            if (amounts == null) return null;
            var result = new LinkedHashMap<Row, Map<AEKey, Long>>();
            for (int i = 0; i < rows.size(); i++) result.put(rows.get(i), amounts.getOrDefault(i, Map.of()));
            return result;
        }
    }
    private final class Family {
        final AEKey primary;
        final Set<AEKey> keys = new LinkedHashSet<>();
        final Set<AEKey> anchors = new LinkedHashSet<>();
        final List<Row> rows = new ArrayList<>();
        long version;
        boolean initialized;
        boolean oversized;
        Conflict conflict;
        Family(AEKey primary) { this.primary = primary; }
        void add(AEKey key) {
            if (oversized || keys.contains(key)) return;
            if (conflict != null) {
                invalidate(conflict);
                conflict.revision++;
            }
            if (keys.size() >= MAX_VARIANTS || conflict != null && conflict.liveVariants >= MAX_PAIRS) {
                oversized = true;
                if (conflict != null) conflict.capacityLimited = true;
            } else {
                keys.add(key);
                version++;
                if (conflict != null && !anchors.contains(key)) conflict.liveVariants++;
            }
        }
        void forget(AEKey key) {
            if (anchors.contains(key) || !keys.remove(key)) return;
            if (conflict != null) conflict.liveVariants--;
            version++;
            oversized = false;
            for (var row : rows) {
                if (row.anchorKeys.contains(key)) continue;
                row.valid.remove(key);
                row.options.remove(key);
                var output = row.returned.remove(key);
                if (output != null) removeOutput(output);
            }
            if (conflict != null) invalidate(conflict);
        }
    }
    private static final class Conflict {
        final List<Row> rows = new ArrayList<>();
        final Map<IPatternDetails, List<Row>> rowsByPattern = new HashMap<>();
        final List<Family> families = new ArrayList<>();
        final Map<Query, CraftingInputAllocation> cached = new LinkedHashMap<>();
        List<Long> signature;
        boolean budgetExceeded;
        Map<Row, Map<AEKey, Long>> lastFinite;
        Incremental incremental;
        Pending pending;
        final Set<AEKey> dirtyKeys = new LinkedHashSet<>();
        final Map<AEKey, Long> observedStock = new LinkedHashMap<>();
        boolean rescan;
        boolean capacityLimited;
        int liveVariants;
        final Set<IPatternDetails> dirtyTasks = new LinkedHashSet<>();
        long revision;
        final Map<IPatternDetails, Failed> failed = new HashMap<>();
    }
    private static final class Row {
        final IPatternDetails pattern;
        final int slot;
        final IPatternDetails.IInput input;
        final GenericStack[] templates;
        final Map<AEKey, Family> families = new LinkedHashMap<>();
        final Map<AEKey, Boolean> valid = new HashMap<>();
        final Set<AEKey> anchorKeys = new LinkedHashSet<>();
        final Map<AEKey, Output> returned = new HashMap<>();
        final LinkedHashMap<AEKey, Long> options = new LinkedHashMap<>();
        final boolean sameId;
        final boolean reservedSeparately;
        long copies;
        long optionsVersion = -1;
        boolean shared;
        boolean replenished;
        Row(IPatternDetails pattern, int slot, IPatternDetails.IInput input, boolean sameId,
                boolean reservedSeparately) {
            this.pattern = pattern;
            this.slot = slot;
            this.input = input;
            this.templates = input.getPossibleInputs();
            this.sameId = sameId;
            this.reservedSeparately = reservedSeparately;
        }
    }
}
