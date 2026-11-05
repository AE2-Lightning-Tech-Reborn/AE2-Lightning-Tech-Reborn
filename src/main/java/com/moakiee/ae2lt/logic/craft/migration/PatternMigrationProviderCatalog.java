package com.moakiee.ae2lt.logic.craft.migration;

import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.moakiee.ae2lt.mixin.PatternProviderLogicAccessor;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Tracks a completed native catalog so migration can remove slots without decoding it again. */
public final class PatternMigrationProviderCatalog {
    public interface Owner {
        PatternMigrationProviderCatalog ae2lt$migrationCatalog();
    }

    private record Entry(int index, AEItemKey definition, IPatternDetails details, Set<AEKey> inputs) {}
    public record Change(InternalInventory inventory, int slot, ItemStack identity, ItemStack before, Object entry) {}

    @Nullable final PatternProviderLogic owner;
    @Nullable private final Legacy legacy;
    private final InternalInventory inventory;
    private final IdentityHashMap<ItemStack, Entry> entries = new IdentityHashMap<>();
    private final Map<AEKey, Integer> inputReferences = new HashMap<>();
    private final int[] removed;
    private int size;
    private boolean ready;
    private boolean supported = true;
    private long updates;

    public PatternMigrationProviderCatalog(InternalInventory inventory) {
        this(inventory, null);
    }

    /** Retains the GTL sliced-index API without changing native decoding or refresh timing. */
    PatternMigrationProviderCatalog(PatternProviderLogic owner) {
        this(owner.getPatternInv(), owner);
    }

