package com.moakiee.ae2lt.crafting.timewheel.allocation;

import com.moakiee.thunderbolt.core.crafting.batch.SharedBatchInputs;

import com.moakiee.thunderbolt.core.crafting.batch.SharedBatchInputPattern;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;

import com.moakiee.thunderbolt.api.crafting.batch.BatchJobView;
import com.moakiee.ae2lt.crafting.timewheel.allocation.CraftingInputAllocation;
import com.moakiee.ae2lt.crafting.timewheel.allocation.CraftingInputAllocation.Choices;

/** LT-local extraction and accounting, adapted from Thunderbolt's batch helper. The only native
 * result conversion is at the scoped batch-dispatch boundary; single/bulk LT paths stay local. */
public final class TimeWheelInputExtractor {
    private static final NativeBatchConstructors NATIVE_BATCH = nativeBatchConstructors();

    private TimeWheelInputExtractor() {
    }

    /** Only LT's scoped batch bridge needs the library's result type. Keep the lookup cached;
     * no changes to Thunderbolt's interfaces, result layout or dispatch rules are required. */
    static boolean canExportNativeBatch() { return NATIVE_BATCH != null; }

    static com.moakiee.thunderbolt.core.crafting.batch.ParallelBatchCpuHelper.BulkResult exportNativeBatch(
            BulkResult result) throws ReflectiveOperationException {
        if (NATIVE_BATCH == null) throw new IllegalStateException("native batch result is unavailable");
        var remainders = new ArrayList<Object>();
        if (result.remainders != null) for (var remainder : result.remainders) {
            remainders.add(NATIVE_BATCH.remainder.newInstance(remainder.key, remainder.count, remainder.shared));
        }
        return NATIVE_BATCH.result.newInstance(result.scaledInputs, result.actualCopies,
                result.scalablePerCopy, result.sharedPerBatch, List.copyOf(remainders));
    }

    private static NativeBatchConstructors nativeBatchConstructors() {
        try {
            var resultType = com.moakiee.thunderbolt.core.crafting.batch.ParallelBatchCpuHelper.BulkResult.class;
            var result = resultType.getDeclaredConstructor(KeyCounter[].class, long.class,
                    KeyCounter[].class, KeyCounter[].class, List.class);
            var remainderType = Class.forName(
                    "com.moakiee.thunderbolt.core.crafting.batch.ParallelBatchCpuHelper$RemainderSpec",
                    false, resultType.getClassLoader());
            var remainder = remainderType.getDeclaredConstructor(AEKey.class, long.class, boolean.class);
            result.setAccessible(true);
            remainder.setAccessible(true);
            return new NativeBatchConstructors(result, remainder);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return null;
        }
    }

    private record NativeBatchConstructors(
            java.lang.reflect.Constructor<com.moakiee.thunderbolt.core.crafting.batch.ParallelBatchCpuHelper.BulkResult> result,
            java.lang.reflect.Constructor<?> remainder) { }

