package com.moakiee.ae2lt.client.compat;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.integration.modules.jeirei.EncodingHelper;
import appeng.menu.me.common.MEStorageMenu;
import com.moakiee.ae2lt.mixin.client.EncodingHelperAccessor;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.List;

/** AE2's processing fill, projected into a draft before any menu or network state is changed. */
public final class ProcessingPatternTransferStacks {
    private ProcessingPatternTransferStacks() {}

    public static Reference2IntOpenHashMap<AEKey> priorities(MEStorageMenu menu) {
        return EncodingHelper.getIngredientPriorities(menu, EncodingHelperAccessor.ae2lt$entryComparator());
    }

    public static List<GenericStack> select(List<List<GenericStack>> ingredients, Reference2IntOpenHashMap<AEKey> priorities) {
        var selected = new ObjectArrayList<GenericStack>();
        for (var options : ingredients) {
            if (!options.isEmpty()) {
                EncodingHelperAccessor.ae2lt$addOrMerge(selected,
                        EncodingHelperAccessor.ae2lt$findBestIngredient(priorities, options));
            }
        }
        return List.copyOf(selected);
    }
}
