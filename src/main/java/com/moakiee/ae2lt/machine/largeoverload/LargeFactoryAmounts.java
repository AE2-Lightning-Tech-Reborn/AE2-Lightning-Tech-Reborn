package com.moakiee.ae2lt.machine.largeoverload;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Checked amounts; no saturating arithmetic is allowed in ownership transfers. */
public final class LargeFactoryAmounts {
    private LargeFactoryAmounts() { }

    public static Map<AEKey, Long> flatten(KeyCounter[] counters) {
        var result = new LinkedHashMap<AEKey, Long>();
        for (var counter : counters) for (var entry : counter) {
            if (entry.getLongValue() < 0) throw new IllegalArgumentException("Negative input");
            if (entry.getLongValue() > 0) add(result, entry.getKey(), entry.getLongValue());
        }
        return Map.copyOf(result);
    }

    public static Map<AEKey, Long> of(List<GenericStack> stacks) {
        var result = new LinkedHashMap<AEKey, Long>();
        for (var stack : stacks) {
            if (stack == null || stack.amount() <= 0) throw new IllegalArgumentException("Invalid stack");
            add(result, stack.what(), stack.amount());
        }
        return Map.copyOf(result);
    }

    public static void add(Map<AEKey, Long> into, AEKey key, long amount) {
        if (key == null || amount <= 0) throw new IllegalArgumentException("Invalid amount");
        into.merge(key, amount, Math::addExact);
    }

    public static Map<AEKey, Long> scale(Map<AEKey, Long> values, long copies) {
        if (copies <= 0) throw new IllegalArgumentException("Invalid copies");
        var result = new LinkedHashMap<AEKey, Long>();
        values.forEach((key, amount) -> result.put(key, Math.multiplyExact(amount, copies)));
        return result;
    }

    public static KeyCounter counter(Map<AEKey, Long> values) {
        var result = new KeyCounter();
        values.forEach(result::add);
        return result;
    }

    public static List<GenericStack> stacks(Map<AEKey, Long> values) {
        return values.entrySet().stream().map(e -> new GenericStack(e.getKey(), e.getValue())).toList();
    }

    public static ListTag save(Map<AEKey, Long> values, HolderLookup.Provider registries) {
        var list = new ListTag();
        values.forEach((key, amount) -> {
            var tag = new CompoundTag();
            tag.put("Key", key.toTagGeneric(registries));
            tag.putLong("Amount", amount);
            list.add(tag);
        });
        return list;
    }

    public static Map<AEKey, Long> load(ListTag list, HolderLookup.Provider registries) {
        var result = new LinkedHashMap<AEKey, Long>();
        for (Tag value : list) if (value instanceof CompoundTag tag) {
            var key = AEKey.fromTagGeneric(registries, tag.getCompound("Key"));
            long amount = tag.getLong("Amount");
            if (key != null && amount > 0) add(result, key, amount);
        }
        return result;
    }
}