    /**
     * Resolves the first concrete copy with AE2's native substitution rules, then scales only that
     * homogeneous input set. Different component variants therefore become separate batches.
     */
    @Nullable
    public static BulkResult bulkExtract(IPatternDetails details, ListCraftingInventory inv, long maxCraft,
                                         boolean allowSharedInputs, Map<AEKey, Long> reservedStock,
                                         Level level,
                                         @Nullable BiFunction<ICraftingInventory, Long, CraftingInputAllocation> allocator) {
        if (maxCraft <= 0) return null;

        // Only these known empty instances prove that no live reservation view is present.
        // Custom maps may expose reservations through get() while entrySet()/isEmpty() stay empty.
        ICraftingInventory guardedInventory = reservedStock == null || reservedStock == Map.<AEKey, Long>of()
                || reservedStock == java.util.Collections.<AEKey, Long>emptyMap()
                ? inv : new ReservedInventory(inv, reservedStock);
        var allocation = allocator == null ? CraftingInputAllocation.UNRESTRICTED
                : allocator.apply(guardedInventory, maxCraft);
        if (!allocation.allowed()) return null;
        var quotas = mutableQuotas(allocation);
        var choices = allocation.openChoices();
        var resolved = extractOneCopy(details, guardedInventory, allowSharedInputs, level, quotas, choices);
        if (resolved == null) return null;

        int slots = resolved.inputs.length;
        var scalablePerCopy = new KeyCounter[slots];
        var sharedPerBatch = new KeyCounter[slots];
        var scalableDemand = new HashMap<AEKey, Long>(slots * 2);
        for (int slot = 0; slot < slots; slot++) {
            scalablePerCopy[slot] = new KeyCounter();
            sharedPerBatch[slot] = new KeyCounter();
            for (var entry : resolved.inputs[slot]) {
                boolean shared = allowSharedInputs
                        && SharedBatchInputs.isSharedInput(details, slot, entry.getKey());
                var target = shared ? sharedPerBatch[slot] : scalablePerCopy[slot];
                target.add(entry.getKey(), entry.getLongValue());
                if (!shared) {
                    scalableDemand.merge(
                            entry.getKey(), entry.getLongValue(), TimeWheelInputExtractor::saturatingAdd);
                }
            }
        }

        long additionalCopies = maxCraft - 1;
        for (var entry : scalableDemand.entrySet()) {
            long available = guardedInventory.extract(entry.getKey(), Long.MAX_VALUE, Actionable.SIMULATE);
            additionalCopies = Math.min(additionalCopies, available / entry.getValue());
        }
        for (int slot = 0; slot < slots; slot++) {
            var quota = quotas.get(slot);
            if (quota == null || choices != null && choices.managesSlot(slot)) continue;
            for (var entry : scalablePerCopy[slot]) {
                additionalCopies = Math.min(additionalCopies,
                        quota.getOrDefault(entry.getKey(), 0L) / entry.getLongValue());
            }
        }

        if (choices != null) {
            var allowance = flexibleAdditionalCopies(choices, scalablePerCopy, additionalCopies);
            additionalCopies = allowance.copies;
            choices = allowance.choices;
        }
        var additionalExtracted = new HashMap<AEKey, Long>(scalableDemand.size() * 2);
        if (additionalCopies > 0) {
            for (var entry : scalableDemand.entrySet()) {
                long needed = saturatingMultiply(entry.getValue(), additionalCopies);
                long extracted = guardedInventory.extract(entry.getKey(), needed, Actionable.MODULATE);
                additionalExtracted.put(entry.getKey(), extracted);
                if (extracted < needed) {
                    for (var rollback : additionalExtracted.entrySet()) {
                        guardedInventory.insert(rollback.getKey(), rollback.getValue(), Actionable.MODULATE);
                    }
                    CraftingCpuHelper.reinjectPatternInputs(guardedInventory, resolved.inputs);
                    return null;
                }
            }
        }

        long actualCopies = additionalCopies + 1;
        var scaled = new KeyCounter[slots];
        for (int slot = 0; slot < slots; slot++) {
            scaled[slot] = new KeyCounter();
            scaled[slot].addAll(sharedPerBatch[slot]);
            addScaled(scaled[slot], scalablePerCopy[slot], actualCopies);
        }
        if (choices != null) choices.retainAssignments();
        return new BulkResult(
                scaled, actualCopies, scalablePerCopy, sharedPerBatch, resolved.remainders);
    }

