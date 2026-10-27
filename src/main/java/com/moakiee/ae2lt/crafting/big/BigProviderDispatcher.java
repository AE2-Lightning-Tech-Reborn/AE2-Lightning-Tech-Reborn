package com.moakiee.ae2lt.crafting.big;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.*;
import appeng.crafting.inv.ListCraftingInventory;
import com.moakiee.ae2lt.crafting.runtime.CraftingDispatch;
import com.moakiee.ae2lt.crafting.runtime.api.DeferredCraftingProvider;
import com.moakiee.thunderbolt.api.crafting.batch.*;
import com.moakiee.thunderbolt.core.crafting.batch.BatchCopyLimitPattern;
import com.moakiee.ae2lt.compat.gto.BigLongSlice;
import com.moakiee.thunderbolt.core.storage.big.BigAmounts;
import net.minecraft.world.level.Level;
import java.math.BigInteger;
import java.util.*;

/** Executes one bounded native call. Accepted inputs leave escrow; outputs must physically return. */
final class BigProviderDispatcher {
    private final Map<ICraftingProvider, Map<BatchProviderResolver, IBatchCraftingProvider>> adapters = new IdentityHashMap<>();
    private final Set<IBatchCraftingProvider> started = Collections.newSetFromMap(new IdentityHashMap<>());
    private long adapterTick = Long.MIN_VALUE;

    long dispatch(BigCraftingJob job, BigProviderRecipe recipe, IPatternDetails details,
            ICraftingProvider provider, Level level, IEnergyService energy, long tick, Runnable changed) {
        if (job.dispatchFailed || job.returning || job.remainingStepCopies().signum() <= 0) return 0;
        try (var transaction = new CraftingDispatch((key, amount) -> {
            long received = job.receive(key, amount, Actionable.MODULATE);
            job.retainOutput(key, BigInteger.valueOf(amount - received));
            changed.run();
        }, (key, amount) -> {
            job.retainOutput(key, BigInteger.valueOf(amount));
            changed.run();
        })) {
            return dispatch(job, recipe, details, provider, level, energy, tick, changed, transaction);
        }
    }

