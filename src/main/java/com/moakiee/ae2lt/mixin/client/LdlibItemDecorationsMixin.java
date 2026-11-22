package com.moakiee.ae2lt.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.client.ldlib.LdlibSlotHost;
import com.moakiee.ae2lt.client.ldlib.LdlibText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiGraphics.class)
public abstract class LdlibItemDecorationsMixin {
    @WrapOperation(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I"))
    private int ae2lt$drawItemCount(GuiGraphics graphics, Font font, String text, int x, int y,
            int color, boolean shadow, Operation<Integer> original) {
        if (!LdlibSlotHost.usesLdlibSlotDecorations(Minecraft.getInstance().screen)) {
            return original.call(graphics, font, text, x, y, color, shadow);
        }
        return LdlibText.drawString(graphics, font, text, x, y, color, shadow);
    }
}
