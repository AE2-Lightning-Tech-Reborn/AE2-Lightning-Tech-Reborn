package com.moakiee.ae2lt.compat.gto.jei;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.ingredients.IIngredientType;
import org.jetbrains.annotations.Nullable;

/**
 * Registry scoped to the adapted Useless integration. AE2's legacy JEI registry contract is
 * retained, including type-identity duplicate rejection and ingredient-class fallback.
 * Adapted from Applied Energistics 2, TeamAppliedEnergistics, LGPL-3.0;
 * see META-INF/ae2-legacy-jei-attribution.txt.
 */
public final class GtoIngredientConverters {
    private record Snapshot(List<GtoIngredientConverter<?>> converters,
            Map<IIngredientType<?>, GtoIngredientConverter<?>> byType) { }
    private static volatile Snapshot snapshot = new Snapshot(List.of(), Map.of());

    private GtoIngredientConverters() { }

    public static synchronized boolean register(GtoIngredientConverter<?> converter) {
        for (var existing : snapshot.converters()) {
            if (existing.getIngredientType() == converter.getIngredientType()) return false;
        }
        var next = new ArrayList<>(snapshot.converters());
        next.add(converter);
        var index = new HashMap<IIngredientType<?>, GtoIngredientConverter<?>>();
        for (var entry : next) {
            if (index.putIfAbsent(entry.getIngredientType(), entry) != null) {
                throw new IllegalStateException("Ambiguous legacy JEI ingredient type: " + entry.getIngredientType());
            }
        }
        snapshot = new Snapshot(List.copyOf(next), Map.copyOf(index));
        return true;
    }

    public static List<GtoIngredientConverter<?>> getConverters() { return snapshot.converters(); }

    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> GtoIngredientConverter<T> getConverter(IIngredientType<T> type) {
        var current = snapshot;
        var result = current.byType().get(type);
        if (result == null) {
            for (var entry : current.converters()) {
                if (entry.getIngredientType().getIngredientClass() == type.getIngredientClass()) {
                    return (GtoIngredientConverter<T>) entry;
                }
            }
        }
        return (GtoIngredientConverter<T>) result;
    }
}
