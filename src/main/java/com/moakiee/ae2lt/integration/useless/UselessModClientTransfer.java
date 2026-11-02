package com.moakiee.ae2lt.integration.useless;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.client.compat.ProcessingPatternTransferStacks;
import com.moakiee.ae2lt.logic.tianshu.terminal.ProcessingPatternPayload;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;

/** Uses the native recipe amounts and AE2's network-aware alternative selection. */
public final class UselessModClientTransfer {
    private UselessModClientTransfer() {}
    @SuppressWarnings("unchecked")
    public static ItemStack prepare(TianshuPatternEncodingTermMenu menu, Object entry) {
        if (!UselessModCompat.isLoaded() || !UselessModCompat.isViewerRecipe(entry)) return ItemStack.EMPTY;
        try {
            var recipe = UselessModPatternBridge.call(entry, "recipe");
            var options = (List<List<GenericStack>>) UselessModPatternBridge.call(
                    UselessModPatternBridge.type("compat.jei.OmniversalPatternJeiTransferHandler"), "inputOptions", recipe);
            var source = (ItemStack) UselessModPatternBridge.call(UselessModPatternBridge.type(
                    "content.machines.advanced_alloy_furnace.ae.OmniversalPatternEncoding"), "createProcessingPattern", recipe);
            var data = ProcessingPatternPayload.read(source);
            if (options == null || options.isEmpty() || data == null) return ItemStack.EMPTY;
            var priorities = ProcessingPatternTransferStacks.priorities(menu);
            var inputs = ProcessingPatternTransferStacks.select(options, priorities);
            var outputs = ProcessingPatternTransferStacks.select(data.sparseOutputs().stream()
                    .filter(Objects::nonNull).map(List::of).toList(), priorities);
            if (inputs.isEmpty() || outputs.isEmpty() || inputs.size() > menu.getOmniversalInputSlots().length
                    || outputs.size() > menu.getOmniversalOutputSlots().length
                    || ((List<?>) UselessModPatternBridge.call(recipe, "molds")).size() > menu.getOmniversalMoldSlots().length) return ItemStack.EMPTY;
            // Bind the native recipe after choosing the same network-aware alternatives as processing.
            var processing = PatternDetailsHelper.encodeProcessingPattern(
                    inputs.toArray(GenericStack[]::new), outputs.toArray(GenericStack[]::new));
            return (ItemStack) UselessModPatternBridge.call(UselessModPatternBridge.type(
                    "content.machines.advanced_alloy_furnace.ae.OmniversalPatternEncoding"), "encode", processing, entry, menu.getPlayer().level());
        } catch (RuntimeException | LinkageError unavailableApi) { return ItemStack.EMPTY; }
    }
}
