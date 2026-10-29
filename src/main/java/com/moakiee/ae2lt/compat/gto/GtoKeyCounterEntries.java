package com.moakiee.ae2lt.compat.gto;

import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;

/** Binary-compatible entry views for addons compiled against AE2's object-key counters. */
public final class GtoKeyCounterEntries {
    private GtoKeyCounterEntries() { }

    public static <K> Iterator<Reference2LongMap.Entry<K>> iterator(
            Iterator<Reference2LongMap.Entry<K>> original) {
        return new Iterator<>() {
            @Override public boolean hasNext() { return original.hasNext(); }
            @Override public Reference2LongMap.Entry<K> next() { return entry(original.next()); }
            @Override public void remove() { original.remove(); }
        };
    }

    public static <K> Consumer<Reference2LongMap.Entry<K>> consumer(
            Consumer<? super Reference2LongMap.Entry<K>> original) {
        Objects.requireNonNull(original);
        return value -> original.accept(entry(value));
    }

    private static <K> Reference2LongMap.Entry<K> entry(Reference2LongMap.Entry<K> original) {
        return original instanceof Object2LongMap.Entry<?> ? original : new CompatibleEntry<>(original);
    }

    // Preserve identity keys, exact long values and write-through behavior. A detached BasicEntry
    // would silently lose setValue changes; replacing the backing map would change key semantics.
    private record CompatibleEntry<K>(Reference2LongMap.Entry<K> original)
            implements Reference2LongMap.Entry<K>, Object2LongMap.Entry<K> {
        @Override public K getKey() { return original.getKey(); }
        @Override public long getLongValue() { return original.getLongValue(); }
        @Override public long setValue(long value) { return original.setValue(value); }
        @Override public Long getValue() { return getLongValue(); }
        @Override public Long setValue(Long value) { return setValue(value.longValue()); }
        @Override public boolean equals(Object other) { return original.equals(other); }
        @Override public int hashCode() { return original.hashCode(); }
        @Override public String toString() { return original.toString(); }
    }
}
