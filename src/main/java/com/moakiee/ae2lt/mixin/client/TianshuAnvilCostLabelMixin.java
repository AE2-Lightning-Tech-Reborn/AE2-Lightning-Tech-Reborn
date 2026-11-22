package com.moakiee.ae2lt.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib2.gui.LDLibFonts;
import com.moakiee.ae2lt.client.tianshu.TianshuAnvilCostView;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AnvilScreen.class)
public abstract class TianshuAnvilCostLabelMixin {
    // A mod that replaces the native cost draw can still supply its own label.
    @WrapOperation(method = "renderLabels", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"))
    private int ae2lt$drawTerminalCost(GuiGraphics graphics, Font font, Component label, int x, int y,
                                       int color, Operation<Integer> original) {
        return (Object) this instanceof TianshuAnvilCostView
                ? LDLibFonts.drawText(graphics, font, label, x, y, color, true)
                : original.call(graphics, font, label, x, y, color);
    }
}
