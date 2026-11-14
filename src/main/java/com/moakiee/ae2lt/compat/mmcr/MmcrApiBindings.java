package com.moakiee.ae2lt.compat.mmcr;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import cn.howxu.mmcr.publicapi.compat.appliedenergistics2.Ae2Processing;
import cn.howxu.mmcr.publicapi.compat.appliedenergistics2.BatchEndpoint;
import cn.howxu.mmcr.publicapi.compat.appliedenergistics2.BatchPattern;
import com.mojang.logging.LogUtils;
import com.moakiee.ae2lt.logic.provider.ProviderTarget;
import com.moakiee.thunderbolt.api.crafting.batch.BatchJobView;
import com.moakiee.thunderbolt.api.crafting.batch.BatchProviderAdapters;
import com.moakiee.thunderbolt.api.crafting.batch.BatchProviderResolver;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;
import com.moakiee.thunderbolt.api.crafting.batch.PreparedBatch;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Typed MMCR consumer. Admission, input ownership and start proof are owned by the public native API. */
final class MmcrApiBindings {
    private MmcrApiBindings() { }

    static boolean initialize() {
        if (Ae2Processing.apiVersion() != 1) return false;
        BatchProviderAdapters.register(ResourceLocation.fromNamespaceAndPath("ae2lt", "mmcr"), 100,
                new Resolver());
        Ae2Processing.registerPlanNormalizer(ResourceLocation.fromNamespaceAndPath("ae2lt", "mmcr_unit_tasks"),
                Ae2Processing::unitCrafts);
        return true;
    }

    static boolean isBatchPattern(IPatternDetails pattern) {
        return pattern instanceof BatchPattern;
    }

    private static final class Resolver implements BatchProviderResolver {
        @Override
        public @Nullable IBatchCraftingProvider resolve(ICraftingProvider provider) {
            try {
                var nativeEndpoint = Ae2Processing.endpoint(provider);
                if (nativeEndpoint == null || !(nativeEndpoint.host().getLevel() instanceof ServerLevel level)) return null;
                return new Endpoint(provider, nativeEndpoint, level);
            } catch (RuntimeException | LinkageError unavailable) {
                return null;
            }
        }

        @Override public boolean cacheResolutionAcrossTicks() { return true; }
    }

    private static final class Endpoint implements IBatchCraftingProvider {
        private final ICraftingProvider provider;
        private final BatchEndpoint nativeEndpoint;
        private final BlockEntity host;
        private final ServerLevel level;
        private final ProviderTarget target;
        private final Map<AEItemKey, IPatternDetails> canonical = new LinkedHashMap<>();
        private boolean stopped;

        private Endpoint(ICraftingProvider provider, BatchEndpoint nativeEndpoint, ServerLevel level) {
            this.provider = provider;
            this.nativeEndpoint = nativeEndpoint;
            host = nativeEndpoint.host();
            this.level = level;
            target = new ProviderTarget(level.dimension(), host.getBlockPos(), Direction.UP);
        }

        @Override public List<IPatternDetails> getAvailablePatterns() { return provider.getAvailablePatterns(); }
        @Override public boolean isBusy() { return stopped || provider.isBusy() || !current(); }

        @Override
        public long getBatchCapacity(IPatternDetails pattern) {
            if (!isBatchPattern(pattern) || isBusy()) return 0L;
            try {
                // A >1 advisory bound opts in even while the adaptive first proof is just one copy.
                return nativeEndpoint.capacity(pattern).maximumCopies();
            } catch (RuntimeException | LinkageError unavailable) {
                return 0L;
            }
        }

        @Override
        public PreparedBatch prepareBatch(IPatternDetails pattern, KeyCounter[] prototype,
                long maxCraft, BatchJobView job) {
            if (!isBatchPattern(pattern) || isBusy() || prototype == null) return unavailable();
            var capacity = available(pattern);
            if (capacity <= 0L) return unavailable();
            var key = canonical(pattern);
            int offer = target.startBatchOffer(key, Math.min(maxCraft, capacity));
            var borrowed = copy(prototype, 1L);
            return new PreparedBatch() {
                private boolean used;
                @Override public long capacity() { return offer; }
                @Override public long push(long copies) {
                    if (used) throw new IllegalStateException("MMCR prepared start already submitted");
                    used = true;
                    if (copies <= 0L) return 0L;
                    if (copies > offer) throw new IllegalArgumentException("MMCR offer exceeds prepared input slice");
                    return dispatch(pattern, key, borrowed, copies);
                }
            };
        }

