package com.moakiee.ae2lt.client;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.menu.me.common.MEStorageMenu;
import com.moakiee.ae2lt.mixin.client.EncodingHelperAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** AE2's processing fill, projected into a draft before any menu or network state is changed. */
public final class ProcessingPatternTransferStacks {
    private ProcessingPatternTransferStacks() {}

    public static Map<AEKey, Integer> priorities(MEStorageMenu menu) {
        return EncodingHelper.getIngredientPriorities(menu, EncodingHelperAccessor.ae2lt$entryComparator());
    }

    public static List<GenericStack> select(List<List<GenericStack>> ingredients, Map<AEKey, Integer> priorities) {
        var selected = new ArrayList<GenericStack>();
        for (var options : ingredients) {
            if (!options.isEmpty()) {
                EncodingHelperAccessor.ae2lt$addOrMerge(selected,
                        EncodingHelperAccessor.ae2lt$findBestIngredient(priorities, options));
            }
        }
        return List.copyOf(selected);
    }
}
