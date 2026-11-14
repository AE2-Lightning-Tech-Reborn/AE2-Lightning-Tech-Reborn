package com.moakiee.ae2lt.mixin.mmcr;

import appeng.api.crafting.IPatternDetails;
import com.moakiee.ae2lt.compat.mmcr.MmcrBatchIntegration;
import java.util.LinkedHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep MMCR's material plan unchanged while dynamic consumers choose native start sizes at runtime. */
@Pseudo
@Mixin(targets = "cn.howxu.mmcr.compat.appliedenergistics2.loaded.PatternBatchPlanning", remap = false)
public abstract class PatternBatchPlanningMixin {
    @Inject(method = "rewrite", at = @At("RETURN"), cancellable = true, require = 0)
    private static void ae2lt$unitPatternTasks(CallbackInfoReturnable<LinkedHashMap<IPatternDetails, Long>> cir) {
        cir.setReturnValue(MmcrBatchIntegration.normalizePlan(cir.getReturnValue()));
    }
}
