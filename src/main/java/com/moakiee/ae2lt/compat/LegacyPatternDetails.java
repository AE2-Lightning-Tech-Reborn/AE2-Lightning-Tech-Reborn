package com.moakiee.ae2lt.compat;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

/** Preserves LT input-ordering hooks on AE2 12, whose provider otherwise flattens counters. */
public interface LegacyPatternDetails extends IPatternDetails {
    default boolean supportsPushInputsToExternalInventory() { return true; }
    default void pushInputsToExternalInventory(KeyCounter[] inputs, PatternInputSink sink) { flatten(inputs, sink); }
    interface PatternInputSink { void pushInput(AEKey key, long amount); }
    static boolean supports(IPatternDetails pattern) {
        return !(pattern instanceof LegacyPatternDetails custom) || custom.supportsPushInputsToExternalInventory();
    }
    static void push(IPatternDetails pattern, KeyCounter[] inputs, PatternInputSink sink) {
        if (pattern instanceof LegacyPatternDetails custom) custom.pushInputsToExternalInventory(inputs, sink);
        else flatten(inputs, sink);
    }
    private static void flatten(KeyCounter[] inputs, PatternInputSink sink) {
        for (var input : inputs) for (var entry : input) sink.pushInput(entry.getKey(), entry.getLongValue());
    }
}
