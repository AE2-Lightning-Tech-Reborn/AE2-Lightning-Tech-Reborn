package com.moakiee.ae2lt.mixin.big;

import appeng.menu.me.crafting.CraftingStatusEntry;

import com.moakiee.ae2lt.crafting.big.BigStatusEntry;
import com.moakiee.thunderbolt.ae2.crafting.ExactPlanReport;

import net.minecraft.network.RegistryFriendlyByteBuf;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = CraftingStatusEntry.class, remap = false)
public abstract class BigCraftingStatusEntryMixin implements BigStatusEntry {
    @Unique private Amounts ae2lt$amounts;

    public Amounts ae2lt$amounts() {
        return ae2lt$amounts;
    }

    public void ae2lt$amounts(Amounts amounts) {
        ae2lt$amounts = amounts;
    }

    @Inject(method = "write", at = @At("TAIL"))
    private static void ae2lt$write(
            RegistryFriendlyByteBuf b, CraftingStatusEntry entry, CallbackInfo ci) {
        var a = ((BigStatusEntry) entry).ae2lt$amounts();
        b.writeBoolean(a != null);
        if (a != null) {
            ExactPlanReport.writeAmount(b, a.stored());
            ExactPlanReport.writeAmount(b, a.active());
            ExactPlanReport.writeAmount(b, a.pending());
        }
    }

    @Inject(method = "read", at = @At("RETURN"))
    private static void ae2lt$read(
            RegistryFriendlyByteBuf b, CallbackInfoReturnable<CraftingStatusEntry> cir) {
        if (b.readBoolean())
            ((BigStatusEntry) cir.getReturnValue())
                    .ae2lt$amounts(
                            new Amounts(
                                    ExactPlanReport.readAmount(b),
                                    ExactPlanReport.readAmount(b),
                                    ExactPlanReport.readAmount(b)));
    }
}
