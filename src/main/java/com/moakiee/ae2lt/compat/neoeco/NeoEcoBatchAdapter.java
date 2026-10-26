package com.moakiee.ae2lt.compat.neoeco;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import com.moakiee.thunderbolt.api.crafting.batch.BatchJobView;
import com.moakiee.thunderbolt.api.crafting.batch.BatchProviderResolver;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;
import com.moakiee.thunderbolt.api.crafting.batch.PreparedBatch;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Tianshu retains extraction, energy and output accounting; ECO accepts only the admitted copies. */
final class NeoEcoBatchAdapter implements BatchProviderResolver {
    private final NeoEcoBatchApi api;
    NeoEcoBatchAdapter(NeoEcoBatchApi api) { this.api = api; }

    @Override public boolean cacheResolutionAcrossTicks() { return true; }

    @Override public @Nullable IBatchCraftingProvider resolve(ICraftingProvider provider) {
        return api.providerType.isInstance(provider) ? new Adapted(provider) : null;
    }

    private final class Adapted implements IBatchCraftingProvider {
        private final ICraftingProvider provider;
        private final Set<IPatternDetails> ordinary = Collections.newSetFromMap(new IdentityHashMap<>());
        private long tick = Long.MIN_VALUE;
        private boolean unavailable;
        private Adapted(ICraftingProvider provider) { this.provider = provider; }

        @Override public void beginDispatchTick(long tick) {
            if (this.tick != tick) { this.tick = tick; ordinary.clear(); }
        }
        @Override public List<IPatternDetails> getAvailablePatterns() { return provider.getAvailablePatterns(); }
        @Override public boolean isBusy() { return provider.isBusy(); }
        @Override public long getBatchCapacity(IPatternDetails details) {
            return isBusy() ? 0 : unavailable || ordinary.contains(details) ? 1 : Long.MAX_VALUE;
        }
        @Override public long pushBatch(IPatternDetails details, KeyCounter[] inputs, long offered) {
            // No job identity: leave custody with the caller.
            return Math.max(0, offered);
        }
        @Override public long pushBatch(IPatternDetails details, KeyCounter[] inputs, long offered, BatchJobView job) {
            if (offered <= 0) return 0;
            UUID jobId = job.craftingId(); // Job views are borrowed and may be rebound after this call.
            if (jobId == null) return offered;
            var prepared = prepare(details, inputs, offered, job.level(), jobId);
            return submit(details, inputs, offered, jobId, prepared);
        }

        @Override public @Nullable PreparedBatch prepareBatch(IPatternDetails details, KeyCounter[] inputs,
                long offered, BatchJobView job) {
            if (offered <= 0 || job.craftingId() == null) return null;
            UUID jobId = job.craftingId();
            var level = job.level();
            var template = copy(inputs);
            var prepared = prepare(details, template, offered, level, jobId);
            long capacity = prepared == null ? 1 : prepared.crafts;
            return new PreparedBatch() {
                @Override public long capacity() { return capacity; }
                @Override public long push(long count) {
                    if (count <= 0) return 0;
                    if (count > capacity) throw new IllegalArgumentException("NeoECO allocation exceeds admission");
                    var bounded = prepared != null && count == capacity ? prepared
                            : prepared == null ? null : prepare(details, template, count, level, jobId);
                    return submit(details, template, count, jobId, bounded);
                }
            };
        }

        private NeoEcoBatchApi.Prepared prepare(IPatternDetails details, KeyCounter[] inputs,
                long offered, net.minecraft.world.level.Level level, UUID jobId) {
            NeoEcoBatchApi.Prepared prepared = null;
            if (!unavailable) {
                try { prepared = api.prepare(provider, details, copy(inputs), offered, level, jobId); }
                catch (RuntimeException | LinkageError preparationFailure) {
                    // Preparation is read-only. Unlike a thrown submission, this still permits safe fallback.
                    unavailable = true;
                    appeng.core.AELog.warn("[ae2lt] NeoECO preparation unavailable; retaining ordinary dispatch. %s", preparationFailure);
                }
            }
            return prepared;
        }

        private long submit(IPatternDetails details, KeyCounter[] inputs, long offered,
                UUID jobId, NeoEcoBatchApi.Prepared prepared) {
            if (prepared != null && prepared.submit()) return offered - prepared.crafts;
            ordinary.add(details);
            // Null/false transfers no resources. Never catch submission exceptions here:
            // the executor's ambiguous-ownership path must stop dispatch instead of refunding.
            return api.pushSingle(provider, details, copy(inputs), jobId) ? offered - 1 : offered;
        }
    }

    private static KeyCounter[] copy(KeyCounter[] inputs) {
        KeyCounter[] result = new KeyCounter[inputs.length];
        for (int i = 0; i < inputs.length; i++) { result[i] = new KeyCounter(); result[i].addAll(inputs[i]); }
        return result;
    }
}
