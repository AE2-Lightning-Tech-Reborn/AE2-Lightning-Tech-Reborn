package com.moakiee.ae2lt.mixin.client;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.menu.me.common.GridInventoryEntry;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reuse AE2's processing selection and overflow-aware merging for additional draft slot sets. */
@Mixin(value = EncodingHelper.class, remap = false)
public interface EncodingHelperAccessor {
    @Accessor("ENTRY_COMPARATOR")
    static Comparator<GridInventoryEntry> ae2lt$entryComparator() {
        throw new AssertionError("Mixin not applied");
    }

    @Invoker("findBestIngredient")
    static GenericStack ae2lt$findBestIngredient(Map<AEKey, Integer> priorities, List<GenericStack> options) {
        throw new AssertionError("Mixin not applied");
    }

    @Invoker("addOrMerge")
    static void ae2lt$addOrMerge(List<GenericStack> stacks, GenericStack added) {
        throw new AssertionError("Mixin not applied");
    }
}
