package com.moakiee.ae2lt.mixin.client;

import appeng.client.gui.style.StyleManager;
import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.moakiee.ae2lt.client.compat.CtnhEnergyEncodingStyle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = StyleManager.class, remap = false)
public abstract class CtnhEnergyEncodingStyleMixin {
    @ModifyExpressionValue(
            method = "loadStyleDocInternal",
            at = @At(value = "INVOKE", target = "Lappeng/client/gui/style/StyleManager;loadMergedJsonTree(Ljava/lang/String;Ljava/util/Set;Ljava/util/Set;)Lcom/google/gson/JsonObject;"))
    private static JsonObject ae2lt$addCircuitWidget(JsonObject style, String path) {
        return CtnhEnergyEncodingStyle.addMissingCircuitWidget(style, path);
    }
}
