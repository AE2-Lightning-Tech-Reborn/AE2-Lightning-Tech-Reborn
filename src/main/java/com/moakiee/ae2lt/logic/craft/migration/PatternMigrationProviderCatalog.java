package com.moakiee.ae2lt.logic.craft.migration;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.moakiee.ae2lt.mixin.PatternProviderLogicAccessor;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.item.ItemStack;

/** Indexes the existing native catalog in slices; removal never decodes the remaining inventory. */
final class PatternMigrationProviderCatalog {
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

    PatternMigrationProviderCatalog(PatternProviderLogic owner) {
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
