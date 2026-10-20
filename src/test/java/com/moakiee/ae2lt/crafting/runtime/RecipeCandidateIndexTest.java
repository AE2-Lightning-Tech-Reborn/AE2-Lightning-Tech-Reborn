package com.moakiee.ae2lt.crafting.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RecipeCandidateIndexTest {
    private record Key(int id) {
        @Override public int hashCode() { return 7; }
    }
    private record Candidate(Key key, String name) {}

    @Test
    void preservesCandidateOrderDuplicatesAndExactEqualityDespiteHashCollisions() {
        var first = new Candidate(new Key(1), "first");
        var second = new Candidate(new Key(1), "second");
        var other = new Candidate(new Key(2), "other");
        var source = new ArrayList<>(List.of(first, other, second, first, new Candidate(null, "unknown")));
        var index = new RecipeCandidateIndex<>(source, Candidate::key);
        source.clear();
        assertEquals(List.of(first, second, first), index.candidates(new Key(1)));
        assertEquals(List.of(other), index.candidates(new Key(2)));
        assertTrue(index.candidates(null).isEmpty());
        assertTrue(index.candidates(new Key(3)).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> index.candidates(new Key(1)).clear());
    }

    @Test
    void repeatedQueriesNeverRescanTheCatalogAndRebuildDoesNotMutateOldSnapshots() {
        var calls = new AtomicInteger();
        var source = new ArrayList<Integer>();
        for (int n = 0; n < 10_000; n++) source.add(n);
        var index = new RecipeCandidateIndex<>(source, n -> { calls.incrementAndGet(); return n; });
        for (int n = 0; n < 16_384; n++) assertEquals(List.of(7), index.candidates(7));
        assertEquals(10_000, calls.get());
        source.remove(Integer.valueOf(7));
        var rebuilt = new RecipeCandidateIndex<>(source, n -> n);
        assertTrue(rebuilt.candidates(7).isEmpty());
        assertEquals(List.of(7), index.candidates(7));
    }
}
