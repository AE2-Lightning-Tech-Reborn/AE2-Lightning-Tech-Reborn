package com.moakiee.ae2lt.mixin.client;

import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.AECheckbox;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.TabButton;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.client.ldlib.LdlibControlSprite;
import com.moakiee.ae2lt.client.ldlib.LdlibToolbarHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = {IconButton.class, TabButton.class, AECheckbox.class}, remap = false)
public abstract class InheritedControlSpriteMixin {
    @WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/style/Blitter;blit(Lnet/minecraft/client/gui/GuiGraphics;)V"))
    private void ae2lt$drawAtlasSprite(Blitter image, GuiGraphics graphics, Operation<Void> original) {
        if (!LdlibToolbarHost.usesLdlibControls(Minecraft.getInstance().screen)
                || !LdlibControlSprite.draw(image, graphics)) {
            original.call(image, graphics);
        }
    }
}