    @Nullable
    private static ResolvedCopy extractOneCopy(IPatternDetails details,
                                               ICraftingInventory inventory,
                                               boolean allowSharedInputs,
                                               Level level,
                                               Map<Integer, Map<AEKey, Long>> quotas,
                                               @Nullable Choices choices) {
        var inputs = details.getInputs();
        var resolved = new KeyCounter[inputs.length];
        var remainders = new ArrayList<RemainderSpec>();
        for (int slot = 0; slot < inputs.length; slot++) {
            var input = inputs[slot];
            var holder = resolved[slot] = new KeyCounter();
            var slotInventory = quotas.containsKey(slot)
                    ? new SlotInventory(inventory, quotas.get(slot), slot, choices) : inventory;
            long remainingMultiplier = input.getMultiplier();
            boolean preferred = choices != null && choices.managesSlot(slot);
            for (int pass = preferred ? 0 : 1; pass < 2 && remainingMultiplier > 0; pass++) {
                // Native discovery eagerly enumerates all fuzzy alternatives. Open it only when
                // positive proof edges cannot fill this slot; it remains the substitution fallback.
                Iterable<InputTemplate> templates;
                if (pass == 0) {
                    var candidates = choices.preferredInputs(slot);
                    templates = () -> new java.util.Iterator<>() {
                        final java.util.Iterator<appeng.api.stacks.GenericStack> keys = candidates.iterator();
                        public boolean hasNext() { return keys.hasNext(); }
                        public InputTemplate next() {
                            var candidate = keys.next();
                            return new InputTemplate(candidate.what(), candidate.amount());
                        }
                    };
                } else templates = CraftingCpuHelper.getValidItemTemplates(slotInventory, input, level);
                for (var template : templates) {
                    long wanted = remainingMultiplier;
                    if (pass == 0) {
                        if (!input.isValid(template.key(), level)) continue;
                        wanted = Math.min(wanted, choices.assigned(slot, template.key()) / template.amount());
                    }
                    if (wanted <= 0) continue;
                    long extracted = CraftingCpuHelper.extractTemplates(slotInventory, template, wanted);
                    if (extracted <= 0) continue;

                    holder.add(template.key(), saturatingMultiply(extracted, template.amount()));
                    var remaining = input.getRemainingKey(template.key());
                    if (remaining != null) {
                        boolean shared = allowSharedInputs
                                && SharedBatchInputs.isSharedInput(details, slot, template.key());
                        remainders.add(new RemainderSpec(remaining, extracted, shared));
                    }
                    remainingMultiplier -= extracted;
                    if (remainingMultiplier == 0) break;
                }
            }
            if (remainingMultiplier > 0) {
                CraftingCpuHelper.reinjectPatternInputs(inventory, resolved);
                return null;
            }
        }
        return new ResolvedCopy(resolved, List.copyOf(remainders));
    }

    /** Same extraction transaction for ordinary CPUs and the time wheel; original pattern remains
     * authoritative for providers, output registration and persistence. */
    @Nullable
    public static KeyCounter[] extractPatternInputs(IPatternDetails details, ICraftingInventory inventory,
            Level level, KeyCounter expectedOutputs, KeyCounter expectedContainers,
            CraftingInputAllocation allocation) {
        if (!allocation.allowed()) return null;
        if (allocation.slotAllowances().isEmpty()) {
            return CraftingCpuHelper.extractPatternInputs(
                    details, inventory, level, expectedOutputs, expectedContainers);
        }
        var choices = allocation.openChoices();
        var copy = extractOneCopy(details, inventory, false, level, mutableQuotas(allocation), choices);
        if (copy == null) return null;
        if (choices != null) choices.retainAssignments();
        for (var output : details.getOutputs()) expectedOutputs.add(output.what(), output.amount());
        for (var remainder : copy.remainders) expectedContainers.add(remainder.key, remainder.count);
        return copy.inputs;
    }

    /** All slots of a batch must be feasible together. Independent per-key maxima are unsafe for
     * intersecting alternatives, so test the concrete batch as one speculative consumption. */
    record BatchAllowance(long copies, Choices choices) { }

    private static BatchAllowance flexibleAdditionalCopies(Choices choices, KeyCounter[] perCopy, long upper) {
        var best = new BatchAllowance(0, choices);
        if (upper <= 0) return best;
        long lower = upper;
        for (int slot = 0; slot < perCopy.length; slot++) {
            if (!choices.managesSlot(slot)) continue;
            for (var entry : perCopy[slot]) {
                lower = Math.min(lower, choices.assigned(slot, entry.getKey()) / entry.getLongValue());
            }
        }
        // A compressed bucket can expose the same capacity through several concrete keys.
        // Validate every joint lower bound too; per-key assigned amounts are not additive.
        var trial = tryConsumeCopies(choices, perCopy, upper);
        if (trial != null) return new BatchAllowance(upper, trial);
        if (lower > 0 && lower < upper) {
            trial = tryConsumeCopies(choices, perCopy, lower);
            if (trial != null) best = new BatchAllowance(lower, trial);
        }
        lower = best.copies;
        if (choices.exhausted()) return best;
        long high = upper - 1;
        while (lower < high && !choices.exhausted()) {
            long middle = lower + (high - lower) / 2 + 1;
            trial = tryConsumeCopies(choices, perCopy, middle);
            if (trial != null) { lower = middle; best = new BatchAllowance(middle, trial); }
            else high = middle - 1;
        }
        return best;
    }

