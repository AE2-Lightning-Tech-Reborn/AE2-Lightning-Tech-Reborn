package com.moakiee.ae2lt.mixin.client;

import appeng.client.gui.implementations.PatternProviderLockReason;
import appeng.client.gui.style.Blitter;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib2.gui.LDLibFonts;
import com.moakiee.ae2lt.client.ldlib.LdlibControlSprite;
import com.moakiee.ae2lt.client.provider.OverloadedPatternProviderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PatternProviderLockReason.class, remap = false)
public abstract class PatternProviderLockReasonLdlibMixin {
    @WrapOperation(method = "drawForegroundLayer", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/style/Blitter;blit(Lnet/minecraft/client/gui/GuiGraphics;)V"))
    private void ae2lt$drawLockIcon(Blitter image, GuiGraphics graphics, Operation<Void> original) {
        if (!(Minecraft.getInstance().screen instanceof OverloadedPatternProviderScreen<?>)
                || !LdlibControlSprite.draw(image, graphics)) {
            original.call(image, graphics);
        }
    }

    @WrapOperation(method = "drawForegroundLayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"))
    private int ae2lt$drawLockText(GuiGraphics graphics, Font font, Component text, int x, int y,
                                   int color, boolean shadow, Operation<Integer> original) {
        return Minecraft.getInstance().screen instanceof OverloadedPatternProviderScreen<?>
                ? LDLibFonts.drawText(graphics, font, text, x, y, color, shadow)
                : original.call(graphics, font, text, x, y, color, shadow);
    }
}
