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
        var slice = BigCraftingTransfer.plan(job.remainingStepCopies(), job.escrow,
                recipe.inputs(), fixedInputs, recipe.outputs(), fixedOutputs, capacity);
        if (slice.copies().signum() <= 0) return 0;
        double wantedPower = power(slice.inputs());
        double availablePower = energy.extractAEPower(wantedPower, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (availablePower + 0.01 < wantedPower) {
            double fixedPower = powerLong(fixedInputs);
            double eachPower = powerLong(recipe.inputs()) - fixedPower;
            if (availablePower < fixedPower || eachPower <= 0) return 0;
            long affordable = (long) Math.floor((availablePower - fixedPower) / eachPower);
            if (affordable <= 0) return 0;
            slice = BigCraftingTransfer.of(Math.min(slice.copies().longValueExact(), affordable),
                    recipe.inputs(), fixedInputs, recipe.outputs(), fixedOutputs);
            if (power(slice.inputs()) > availablePower + 0.01) return 0;
        }
        PreparedBatch prepared = null;
        if (batchCall) {
            try {
                prepared = batch.prepareBatch(details, recipe.template(), slice.copies().longValueExact(), view);
                if (prepared != null) {
                    long admitted = Math.min(slice.copies().longValueExact(), Math.max(0, prepared.capacity()));
                    if (admitted == 0) return 0;
                    slice = BigCraftingTransfer.of(admitted, recipe.inputs(), fixedInputs, recipe.outputs(), fixedOutputs);
                }
            } catch (RuntimeException unavailable) { return 0; }
        }
        job.reserve(slice);
        changed.run();
        try {
            // Native batch templates are borrowed; the single-copy API takes ownership of its clone.
            long copies = slice.copies().longValueExact();
            long leftover = prepared != null ? prepared.push(copies) : batchCall
                    ? batch.pushBatch(details, recipe.template(), copies, view)
                    : (provider instanceof DeferredCraftingProvider deferred
                            ? deferred.pushPattern(details, recipe.template(), transaction.outputSink())
                            : provider.pushPattern(details, recipe.template())) ? 0 : copies;
            if (leftover < 0 || leftover > copies)
                throw new IllegalStateException("Provider returned an invalid accepted count");
            long received = copies - leftover;
            var accepted = BigCraftingTransfer.of(received,
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
