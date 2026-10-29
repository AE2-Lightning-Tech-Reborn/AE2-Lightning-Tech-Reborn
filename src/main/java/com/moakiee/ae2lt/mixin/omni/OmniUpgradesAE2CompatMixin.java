package com.moakiee.ae2lt.mixin.omni;

import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Frequency cards own block interactions even though they also implement AE2 upgrades. */
@Pseudo
@Mixin(targets = "com.sakurain.omni_upgrades.compat.AE2Compat", remap = false)
public abstract class OmniUpgradesAE2CompatMixin {
    @Inject(method = "isUpgradeItem(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void ae2lt$preserveFrequencyCardUse(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.getItem() instanceof OverloadedFrequencyCardItem) cir.setReturnValue(false);
    }
}
