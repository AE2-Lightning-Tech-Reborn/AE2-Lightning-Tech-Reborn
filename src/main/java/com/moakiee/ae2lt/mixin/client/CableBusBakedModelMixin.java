package com.moakiee.ae2lt.mixin.client;

import java.util.ArrayList;
import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.resources.model.geometry.BakedQuad;

import appeng.client.render.cablebus.CableBusModel;
import appeng.block.networking.CableBusRenderState;

import com.moakiee.ae2lt.client.render.OverloadedCableRenderHelper;
import com.moakiee.ae2lt.client.render.OverloadedCableRenderStateAccess;

@Mixin(CableBusModel.class)
public class CableBusBakedModelMixin {
    @Inject(method = "getCableQuads", at = @At("HEAD"), cancellable = true)
    private void ae2lt$renderOverloadedCable(CableBusRenderState renderState, Consumer<BakedQuad> quadsOut,
            CallbackInfo ci) {
        if (renderState == null) {
            return;
        }
        if (!((OverloadedCableRenderStateAccess) renderState).ae2lt$isOverloadedCable()) {
            return;
        }

        var quads = new ArrayList<BakedQuad>();
        OverloadedCableRenderHelper.addCableQuads(renderState, quads);
        quads.forEach(quadsOut);
        ci.cancel();
    }
}
