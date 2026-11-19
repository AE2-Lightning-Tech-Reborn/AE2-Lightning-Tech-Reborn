package com.moakiee.ae2lt.logic.craft;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.MapMaker;
import com.moakiee.thunderbolt.core.crafting.support.CraftingPatternDelegates;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;

/** The patterns advertised by one physical warehouse, with AE2-compatible lookup semantics. */
public final class MatrixWarehousePatternCatalog {
    private List<IPatternDetails> patterns = List.of();
    private final Map<IPatternDetails, IPatternDetails> registeredByEquality = new HashMap<>();
    private final Map<AEItemKey, IPatternDetails> registeredByDefinition = new HashMap<>();
    // CPU wrappers are transient. A weak identity cache keeps their equality work off the hot path.
    private final Map<IPatternDetails, IPatternDetails> resolvedByIdentity = new MapMaker()
            .weakKeys()
            .weakValues()
            .concurrencyLevel(1)
            .makeMap();

    public void replace(List<IPatternDetails> currentPatterns) {
        patterns = List.copyOf(currentPatterns);
        registeredByEquality.clear();
        registeredByDefinition.clear();
        resolvedByIdentity.clear();
        for (var details : patterns) {
            registeredByEquality.putIfAbsent(details, details);
            if (details.getDefinition() != null) {
                registeredByDefinition.putIfAbsent(details.getDefinition(), details);
            }
            resolvedByIdentity.put(details, details);
        }
    }

    public List<IPatternDetails> patterns() {
        return patterns;
    }

    public IPatternDetails byDefinition(AEItemKey definition) {
        return registeredByDefinition.get(definition);
    }

    public boolean contains(IPatternDetails executionDetails) {
        if (executionDetails == null) return false;
        if (resolvedByIdentity.get(executionDetails) != null) return true;
        var providerDetails = CraftingPatternDelegates.forProviderLookup(executionDetails);
        var registered = resolvedByIdentity.get(providerDetails);
        if (registered == null) {
            registered = registeredByEquality.get(providerDetails);
        }
        if (registered == null) return false;
        resolvedByIdentity.put(providerDetails, registered);
        if (providerDetails != executionDetails) {
            resolvedByIdentity.put(executionDetails, registered);
        }
        return true;
    }
}
