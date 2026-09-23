package com.moakiee.ae2lt.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;

import com.moakiee.ae2lt.client.railgun.RailgunClientExtensions;
import com.moakiee.ae2lt.item.railgun.ElectromagneticRailgunItem;

@Mixin(HumanoidModel.class)
public class RailgunHumanoidModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("RETURN"))
    private void ae2lt$poseRailgun(HumanoidRenderState state, CallbackInfo ci) {
        HumanoidArm arm = null;
        if (state.getMainHandItemStack().getItem() instanceof ElectromagneticRailgunItem) {
            arm = state.mainArm;
        } else if (state.getUseItemStackForArm(state.mainArm.getOpposite()).getItem()
                instanceof ElectromagneticRailgunItem) {
            arm = state.mainArm.getOpposite();
        }
        if (arm == null) {
            return;
        }
        RailgunClientExtensions.poseRailgunArms((HumanoidModel<?>) (Object) this, arm);
    }
}
