package com.moakiee.ae2lt.mixin.client;

import appeng.api.stacks.*;
import appeng.client.gui.widgets.CPUSelectionList;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.moakiee.ae2lt.crafting.big.BigDisplayAmounts;
import com.moakiee.thunderbolt.ae2.crafting.ExactAmountFormatter;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CPUSelectionList.class, remap = false)
public abstract class BigCpuListMixin {
    @WrapOperation(
            method = "drawBackgroundLayer",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lappeng/api/stacks/AEKey;formatAmount(JLappeng/api/stacks/AmountFormat;)Ljava/lang/String;"))
    private String ae2lt$format(
            AEKey key,
            long amount,
            AmountFormat format,
            Operation<String> original,
            @Local GenericStack job) {
        var precise = BigDisplayAmounts.get(job);
        return precise == null
                ? original.call(key, amount, format)
                : ExactAmountFormatter.compact(precise, key.getAmountPerUnit());
    }
}
