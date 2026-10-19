package com.moakiee.ae2lt.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.Entity;

import com.moakiee.ae2lt.client.equipment.ShieldHitFeedbackClientState;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerShieldHitFeedbackMixin {
    @WrapOperation(
            method = "handleEntityEvent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;handleEntityEvent(B)V"))
    private void ae2lt$skipShieldHurtAnimation(Entity entity, byte event, Operation<Void> original) {
        // 1.19 sends hurt feedback as entity status events, alongside unrelated events.
        boolean hurt = event == 2 || event == 33 || event == 36 || event == 37 || event == 44 || event == 57;
        if (hurt && ShieldHitFeedbackClientState.suppressHurtAnimation(entity)) {
            return;
        }
        original.call(entity, event);
    }
}
