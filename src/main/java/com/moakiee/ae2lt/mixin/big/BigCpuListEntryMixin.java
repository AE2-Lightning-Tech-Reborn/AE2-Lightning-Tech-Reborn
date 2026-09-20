package com.moakiee.ae2lt.mixin.big;

import appeng.menu.me.crafting.CraftingStatusMenu.CraftingCpuListEntry;

import com.moakiee.ae2lt.crafting.big.BigDisplayAmounts;
import com.moakiee.thunderbolt.ae2.crafting.ExactPlanReport;

import net.minecraft.network.FriendlyByteBuf;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = CraftingCpuListEntry.class, remap = false)
public abstract class BigCpuListEntryMixin {
    @Inject(method = "writeToPacket", at = @At("TAIL"))
    private void ae2lt$write(FriendlyByteBuf b, CallbackInfo ci) {
        var amount = BigDisplayAmounts.get(((CraftingCpuListEntry) (Object) this).currentJob());
        b.writeBoolean(amount != null);
        if (amount != null) ExactPlanReport.writeAmount(b, amount);
    }

    @Inject(method = "readFromPacket", at = @At("RETURN"))
    private static void ae2lt$read(
            FriendlyByteBuf b, CallbackInfoReturnable<CraftingCpuListEntry> cir) {
        if (b.readBoolean())
            BigDisplayAmounts.attach(
                    cir.getReturnValue().currentJob(), ExactPlanReport.readAmount(b));
    }
}
