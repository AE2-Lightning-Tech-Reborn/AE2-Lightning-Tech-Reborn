package com.moakiee.ae2lt.mixin.client;

import appeng.client.gui.widgets.AECheckbox;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib2.gui.LDLibFonts;
import com.moakiee.ae2lt.client.ldlib.LdlibToolbarHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = AECheckbox.class, remap = false)
public abstract class AECheckboxLabelLdlibMixin {
    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I"))
    private int ae2lt$addLabel(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y,
                               int color, boolean shadow, Operation<Integer> original) {
        return LdlibToolbarHost.usesLdlibControls(Minecraft.getInstance().screen)
                ? LDLibFonts.drawText(graphics, font, text, x, y, color, shadow)
                : original.call(graphics, font, text, x, y, color, shadow);
    }
}
