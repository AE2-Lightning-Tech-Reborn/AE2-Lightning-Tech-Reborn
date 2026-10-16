package com.moakiee.ae2lt.logic.craft.migration;

import appeng.api.crafting.IPatternDetails;
import com.moakiee.ae2lt.mixin.PatternMigrationCraftingServiceAccessor;
import com.moakiee.ae2lt.mixin.PatternMigrationNetworkProvidersAccessor;
import com.moakiee.ae2lt.mixin.PatternMigrationProviderStateAccessor;
import com.moakiee.ae2lt.mixin.PatternProviderLogicAccessor;
import java.util.List;

final class PatternMigrationNetworkCatalog {
    private PatternMigrationNetworkCatalog() {}

    /** Native unmount is safe for one entry only when this provider has no equivalent duplicate. */
    static boolean removeUnique(PatternProviderLogicAccessor owner, int index, IPatternDetails pattern, int size) {
        var node = owner.getMainNode().getNode();
        if (node == null || !(node.getGrid().getCraftingService() instanceof PatternMigrationCraftingServiceAccessor service)) return false;
        var providers = service.ae2lt$craftingProviders();
        var access = (PatternMigrationNetworkProvidersAccessor) providers;
        var state = access.ae2lt$providerStates().get(node);
        if (!(state instanceof PatternMigrationProviderStateAccessor snapshot) || snapshot.ae2lt$provider() != owner) return false;
        var patterns = snapshot.ae2lt$patterns();
        if (patterns.size() != size || patterns.get(index) != pattern) return false;

        // Reuse native bookkeeping for outputs, provider weights and pattern indexes.
        snapshot.ae2lt$patterns(List.of(pattern));
        try { snapshot.ae2lt$unmount(providers); }
        finally { snapshot.ae2lt$patterns(patterns); }
        int last = patterns.size() - 1;
        if (index != last) patterns.set(index, patterns.get(last));
        patterns.remove(last);
        access.ae2lt$markModified();
        return true;
    }
}
