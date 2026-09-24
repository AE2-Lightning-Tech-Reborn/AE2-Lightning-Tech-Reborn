package com.moakiee.ae2lt.integration.useless;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import com.moakiee.ae2lt.client.ProcessingPatternTransferStacks;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import com.sorrowmist.useless.compat.jei.OmniversalPatternJeiTransferHandler;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternEncoding;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;

/** Client-only optional bridge, called only for a native Useless Mod viewer recipe. */
public final class UselessModClientTransfer {
    private UselessModClientTransfer() {}

    public static ItemStack prepare(TianshuPatternEncodingTermMenu menu, Object selection) {
        if (!(selection instanceof AlloyFurnaceRecipeCatalog.Entry entry)) return ItemStack.EMPTY;
        var options = OmniversalPatternJeiTransferHandler.inputOptions(entry.recipe());
        var source = OmniversalPatternEncoding.createProcessingPattern(entry.recipe());
        var data = source.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (options == null || options.isEmpty() || data == null) return ItemStack.EMPTY;

        var priorities = ProcessingPatternTransferStacks.priorities(menu);
        var inputs = ProcessingPatternTransferStacks.select(options, priorities);
        var outputs = ProcessingPatternTransferStacks.select(data.sparseOutputs().stream()
                .filter(Objects::nonNull).map(List::of).toList(), priorities);
        if (inputs.isEmpty() || outputs.isEmpty()
                || inputs.size() > menu.getOmniversalInputSlots().length
                || outputs.size() > menu.getOmniversalOutputSlots().length
                || entry.recipe().molds().size() > menu.getOmniversalMoldSlots().length) return ItemStack.EMPTY;

        // Use recipe amounts and types, not the viewer's sometimes abbreviated display stacks.
        // Bind the exact selected recipe after choosing the same alternatives as ordinary processing.
        var processing = PatternDetailsHelper.encodeProcessingPattern(inputs, outputs);
        return OmniversalPatternEncoding.encode(processing, entry, menu.getPlayer().level());
    }
}
