package com.moakiee.ae2lt.logic.craft.migration;

import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.ItemStack;

/** Tracks a completed native catalog so migration can remove slots without decoding it again. */
public final class PatternMigrationProviderCatalog {
    public interface Owner {
        PatternMigrationProviderCatalog ae2lt$migrationCatalog();
    }

    private record Entry(int index, AEItemKey definition, IPatternDetails details, Set<AEKey> inputs) {}
    public record Change(InternalInventory inventory, int slot, ItemStack identity, ItemStack before, Object entry) {}

    private final InternalInventory inventory;
    private final IdentityHashMap<ItemStack, Entry> entries = new IdentityHashMap<>();
    private final Map<AEKey, Integer> inputReferences = new HashMap<>();
    private final int[] removed;
    private int size;
    private boolean ready;
    private boolean supported = true;
    private long updates;

    public PatternMigrationProviderCatalog(InternalInventory inventory) {
        this.inventory = inventory;
        removed = new int[inventory.size() + 1];
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
}
