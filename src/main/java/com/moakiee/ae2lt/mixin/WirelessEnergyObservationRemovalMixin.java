package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.logic.energy.WirelessEnergyAdmissions;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public abstract class WirelessEnergyObservationRemovalMixin {
    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void ae2lt$interruptObservation(CallbackInfo ci) {
        WirelessEnergyAdmissions.onUnavailable((BlockEntity) (Object) this);
    }
}
