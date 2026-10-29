package com.moakiee.ae2lt.compat.gto;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import org.junit.jupiter.api.Test;

class GtoKeyCounterEntriesTest {
    @Test
    void legacyAndNativeReadersShareExactValuesAndWriteThroughMutations() {
        var key = new String("identity key");
        var equalKey = new String("identity key");
        var map = new Reference2LongOpenHashMap<String>();
        map.put(key, Long.MAX_VALUE);
        map.put(equalKey, 37L);
        var iterator = GtoKeyCounterEntries.iterator(map.reference2LongEntrySet().iterator());
        while (iterator.hasNext()) {
            var nativeEntry = iterator.next();
            var legacy = assertInstanceOf(Object2LongMap.Entry.class, nativeEntry);
            assertSame(nativeEntry.getKey(), legacy.getKey());
            assertEquals(map.getLong(nativeEntry.getKey()), legacy.getLongValue());
            if (nativeEntry.getKey() == key) {
                assertEquals(Long.MAX_VALUE, legacy.setValue(Long.MIN_VALUE));
                assertEquals(Long.MIN_VALUE, map.getLong(key));
                assertEquals(Long.valueOf(Long.MIN_VALUE), ((Map.Entry<?, ?>) legacy).getValue());
            } else {
                iterator.remove();
            }
        }
        assertEquals(1, map.size());
        assertTrue(map.containsKey(key));
        assertFalse(map.containsKey(equalKey));
    }

    @Test
    void nativeFastForEachCanCallALegacyCompiledConsumer() {
        var map = new Reference2LongOpenHashMap<String>();
        map.put("first", Long.MAX_VALUE);
        map.put("second", -37L);
        var actual = new ArrayList<Long>();
        Consumer<Object> legacy = value -> actual.add(((Object2LongMap.Entry<?>) value).getLongValue());
        map.reference2LongEntrySet().fastForEach(GtoKeyCounterEntries.<String>consumer(legacy));
        assertEquals(2, actual.size());
        assertTrue(actual.containsAll(java.util.List.of(Long.MAX_VALUE, -37L)));
    }

    @Test
    void adaptedEntriesRemainUsableByNativeConsumers() {
        var map = new Reference2LongOpenHashMap<String>();
        map.put("native", 42L);
        var seen = new ArrayList<Long>();
        Consumer<Reference2LongMap.Entry<String>> nativeReader = value -> seen.add(value.getLongValue());
        GtoKeyCounterEntries.iterator(map.reference2LongEntrySet().iterator()).forEachRemaining(nativeReader);
        assertEquals(java.util.List.of(42L), seen);
    }
}