        @Override
        public long pushBatch(IPatternDetails pattern, KeyCounter[] prototype, long copies) {
            if (copies <= 0L) return 0L;
            if (!isBatchPattern(pattern) || prototype == null || isBusy()) return copies;
            return dispatch(pattern, canonical(pattern), prototype, copies);
        }

        private long dispatch(IPatternDetails pattern, IPatternDetails key, KeyCounter[] prototype, long copies) {
            if (isBusy() || copies > available(pattern) || copies > target.startBatchOffer(key, copies)) return copies;
            var pause = new boolean[1];
            var result = target.pushStartPattern(key, copies,
                    () -> pause[0] || isBusy() || available(pattern) <= 0L,
                    offered -> {
                        KeyCounter[] totalInputs;
                        try { totalInputs = copy(prototype, offered); }
                        catch (RuntimeException invalidInputs) {
                            pause[0] = true;
                            return ProviderTarget.BatchChunk.REJECTED;
                        }
                        // Only the single prepared packet is at risk once this native call begins.
                        try {
                            var receipt = nativeEndpoint.submit(pattern, totalInputs, offered);
                            if (receipt.ownership() == BatchEndpoint.Ownership.INDETERMINATE) {
                                throw new IllegalStateException("MMCR native start has indeterminate input ownership");
                            }
                            if (receipt.ownership() == BatchEndpoint.Ownership.REJECTED) {
                                pause[0] = receipt.reason() != BatchEndpoint.StopReason.CAPACITY;
                                return ProviderTarget.BatchChunk.REJECTED;
                            }
                            if (receipt.acceptedCopies() != offered) {
                                throw new IllegalStateException("MMCR returned an invalid whole-packet ownership count");
                            }
                            boolean complete = receipt.startedCopies() == offered;
                            if (!complete) {
                                stopped = true;
                                LogUtils.getLogger().warn("AE2LT stopped MMCR batch dispatch at {}: inputs were accepted "
                                        + "but a complete native start was not confirmed", host.getBlockPos());
                            }
                            // Accepted ownership is settled once. Only complete-start proof grows the next visit.
                            return new ProviderTarget.BatchChunk(receipt.acceptedCopies(), complete, false);
                        } catch (RuntimeException | LinkageError failure) {
                            stopped = true;
                            throw new IllegalStateException("MMCR native submission has uncertain input ownership", failure);
                        }
                    });
            return copies - result.ownedCopies();
        }

        private long available(IPatternDetails pattern) {
            if (isBusy()) return 0L;
            try { return nativeEndpoint.capacity(pattern).availableCopies(); }
            catch (RuntimeException | LinkageError unavailable) { return 0L; }
        }

        private IPatternDetails canonical(IPatternDetails pattern) {
            if (canonical.size() >= 1024 && !canonical.containsKey(pattern.getDefinition())) {
                canonical.clear();
                target.clearRuntimeState();
            }
            return canonical.computeIfAbsent(pattern.getDefinition(), ignored -> pattern);
        }

        private boolean current() {
            return !host.isRemoved() && host.getLevel() == level && level.getServer().isSameThread()
                    && level.isLoaded(host.getBlockPos()) && level.getBlockEntity(host.getBlockPos()) == host;
        }
    }

    private static PreparedBatch unavailable() {
        return new PreparedBatch() {
            @Override public long capacity() { return 0L; }
            @Override public long push(long copies) { return copies; }
        };
    }

    private static KeyCounter[] copy(KeyCounter[] source, long multiplier) {
        var result = new KeyCounter[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = new KeyCounter();
            for (var entry : source[i]) {
                result[i].add(entry.getKey(), Math.multiplyExact(entry.getLongValue(), multiplier));
            }
        }
        return result;
    }
}
