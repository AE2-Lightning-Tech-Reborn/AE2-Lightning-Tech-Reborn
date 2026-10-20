package com.moakiee.ae2lt.crafting.big;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.moakiee.ae2lt.crafting.runtime.RecipeCandidateIndex;
import com.moakiee.thunderbolt.core.crafting.support.CraftingProviderRevision;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/** CPU-local, queried-output-only indexes. A mutation revision, never a tick/hash, proves validity. */
final class BigCraftingPatternLookup {
    private static final int MAX_OUTPUT_BUCKETS = 64;
    private ICraftingService owner;
    private long revision;
    private final Map<AEKey, RecipeCandidateIndex<AEItemKey, IPatternDetails>> buckets = new HashMap<>();

    IPatternDetails resolve(BigCraftingRecipe recipe, ICraftingService crafting, Level level) {
        if (!(crafting instanceof CraftingProviderRevision tracked)) {
            clear();
            return recipe.resolve(crafting, level);
        }
        long current = tracked.thunderbolt$getCraftingProviderRevision();
        if (owner != crafting || revision != current) {
            buckets.clear();
            owner = crafting;
            revision = current;
        }
        var output = recipe.pattern().output();
        var index = buckets.get(output);
        if (index == null) {
            if (buckets.size() >= MAX_OUTPUT_BUCKETS) buckets.clear();
            index = new RecipeCandidateIndex<>(crafting.getCraftingFor(output), IPatternDetails::getDefinition);
            buckets.put(output, index);
        }
        for (var candidate : index.candidates(recipe.definition())) {
            // Conditions, concrete containers and dynamic matching remain authoritative on every visit.
            if (recipe.matches(candidate, level)) return candidate;
        }
        return null;
    }

    void clear() {
        owner = null;
        buckets.clear();
    }
}