    private static @Nullable Choices tryConsumeCopies(Choices choices, KeyCounter[] perCopy, long copies) {
        var rollback = choices.checkpoint();
        try {
            for (int slot = 0; slot < perCopy.length; slot++) {
                if (!choices.managesSlot(slot)) continue;
                for (var entry : perCopy[slot]) {
                    long amount = saturatingMultiply(entry.getLongValue(), copies);
                    if (choices.available(slot, entry.getKey(), amount) < amount) return null;
                    choices.consume(slot, entry.getKey(), amount);
                }
            }
            // Keep the successful sparse witness before rollback. Never re-solve it after a later
            // failed trial has used up the budget, and never publish a failed speculative branch.
            return choices.copy();
        } finally {
            rollback.run();
        }
    }

    private static Map<Integer, Map<AEKey, Long>> mutableQuotas(CraftingInputAllocation allocation) {
        var result = new HashMap<Integer, Map<AEKey, Long>>();
        allocation.slotAllowances().forEach((slot, amounts) -> result.put(slot, new HashMap<>(amounts)));
        return result;
    }

    private record SlotInventory(ICraftingInventory delegate, Map<AEKey, Long> quota,
            int slot, @Nullable Choices choices) implements ICraftingInventory {
        @Override public void insert(AEKey key, long amount, Actionable mode) {
            delegate.insert(key, amount, mode);
            if (mode == Actionable.MODULATE) quota.merge(key, amount, TimeWheelInputExtractor::saturatingAdd);
        }
        @Override public long extract(AEKey key, long amount, Actionable mode) {
            boolean flexible = choices != null && choices.managesSlot(slot);
            long allowed = flexible ? choices.available(slot, key, amount) : quota.getOrDefault(key, 0L);
            long extracted = delegate.extract(key, Math.min(amount, allowed), mode);
            if (mode == Actionable.MODULATE && extracted > 0) {
                if (flexible) choices.consume(slot, key, extracted);
                else quota.merge(key, -extracted, Long::sum);
            }
            return extracted;
        }
        @Override public Iterable<AEKey> findFuzzyTemplates(AEKey key) {
            return delegate.findFuzzyTemplates(key);
        }
    }

    public static void reinject(BulkResult result, long leftoverCopies, ListCraftingInventory inv) {
        if (leftoverCopies <= 0) return;
        long returnedCopies = Math.min(leftoverCopies, result.remainingCopies);
        for (int slot = 0; slot < result.scaledInputs.length; slot++) {
            for (var entry : result.scalablePerCopy[slot]) {
                long amount = saturatingMultiply(entry.getLongValue(), returnedCopies);
                if (amount > 0) {
                    inv.insert(entry.getKey(), amount, Actionable.MODULATE);
                    result.scaledInputs[slot].remove(entry.getKey(), amount);
                }
            }
        }
        result.remainingCopies -= returnedCopies;
        if (result.remainingCopies == 0 && !result.sharedDispatched) result.reinjectShared(inv);
    }

    public static void registerExpectedOutputs(BatchJobView job, IPatternDetails details,
                                               BulkResult result, long dispatched) {
        if (dispatched <= 0) return;
        registerPatternOutputs(job, details, dispatched, result.hasSharedInputs());
        if (result.remainders != null) {
            for (var remainder : result.remainders) {
                long copies = remainder.shared ? 1L : dispatched;
                long count = saturatingMultiply(remainder.count, copies);
                job.insertWaitingFor(remainder.key, count);
                job.addContainerMaxItems(count, remainder.key.getType());
            }
        }
    }

    private static void registerPatternOutputs(
            BatchJobView job, IPatternDetails details, long dispatched, boolean sharedBatch) {
        var sharedPattern = sharedBatch && details instanceof SharedBatchInputPattern pattern
                ? pattern : null;
        var sharedOutputsLeft = new HashMap<AEKey, Long>();
        for (var output : details.getOutputs()) {
            long sharedAmount = 0L;
            if (sharedPattern != null) {
                long remainingShared = sharedOutputsLeft.computeIfAbsent(
                        output.what(), sharedPattern::sharedBatchOutputAmount);
                sharedAmount = Math.min(output.amount(), Math.max(0L, remainingShared));
                sharedOutputsLeft.put(output.what(), remainingShared - sharedAmount);
            }
            long scalable = Math.max(0L, output.amount() - sharedAmount);
            job.insertWaitingFor(output.what(), saturatingAdd(
                    sharedAmount, saturatingMultiply(scalable, dispatched)));
        }
    }

