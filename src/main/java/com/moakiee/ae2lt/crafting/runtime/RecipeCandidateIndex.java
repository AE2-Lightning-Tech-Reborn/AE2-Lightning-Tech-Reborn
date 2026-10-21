package com.moakiee.ae2lt.crafting.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Immutable coarse candidates in source order; callers still perform their original full match.
 *
 * <p>Index-then-validate design inspired by RecipeSearch / Fast Recipe Search by nutant233
 * (nutant / mutan) and GregTech-Odyssey's RecipeDB integration. This independent implementation
 * indexes an already-known pattern definition, rather than changing task order with a material
 * trie. See META-INF/recipesearch-attribution.txt.
 */
public final class RecipeCandidateIndex<K, V> {
    private final Map<K, List<V>> byKey;

    public RecipeCandidateIndex(Collection<? extends V> values, Function<? super V, ? extends K> key) {
        var building = new HashMap<K, List<V>>();
        for (var value : values) {
            var identity = key.apply(value);
            if (identity != null) building.computeIfAbsent(identity, ignored -> new ArrayList<>()).add(value);
        }
        building.replaceAll((ignored, candidates) -> List.copyOf(candidates));
        byKey = Map.copyOf(building);
    }

    public List<V> candidates(K key) {
        return key == null ? List.of() : byKey.getOrDefault(key, List.of());
    }
}
