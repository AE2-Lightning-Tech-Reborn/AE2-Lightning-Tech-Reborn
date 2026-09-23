package com.moakiee.ae2lt.logic.tianshu.maintenance;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.IntSupplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class InventoryMaintenanceRepository {
    private static final String TAG_RULES = "Rules";
    private final IntSupplier capacity;
    private final LinkedHashMap<AEKey, InventoryMaintenanceRule> rules = new LinkedHashMap<>();

    public InventoryMaintenanceRepository(IntSupplier capacity) { this.capacity = capacity; }
    public int capacity() { return Math.max(0, capacity.getAsInt()); }
    public int size() { return rules.size(); }
    public List<InventoryMaintenanceRule> rules() { return List.copyOf(rules.values()); }
    /**
     * Returns a stable, bounded prefix for diagnostics and recovery UIs.
     *
     * <p>This deliberately remains available when legacy persisted state exceeds the
     * current capacity, so players can delete visible entries until the repository is
     * writable again without ever serializing an unbounded network payload.</p>
     */
    public List<InventoryMaintenanceRule> rules(int limit) {
        if (limit <= 0) return List.of();
        return rules.values().stream().limit(limit).toList();
    }
    /** Rules currently backed by installed maintenance-core capacity. Overflow stays persisted. */
    public List<InventoryMaintenanceRule> activeRules() {
        if (rules.size() > capacity()) return List.of();
        return rules.values().stream().limit(capacity()).toList();
    }
    public InventoryMaintenanceRule get(AEKey key) { return rules.get(key); }
    public InventoryMaintenanceRule getById(UUID id) {
        if (id == null) return null;
        for (var rule : rules.values()) if (id.equals(rule.id())) return rule;
        return null;
    }

    public PutResult put(InventoryMaintenanceRule rule) {
        if (rule == null) return PutResult.INVALID;
        // Oversized legacy state remains persisted and removable, but cannot be mutated
        // until enough entries have been deleted to return within the supported bound.
        if (rules.size() > capacity()) return PutResult.FULL;
        if (!rules.containsKey(rule.key()) && rules.size() >= capacity()) {
            return capacity() <= 0 ? PutResult.UNAVAILABLE : PutResult.FULL;
        }
        boolean update = rules.containsKey(rule.key());
        rules.put(rule.key(), rule);
        return update ? PutResult.UPDATED : PutResult.ADDED;
    }

    public boolean remove(AEKey key) { return rules.remove(key) != null; }

    public void writeTo(CompoundTag parent, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var rule : rules.values()) {
            var tag = new CompoundTag();
            com.moakiee.ae2lt.recipe.compat.LegacyNbtUuid.put(tag, "Id", rule.id());
            tag.put("Key", com.moakiee.ae2lt.recipe.compat.LegacyAeStackTags.writeGeneric(registries, new GenericStack(rule.key(), 1)));
            tag.putLong("Lower", rule.lowerThreshold());
            tag.putLong("Upper", rule.upperThreshold());
            tag.putLong("PerJob", rule.amountPerJob());
            tag.putBoolean("Enabled", rule.enabled());
            tag.putBoolean("Replenishing", rule.replenishing());
            if (rule.activeCraftingId() != null) com.moakiee.ae2lt.recipe.compat.LegacyNbtUuid.put(tag, "CraftingId", rule.activeCraftingId());
            list.add(tag);
        }
        parent.put(TAG_RULES, list);
    }

    public void readFrom(CompoundTag parent, HolderLookup.Provider registries) {
        rules.clear();
        var list = parent.getListOrEmpty(TAG_RULES);
        for (int i = 0; i < list.size(); i++) {
            try {
                var tag = list.getCompoundOrEmpty(i);
                var keyStack = com.moakiee.ae2lt.recipe.compat.LegacyAeStackTags.readGeneric(registries, tag.getCompoundOrEmpty("Key"));
                if (keyStack == null || !com.moakiee.ae2lt.recipe.compat.LegacyNbtUuid.has(tag, "Id")) continue;
                var rule = new InventoryMaintenanceRule(
                        com.moakiee.ae2lt.recipe.compat.LegacyNbtUuid.get(tag, "Id"), keyStack.what(), tag.getLongOr("Lower", 0L), tag.getLongOr("Upper", 0L),
                        tag.getLongOr("PerJob", 0L), tag.getBooleanOr("Enabled", false), tag.getBooleanOr("Replenishing", false),
                        com.moakiee.ae2lt.recipe.compat.LegacyNbtUuid.has(tag, "CraftingId") ? com.moakiee.ae2lt.recipe.compat.LegacyNbtUuid.get(tag, "CraftingId") : null);
                rules.put(rule.key(), rule);
            } catch (RuntimeException ignored) {
            }
        }
    }

    public enum PutResult { ADDED, UPDATED, FULL, UNAVAILABLE, INVALID }
}
