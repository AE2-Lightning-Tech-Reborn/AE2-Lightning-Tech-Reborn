package com.moakiee.ae2lt.machine.largeoverload;

import java.util.List;
import appeng.api.stacks.GenericStack;
import net.minecraft.world.item.ItemStack;

/** AE2 15 takes arrays for the same physical processing-pattern data. */
public final class LargeFactoryPatternEncoding {
    private LargeFactoryPatternEncoding() { }
    public static ItemStack encodeProcessingPattern(List<GenericStack> inputs, List<GenericStack> outputs) {
        return appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(
                inputs.toArray(GenericStack[]::new), outputs.toArray(GenericStack[]::new));
    }
}
