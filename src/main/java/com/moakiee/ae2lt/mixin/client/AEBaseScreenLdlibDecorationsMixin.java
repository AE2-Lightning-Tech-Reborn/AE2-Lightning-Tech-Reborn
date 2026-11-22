package com.moakiee.ae2lt.mixin.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.Blitter;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib2.gui.LDLibFonts;
import com.moakiee.ae2lt.client.ldlib.LdlibControlSprite;
import com.moakiee.ae2lt.client.ldlib.LdlibToolbarHost;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = AEBaseScreen.class, remap = false)
public abstract class AEBaseScreenLdlibDecorationsMixin {
    @WrapOperation(method = "drawText", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I"))
    private int ae2lt$drawStyleText(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y,
                                    int color, boolean shadow, Operation<Integer> original) {
        return ae2lt$usesLdlib() ? LDLibFonts.drawText(graphics, font, text, x, y, color, shadow)
                : original.call(graphics, font, text, x, y, color, shadow);
    }

    @WrapOperation(method = {"drawBG", "drawOptionalSlotBackground", "renderAppEngSlot"},
            at = @At(value = "INVOKE",
                    target = "Lappeng/client/gui/style/Blitter;blit(Lnet/minecraft/client/gui/GuiGraphics;)V"))
    private void ae2lt$drawStyleSprite(Blitter image, GuiGraphics graphics, Operation<Void> original) {
        if (!ae2lt$usesLdlib() || !LdlibControlSprite.draw(image, graphics)) {
            original.call(image, graphics);
        }
    }

    @Unique
    private boolean ae2lt$usesLdlib() {
        return LdlibToolbarHost.usesLdlibControls((AEBaseScreen<?>) (Object) this);
    }
}