    private PatternMigrationProviderCatalog(InternalInventory inventory, @Nullable PatternProviderLogic owner) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.owner = owner;
        legacy = owner != null ? new Legacy(owner) : null;
        removed = new int[inventory.size() + 1];
    }

    int prepare(long deadline, int limit) { return legacy().prepare(deadline, limit); }
    boolean ready() { return legacy != null ? legacy.ready() : ready; }
    void track(int slot, AEItemKey key) { legacy().track(slot, key); }
    void changed(int slot) { legacy().changed(slot); }
    void flush() { legacy().flush(); }

    private Legacy legacy() {
        if (legacy == null) throw new IllegalStateException("This catalog has no native provider owner");
        return legacy;
    }

    /** Called during native decoding; neither the native decoder nor its resulting list is replaced. */
    public void decoded(ItemStack stack, IPatternDetails details) {
        if (details == null) return;
        var inputs = new HashSet<AEKey>();
        for (var input : details.getInputs()) {
            for (var possible : input.getPossibleInputs()) inputs.add(possible.what().dropSecondary());
        }
        for (var input : inputs) inputReferences.merge(input, 1, Integer::sum);
        if (details instanceof IMolecularAssemblerSupportedPattern) {
            var entry = new Entry(size, AEItemKey.of(stack.copy()), details, Set.copyOf(inputs));
            if (entries.put(stack, entry) != null) supported = false;
        }
        size++;
    }

    public void finish(List<IPatternDetails> patterns, Set<AEKey> inputs) {
        ready = supported && size == patterns.size() && inputs.equals(inputReferences.keySet());
    }

    public long updates() { return updates; }

    public Change capture(InternalInventory actual, int slot) {
        var stack = actual.getStackInSlot(slot);
        return new Change(actual, slot, stack, stack.copy(), actual == inventory ? entries.get(stack) : null);
    }

    /** Validate every change before touching the catalog. Unknown insertions use native rebuild instead. */
    public boolean apply(InternalInventory actual, List<IPatternDetails> patterns, Set<AEKey> inputs,
            Iterable<Change> changes) {
        if (!ready || inventory != actual || patterns.size() != size) return false;
        var removals = new ArrayList<Entry>();
        var rebound = new IdentityHashMap<ItemStack, Entry>();
        var changedEntries = java.util.Collections.newSetFromMap(new IdentityHashMap<Entry, Boolean>());
        for (var change : changes) {
            if (change.inventory != inventory || !(change.entry instanceof Entry entry)
                    || !changedEntries.add(entry) || !entry.definition.equals(AEItemKey.of(change.before))) return false;
            int position = position(entry.index);
            if (position < 0 || position >= size || patterns.get(position) != entry.details) return false;
            var after = inventory.getStackInSlot(change.slot);
            if (after.isEmpty()) removals.add(entry);
            else if (ItemStack.isSameItemSameTags(change.before, after)) {
                var existing = entries.get(after);
                if (existing != null && existing != entry) return false;
                rebound.put(after, entry);
            } else return false;
            for (var key : entry.inputs) {
                if (!inputs.contains(key) || inputReferences.getOrDefault(key, 0) <= 0) return false;
            }
        }
        // Removing in descending order preserves all remaining native list indices within this batch.
        removals.sort(Comparator.comparingInt(Entry::index).reversed());
        for (var entry : removals) {
            patterns.remove(position(entry.index));
            for (int i = entry.index + 1; i < removed.length; i += i & -i) removed[i]++;
            size--;
            for (var key : entry.inputs) {
                int remaining = inputReferences.get(key) - 1;
                if (remaining == 0) { inputReferences.remove(key); inputs.remove(key); }
                else inputReferences.put(key, remaining);
            }
        }
        // Retire/rebind only changed stack identities; do not walk the entire provider inventory.
        for (var change : changes) entries.remove(change.identity);
        entries.putAll(rebound);
        updates++;
        return true;
    }

    private int position(int original) {
        int count = 0;
        for (int i = original; i > 0; i -= i & -i) count += removed[i];
        return original - count;
    }

    /** GTL's revision-bound catalog and exact network removal remain an independent compatibility path. */
    private static final class Legacy {
        final PatternProviderLogic owner;
        private final PatternProviderLogicAccessor access;
        private Map<AEItemKey, IntOpenHashSet> positions = new HashMap<>();
        private Map<AEKey, Integer> inputs = new HashMap<>();
        private Map<IPatternDetails, Integer> equivalentPatterns = new HashMap<>();
        private final Map<Integer, AEItemKey> changedSlots = new HashMap<>();
        private long revision = -1;
        private int cursor;
        private int size;
        private boolean unexpectedChange;
        private boolean networkRefresh;

        Legacy(PatternProviderLogic owner) {
            this.owner = owner;
            access = (PatternProviderLogicAccessor) owner;
        }

        int prepare(long deadline, int limit) {
            long current = ((PatternMigrationProviderRevision) owner).ae2lt$patternCatalogRevision();
            var patterns = access.getPatterns();
            if (revision != current || size != patterns.size()) {
                positions = new HashMap<>();
                inputs = new HashMap<>();
                equivalentPatterns = new HashMap<>();
                revision = current;
                size = patterns.size();
                cursor = 0;
            }
            int checked = 0;
            while (cursor < size && checked < limit && System.nanoTime() < deadline) {
                var pattern = patterns.get(cursor);
                equivalentPatterns.merge(pattern, 1, Integer::sum);
                positions.computeIfAbsent(pattern.getDefinition(), ignored -> new IntOpenHashSet()).add(cursor++);
                for (var input : pattern.getInputs()) for (var option : input.getPossibleInputs()) {
                    inputs.merge(option.what().dropSecondary(), 1, Integer::sum);
                }
                checked++;
            }
            return checked;
        }

        boolean ready() { return cursor == size; }

        void track(int slot, AEItemKey key) { changedSlots.putIfAbsent(slot, key); }

        void changed(int slot) { if (!changedSlots.containsKey(slot)) unexpectedChange = true; }

        void flush() {
            try {
                for (var entry : changedSlots.entrySet()) {
                    ItemStack stack = owner.getPatternInv().getStackInSlot(entry.getKey());
                    if (!stack.isEmpty() && !entry.getValue().matches(stack)) unexpectedChange = true;
                }
                if (unexpectedChange) {
                    owner.updatePatterns();
                    return;
                }
                boolean removed = false;
                for (var entry : changedSlots.entrySet()) {
                    if (owner.getPatternInv().getStackInSlot(entry.getKey()).isEmpty()) removed |= remove(entry.getValue());
                }
                if (removed && networkRefresh) ICraftingProvider.requestUpdate(access.getMainNode());
                size = cursor = access.getPatterns().size();
            } finally {
                changedSlots.clear();
                unexpectedChange = false;
                networkRefresh = false;
            }
        }

        private boolean remove(AEItemKey key) {
            var indices = positions.get(key);
            if (indices == null || indices.isEmpty()) return false;
            int index = indices.iterator().nextInt();
            var patterns = access.getPatterns();
            var removed = patterns.get(index);
            int equivalents = equivalentPatterns.get(removed);
            if (equivalents != 1 || !PatternMigrationNetworkCatalog.removeUnique(access, index, removed, patterns.size())) {
                networkRefresh = true;
            }
            if (equivalents == 1) equivalentPatterns.remove(removed);
            else equivalentPatterns.put(removed, equivalents - 1);
            indices.remove(index);
            int last = patterns.size() - 1;
            if (index != last) {
                var moved = patterns.get(last);
                patterns.set(index, moved);
                var movedIndices = positions.get(moved.getDefinition());
                movedIndices.remove(last);
                movedIndices.add(index);
            }
            patterns.remove(last);
            for (var input : removed.getInputs()) for (var option : input.getPossibleInputs()) {
                var inputKey = option.what().dropSecondary();
                int remaining = inputs.get(inputKey) - 1;
                if (remaining == 0) {
                    inputs.remove(inputKey);
                    access.getPatternInputs().remove(inputKey);
                } else inputs.put(inputKey, remaining);
            }
            return true;
        }
    }
}