    private long dispatch(BigCraftingJob job, BigProviderRecipe recipe, IPatternDetails details,
            ICraftingProvider provider, Level level, IEnergyService energy, long tick, Runnable changed,
            CraftingDispatch transaction) {
        var view = new View(job, details, level, transaction);
        Long exactResult = dispatchNeoEcoExact(job, recipe, details, provider, level, energy, changed, transaction);
        if (exactResult != null) return exactResult;
        IBatchCraftingProvider batch;
        long capacity;
        boolean shared;
        try {
            batch = resolve(provider, details, view, tick);
            capacity = batch == null ? (provider.isBusy() ? 0 : 1) : batch.getBatchCapacity(details);
            if (details instanceof BatchCopyLimitPattern limit)
                capacity = Math.min(capacity, Math.max(1, limit.maxBatchCopies()));
            shared = batch != null && capacity > 1 && batch.supportsSharedBatchInputs();
        } catch (RuntimeException unavailable) {
            return 0; // No ownership has changed; another provider can still accept the pattern.
        }
        if (capacity <= 0) return 0;
        boolean batchCall = batch != null;
        var fixedInputs = shared ? recipe.sharedInputs() : Map.<AEKey, Long>of();
        var fixedOutputs = shared ? recipe.sharedOutputs() : Map.<AEKey, Long>of();
        var slice = BigLongSlice.plan(job.remainingStepCopies(), job.escrow,
                recipe.inputs(), fixedInputs, recipe.outputs(), fixedOutputs, capacity);
        if (slice.copies() <= 0) return 0;
        double wantedPower = power(slice.inputs());
        double availablePower = energy.extractAEPower(wantedPower, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (availablePower + 0.01 < wantedPower) {
            double fixedPower = powerLong(fixedInputs);
            double eachPower = powerLong(recipe.inputs()) - fixedPower;
            if (availablePower < fixedPower || eachPower <= 0) return 0;
            long affordable = (long) Math.floor((availablePower - fixedPower) / eachPower);
            if (affordable <= 0) return 0;
            slice = BigLongSlice.of(Math.min(slice.copies(), affordable),
                    recipe.inputs(), fixedInputs, recipe.outputs(), fixedOutputs);
            if (power(slice.inputs()) > availablePower + 0.01) return 0;
        }
        PreparedBatch prepared = null;
        if (batchCall) {
            try {
                prepared = batch.prepareBatch(details, recipe.template(), slice.copies(), view);
                if (prepared != null) {
                    long admitted = Math.min(slice.copies(), Math.max(0, prepared.capacity()));
                    if (admitted == 0
                            && com.moakiee.ae2lt.compat.neoeco.NeoEcoCraftingCompat.supports(provider)) {
                        // Processing/stateful recipes can opt out of FastPath and still take one copy.
                        prepared = null;
                        batchCall = false;
                        slice = BigLongSlice.plan(job.remainingStepCopies(), job.escrow,
                                recipe.inputs(), Map.of(), recipe.outputs(), Map.of(), 1);
                        if (slice.copies() == 0) return 0;
                    } else {
                        if (admitted == 0) return 0;
                        slice = BigLongSlice.of(admitted, recipe.inputs(), fixedInputs, recipe.outputs(), fixedOutputs);
                    }
                }
            } catch (RuntimeException unavailable) { return 0; }
        }
        job.reserve(slice);
        changed.run();
        try {
            // Native batch templates are borrowed; the single-copy API takes ownership of its clone.
            long leftover = prepared != null ? prepared.push(slice.copies()) : batchCall
                    ? batch.pushBatch(details, recipe.template(), slice.copies(), view)
                    : (provider instanceof DeferredCraftingProvider deferred
                            ? deferred.pushPattern(details, recipe.template(), transaction.outputSink())
                            : com.moakiee.ae2lt.compat.neoeco.NeoEcoCraftingCompat.pushForJob(
                                    provider, details, recipe.template(), job.id)) ? 0 : slice.copies();
            if (leftover < 0 || leftover > slice.copies())
                throw new IllegalStateException("Provider returned an invalid accepted count");
            long received = slice.copies() - leftover;
            var accepted = BigLongSlice.of(received,
                    recipe.inputs(), fixedInputs, recipe.outputs(), fixedOutputs);
            job.settle(slice, accepted);
            energy.extractAEPower(power(accepted.inputs()), Actionable.MODULATE, PowerMultiplier.CONFIG);
            changed.run();
            transaction.accounted();
            return received;
        } catch (RuntimeException uncertain) {
            // A provider might have accepted work before throwing. Refunding/retrying would duplicate it.
            job.failDispatch();
            changed.run();
            appeng.core.AELog.warn("[ae2lt] Exact order %s stopped after indeterminate provider ownership: %s",
                    job.id, uncertain);
            return 0;
        }
    }

    private Long dispatchNeoEcoExact(BigCraftingJob job, BigProviderRecipe recipe, IPatternDetails details,
            ICraftingProvider provider, Level level, IEnergyService energy, Runnable changed,
            CraftingDispatch transaction) {
        if (!com.moakiee.ae2lt.compat.neoeco.NeoEcoCraftingCompat.supports(provider)
                || !recipe.sharedInputs().isEmpty()) return null;
        BigInteger requested = job.remainingStepCopies();
        if (details instanceof BatchCopyLimitPattern limit)
            requested = requested.min(BigInteger.valueOf(Math.max(1, limit.maxBatchCopies())));
        for (var input : recipe.inputs().entrySet())
            requested = requested.min(job.escrow.getOrDefault(input.getKey(), BigInteger.ZERO)
                    .divide(BigInteger.valueOf(input.getValue())));
        double eachPower = powerLong(recipe.inputs());
        if (eachPower > 0) {
            double available = energy.extractAEPower(Double.MAX_VALUE, Actionable.SIMULATE, PowerMultiplier.CONFIG);
            if (!Double.isFinite(available)) available = Double.MAX_VALUE;
            requested = requested.min(java.math.BigDecimal.valueOf(Math.max(0, available))
                    .divide(java.math.BigDecimal.valueOf(eachPower), 0, java.math.RoundingMode.DOWN).toBigInteger());
        }
        if (requested.signum() <= 0) return 0L;
        com.moakiee.ae2lt.compat.neoeco.NeoEcoCraftingCompat.ExactBatch batch;
        try {
            batch = com.moakiee.ae2lt.compat.neoeco.NeoEcoCraftingCompat.prepareExact(
                    provider, details, recipe.template(), requested, level, job.id);
            if (batch == null) return null;
        } catch (RuntimeException unavailable) { return 0L; }
        var offered = BigCraftingTransfer.of(recipe, batch.copies());
        double offeredPower = power(offered.inputs());
        if (!Double.isFinite(offeredPower) || energy.extractAEPower(offeredPower,
                Actionable.SIMULATE, PowerMultiplier.CONFIG) + 0.01 < offeredPower) return 0L;
        job.reserve(offered);
        changed.run();
        try {
            boolean accepted = batch.submit();
            job.settle(offered, accepted ? offered : BigCraftingTransfer.of(recipe, BigInteger.ZERO));
            if (accepted) energy.extractAEPower(power(offered.inputs()), Actionable.MODULATE, PowerMultiplier.CONFIG);
            changed.run();
            transaction.accounted();
            return accepted ? 1L : 0L;
        } catch (RuntimeException uncertain) {
            job.failDispatch();
            changed.run();
            appeng.core.AELog.warn("[ae2lt] Exact order %s stopped after uncertain NeoECO ownership: %s", job.id, uncertain);
            return 0L;
        }
    }

    private IBatchCraftingProvider resolve(ICraftingProvider provider, IPatternDetails details,
            BatchJobView view, long tick) {
        if (adapterTick != tick) {
            adapters.clear();
            started.clear();
            adapterTick = tick;
        }
        if (provider instanceof IBatchCraftingProvider nativeBatch) {
            if (started.add(nativeBatch)) nativeBatch.beginDispatchTick(tick);
            return nativeBatch;
        }
        var resolutions = adapters.computeIfAbsent(provider, ignored -> new IdentityHashMap<>());
        for (var entry : BatchProviderAdapters.entries()) {
            var adapter = entry.adapter();
            IBatchCraftingProvider result;
            if (adapter instanceof BatchProviderResolver resolver && resolver.cacheResolutionForTick()) {
                if (resolutions.containsKey(resolver)) result = resolutions.get(resolver);
                else {
                    result = resolver.resolve(provider);
                    if (result != null) result.beginDispatchTick(tick);
                    resolutions.put(resolver, result);
                }
            } else {
                result = adapter.adapt(provider, details, view);
                if (result != null) result.beginDispatchTick(tick);
            }
            if (result != null) return result;
        }
        return null;
    }

    private static double power(Map<AEKey, BigInteger> inputs) {
        double result = 0;
        for (var entry : inputs.entrySet())
            result += entry.getValue().doubleValue() / Math.max(1, entry.getKey().getAmountPerOperation());
        return result;
    }

    private static double powerLong(Map<AEKey, Long> inputs) {
        double result = 0;
        for (var entry : inputs.entrySet())
            result += (double) entry.getValue() / Math.max(1, entry.getKey().getAmountPerOperation());
        return result;
    }

    private record View(BigCraftingJob job, IPatternDetails details, Level level,
                        CraftingDispatch transaction) implements DeferredCraftingProvider.Job {
        public DeferredCraftingProvider.OutputSink deferredOutputSink() { return transaction.outputSink(); }
        public UUID craftingId() { return job.id; }
        public Iterator<BatchTaskHandle> taskIterator() {
            return List.<BatchTaskHandle>of(new BatchTaskHandle() {
                public IPatternDetails details() { return details; }
                public long getValue() { return BigAmounts.project(job.remainingStepCopies()); }
                public void setValue(long value) { throw new UnsupportedOperationException("Exact task owns its count"); }
            }).iterator();
        }
        public ListCraftingInventory waitingFor() {
            var projected = new ListCraftingInventory(key -> {});
            job.waiting.forEach((key, n) -> projected.insert(key, BigAmounts.project(n), Actionable.MODULATE));
            return projected;
        }
        public void insertWaitingFor(AEKey key, long amount) {
            throw new UnsupportedOperationException("Outputs are registered by the exact dispatch transaction");
        }
        public void addContainerMaxItems(long count, AEKeyType type) {}
    }
}
