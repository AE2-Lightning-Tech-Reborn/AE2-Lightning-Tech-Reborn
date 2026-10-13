package com.moakiee.ae2lt.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.client.workstation.PigmeeControlScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Move only the render section grid. The logical player, world and packet origin stay native. */
@Mixin(LevelRenderer.class)
public abstract class PigmeeRenderOriginMixin {
    @WrapOperation(method = "setupRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getX()D"))
    private double ae2lt$renderX(LocalPlayer body, Operation<Double> original) {
        Entity subject = PigmeeControlScreen.renderView(body);
        return subject == body ? original.call(body) : subject.getX();
    }
    @WrapOperation(method = "setupRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getY()D"))
    private double ae2lt$renderY(LocalPlayer body, Operation<Double> original) {
        Entity subject = PigmeeControlScreen.renderView(body);
        return subject == body ? original.call(body) : subject.getY();
    }
    @WrapOperation(method = "setupRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getZ()D"))
    private double ae2lt$renderZ(LocalPlayer body, Operation<Double> original) {
        Entity subject = PigmeeControlScreen.renderView(body);
        return subject == body ? original.call(body) : subject.getZ();
    }
    @WrapOperation(method = "allChanged", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getCameraEntity()Lnet/minecraft/world/entity/Entity;"))
    private Entity ae2lt$rebuildRenderOrigin(Minecraft client, Operation<Entity> original) {
        return PigmeeControlScreen.renderView(original.call(client));
    }
}
