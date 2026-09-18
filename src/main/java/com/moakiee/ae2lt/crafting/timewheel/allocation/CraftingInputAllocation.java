package com.moakiee.ae2lt.crafting.timewheel.allocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

/**
 * A transient CPU extraction allowance. Keys remain original pattern slot indices and amounts are
 * physical inventory units, not firing counts. Missing slots retain the pattern's normal semantics.
 * With choices, slot entries may be empty ownership markers; quantities are queried from choices.
 * This is neither a crafting plan nor a persisted pattern. Cached snapshots must be revalidated
 * against current task counts and inventory; each extraction opens a fresh choices transaction.
 * Allocate, open and use it synchronously on the CPU thread before the next allocation call. A
 * persistent witness may change between allocation calls; transaction writes remain isolated.
 */
public record CraftingInputAllocation(boolean allowed, Map<Integer, Map<AEKey, Long>> slotAllowances,
        @Nullable Supplier<Choices> choiceFactory) {
    public static final CraftingInputAllocation UNRESTRICTED = new CraftingInputAllocation(true, Map.of());
    public static final CraftingInputAllocation WAIT = new CraftingInputAllocation(false, Map.of());

    public CraftingInputAllocation(boolean allowed, Map<Integer, Map<AEKey, Long>> slotAllowances) {
        this(allowed, slotAllowances, null);
    }

    public CraftingInputAllocation {
        var copy = new LinkedHashMap<Integer, Map<AEKey, Long>>();
        slotAllowances.forEach((slot, amounts) -> copy.put(slot, Map.copyOf(amounts)));
        slotAllowances = Map.copyOf(copy);
    }

    /** A fresh transaction: a cached allocation must never share consumption with another attempt. */
    public @Nullable Choices openChoices() {
        return choiceFactory == null ? null : choiceFactory.get();
    }

    /** Optional residual matching for finite inputs. Allowances are not a permanent binding.
     * Queries may rearrange that witness, but only consume records physical extraction. */
    public interface Choices {
        boolean managesSlot(int slot);
        /** Proven amount for this candidate. Multiple keys may share a compressed bucket; callers
         * must check joint consumption before using per-key amounts as a batch lower bound. */
        long assigned(int slot, AEKey key);
        long available(int slot, AEKey key, long requested);
        void consume(int slot, AEKey key, long amount);
        /** Already-proven concrete candidates, in extraction order. Amounts are template units.
         * Optional hint only: callers must still validate with the original input and inventory,
         * and may use normal candidate discovery when these candidates cannot fill the slot. */
        default Iterable<GenericStack> preferredInputs(int slot) { return java.util.List.of(); }
        /** Retain rearrangements after a complete successful extraction. This publishes a proof
         * for the PRE-extraction stock and demands by adding all consumed quantities back, not a
         * consumption commit. Provider rejection/partial acceptance is therefore safe; inventory
         * and task notifications remain authoritative. Finish using this transaction afterwards.
         * Speculative checkpoints must have been closed. Stale proofs may be ignored. */
        default void retainAssignments() { }
        /** Isolated speculative quantities; shares the transaction's bounded search budget. */
        Choices copy();
        /** Begin a nested speculative edit. Run the returned rollback exactly once, in LIFO order.
         * Search work remains charged after rollback; only changed quantities are restored. */
        Runnable checkpoint();
        boolean exhausted();
    }
}
