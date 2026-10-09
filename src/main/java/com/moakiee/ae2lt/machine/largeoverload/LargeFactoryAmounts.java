package com.moakiee.ae2lt.machine.largeoverload;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Checked amounts; no saturating arithmetic is allowed in ownership transfers. */
public final class LargeFactoryAmounts {
    private LargeFactoryAmounts() { }

    public static Map<AEKey, Long> flatten(KeyCounter[] counters) {
        AEKey singleKey = null;
        long singleAmount = 0;
        Map<AEKey, Long> result = null;
        for (var counter : counters) for (var entry : counter) {
            long amount = entry.getLongValue();
            if (amount < 0) throw new IllegalArgumentException("Negative input");
            if (amount == 0) continue;
            var key = entry.getKey();
            if (result != null) add(result, key, amount);
            else if (singleKey == null) { singleKey = key; singleAmount = amount; }
            else if (singleKey.equals(key)) singleAmount = Math.addExact(singleAmount, amount);
            else {
                result = new LinkedHashMap<>();
                result.put(singleKey, singleAmount);
                result.put(key, amount);
            }
        }
        return result != null ? Map.copyOf(result) : singleKey == null ? Map.of() : Map.of(singleKey, singleAmount);
    }

    public static Map<AEKey, Long> of(GenericStack[] stacks) { return of(java.util.Arrays.asList(stacks)); }

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

    /** The returned amounts are read-only and may borrow the input map when the scale is one. */
    public static Map<AEKey, Long> scale(Map<AEKey, Long> values, long copies) {
        if (copies <= 0) throw new IllegalArgumentException("Invalid copies");
        if (copies == 1 || values.isEmpty()) return values;
        if (values.size() == 1) {
            var value = values.entrySet().iterator().next();
            return Map.of(value.getKey(), Math.multiplyExact(value.getValue(), copies));
        }
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

    public static ListTag save(Map<AEKey, Long> values) {
        var list = new ListTag();
        values.forEach((key, amount) -> {
            var tag = new CompoundTag();
            tag.put("Key", key.toTagGeneric());
            tag.putLong("Amount", amount);
            list.add(tag);
        });
        return list;
    }

    public static Map<AEKey, Long> load(ListTag list) {
        var result = new LinkedHashMap<AEKey, Long>();
        for (Tag value : list) if (value instanceof CompoundTag tag) {
            var key = AEKey.fromTagGeneric(tag.getCompound("Key"));
            long amount = tag.getLong("Amount");
            if (key != null && amount > 0) add(result, key, amount);
        }
        return result;
    }
}