    public static KeyCounter[] cloneSingleCopy(BulkResult result) {
        return copySlice(result, 1);
    }

    public static KeyCounter[] copySlice(BulkResult result, long sliceCount) {
        var slice = new KeyCounter[result.scaledInputs.length];
        for (int slot = 0; slot < slice.length; slot++) {
            slice[slot] = new KeyCounter();
            slice[slot].addAll(result.sharedPerBatch[slot]);
            addScaled(slice[slot], result.scalablePerCopy[slot], Math.max(0, sliceCount));
        }
        return slice;
    }

    public static void markDispatched(BulkResult result, long dispatchedCopies) {
        if (dispatchedCopies <= 0) return;
        long accepted = Math.min(dispatchedCopies, result.remainingCopies);
        for (int slot = 0; slot < result.scaledInputs.length; slot++) {
            for (var entry : result.scalablePerCopy[slot]) {
                long amount = saturatingMultiply(entry.getLongValue(), accepted);
                if (amount > 0) {
                    result.scaledInputs[slot].remove(entry.getKey(), amount);
                }
            }
            if (!result.sharedDispatched) {
                for (var entry : result.sharedPerBatch[slot]) {
                    result.scaledInputs[slot].remove(entry.getKey(), entry.getLongValue());
                }
            }
        }
        result.sharedDispatched = true;
        result.remainingCopies -= accepted;
    }

    public static final class BulkResult {
        public final KeyCounter[] scaledInputs;
        public final long actualCopies;
        final KeyCounter[] scalablePerCopy;
        final KeyCounter[] sharedPerBatch;
        @Nullable
        final List<RemainderSpec> remainders;
        long remainingCopies;
        boolean sharedDispatched;

        private BulkResult(KeyCounter[] scaledInputs, long actualCopies,
                           KeyCounter[] scalablePerCopy, KeyCounter[] sharedPerBatch,
                           List<RemainderSpec> remainders) {
            this.scaledInputs = scaledInputs;
            this.actualCopies = actualCopies;
            this.scalablePerCopy = scalablePerCopy;
            this.sharedPerBatch = sharedPerBatch;
            this.remainders = remainders;
            this.remainingCopies = actualCopies;
        }

        public boolean hasSharedInputs() {
            for (var counter : sharedPerBatch) {
                if (counter.iterator().hasNext()) return true;
            }
            return false;
        }

        private void reinjectShared(ListCraftingInventory inv) {
            for (int slot = 0; slot < scaledInputs.length; slot++) {
                for (var entry : sharedPerBatch[slot]) {
                    inv.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
                    scaledInputs[slot].remove(entry.getKey(), entry.getLongValue());
                }
            }
        }
    }

    private static void addScaled(KeyCounter target, KeyCounter source, long scale) {
        if (scale <= 0) return;
        for (var entry : source) {
            target.add(entry.getKey(), saturatingMultiply(entry.getLongValue(), scale));
        }
    }

    private record ResolvedCopy(KeyCounter[] inputs, List<RemainderSpec> remainders) {
    }

    private record RemainderSpec(AEKey key, long count, boolean shared) {
    }

    private static final class ReservedInventory implements ICraftingInventory {
        private final ListCraftingInventory delegate;
        private final Map<AEKey, Long> reserved;

        private ReservedInventory(ListCraftingInventory delegate, Map<AEKey, Long> reserved) {
            this.delegate = delegate;
            this.reserved = reserved != null ? reserved : Map.of();
        }

        @Override
        public void insert(AEKey what, long amount, Actionable mode) {
            delegate.insert(what, amount, mode);
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode) {
            long available = delegate.extract(what, Long.MAX_VALUE, Actionable.SIMULATE);
            long protectedAmount = Math.max(0L, reserved.getOrDefault(what, 0L));
            long extractable = Math.max(0L, available - protectedAmount);
            long allowed = Math.min(Math.max(0L, amount), extractable);
            return mode == Actionable.SIMULATE
                    ? allowed : delegate.extract(what, allowed, Actionable.MODULATE);
        }

        @Override
        public Iterable<AEKey> findFuzzyTemplates(AEKey input) {
            return delegate.findFuzzyTemplates(input);
        }
    }

    private static long saturatingAdd(long left, long right) {
        return left >= Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0 || right <= 0) return 0L;
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }
}
