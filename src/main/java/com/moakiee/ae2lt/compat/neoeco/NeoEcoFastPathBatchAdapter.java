package com.moakiee.ae2lt.compat.neoeco;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;

import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;

import com.moakiee.thunderbolt.api.crafting.batch.BatchJobView;
import com.moakiee.thunderbolt.api.crafting.batch.BatchProviderResolver;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;

/** Tianshu-only bridge from its allocated batch contract to NeoECO's public FastPath API. */
public final class NeoEcoFastPathBatchAdapter implements BatchProviderResolver {
    @Override
    public @Nullable IBatchCraftingProvider resolve(ICraftingProvider provider) {
        return ECOFastPathFacade.supports(provider) ? new AdaptedProvider(provider) : null;
    }

    private record AdaptedProvider(ICraftingProvider delegate)
            implements IBatchCraftingProvider {
        @Override
        public java.util.List<IPatternDetails> getAvailablePatterns() {
            return delegate.getAvailablePatterns();
        }

        @Override
        public boolean isBusy() {
            return delegate.isBusy();
        }

        @Override
        public long getBatchCapacity(IPatternDetails details) {
            // NeoECO performs the authoritative recipe, cache, coolant and lane checks while
            // preparing the concrete allocated batch below.
            return delegate.isBusy() ? 0L : Long.MAX_VALUE;
        }

        @Override
        public long pushBatch(IPatternDetails details, KeyCounter[] oneCopy, long maxCraft) {
            // Allocated FastPath requires a current job. Leave ownership with context-free callers.
            return Math.max(0L, maxCraft);
        }

        @Override
        public long pushBatch(IPatternDetails details, KeyCounter[] oneCopy, long maxCraft, BatchJobView job) {
            if (maxCraft <= 0L) {
                return 0L;
            }
            var prepared = ECOFastPathFacade.prepareAllocated(
                    delegate, details, oneCopy, maxCraft, job.level(), job.craftingId());
            if (prepared == null) {
                return maxCraft;
            }

            long offered = prepared.craftCount();
            try {
                boolean accepted = prepared.submit(amount -> new ECOFastPathFacade.Reservation() {
                    @Override
                    public void commit() {
                    }

                    @Override
                    public void refund() {
                    }
                });
                return accepted ? maxCraft - offered : maxCraft;
            } catch (ECOIndeterminateBatchException uncertain) {
                // Ownership may already have crossed the API boundary. Account the prepared part
                // as dispatched so BatchExecutor cannot refund it and duplicate the materials.
                appeng.core.AELog.error(
                        "[ae2lt] NeoECO FastPath acceptance is indeterminate; retaining Tianshu output accounting for %d crafts.",
                        offered,
                        uncertain);
                return maxCraft - offered;
            }
        }
    }
}
