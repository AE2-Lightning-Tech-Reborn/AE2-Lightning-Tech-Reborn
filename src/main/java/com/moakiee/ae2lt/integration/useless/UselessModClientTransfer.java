package com.moakiee.ae2lt.integration.useless;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.client.compat.ProcessingPatternTransferStacks;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import static com.moakiee.ae2lt.integration.useless.UselessModReflection.*;
public final class UselessModClientTransfer {
    private UselessModClientTransfer() {}
    @SuppressWarnings("unchecked")
    public static ItemStack prepare(TianshuPatternEncodingTermMenu menu,Object entry) {
        if(!UselessModCompat.isViewerRecipe(entry))return ItemStack.EMPTY;
        try {
            var recipe=call(entry,"recipe");
            var options=(List<List<GenericStack>>)call("com.sorrowmist.useless.compat.jei.OmniversalPatternJeiTransferHandler","inputOptions",recipe);
            var nativePattern=UselessModCompat.encodeViewerRecipe(entry,menu.getPlayer().level());
            var preview=UselessModPatternBridge.preview(nativePattern,menu.getPlayer().level());
            var priorities=ProcessingPatternTransferStacks.priorities(menu);
            var inputs=ProcessingPatternTransferStacks.select(options,priorities);
            var outputs=ProcessingPatternTransferStacks.select(preview.outputs().stream().map(List::of).toList(),priorities);
            if(inputs.isEmpty()||outputs.isEmpty()||inputs.size()>menu.getOmniversalInputSlots().length||outputs.size()>menu.getOmniversalOutputSlots().length||preview.molds().size()>menu.getOmniversalMoldSlots().length)return ItemStack.EMPTY;
            var source=PatternDetailsHelper.encodeProcessingPattern(inputs.toArray(GenericStack[]::new),outputs.toArray(GenericStack[]::new));
            return (ItemStack)call("com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternEncoding","encode",source,entry,menu.getPlayer().level());
        } catch(RuntimeException failure) {return ItemStack.EMPTY;}
    }
}
