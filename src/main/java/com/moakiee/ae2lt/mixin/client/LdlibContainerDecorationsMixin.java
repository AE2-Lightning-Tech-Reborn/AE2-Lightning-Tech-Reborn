package com.moakiee.ae2lt.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.client.ldlib.LdlibScreens;
import com.moakiee.ae2lt.client.ldlib.LdlibSlotHost;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractContainerScreen.class)
public abstract class LdlibContainerDecorationsMixin {
    @WrapOperation(method = "renderSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(IIIIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"))
    private void ae2lt$drawEmptySlotIcon(GuiGraphics graphics, int x, int y, int z, int width, int height,
            TextureAtlasSprite sprite, Operation<Void> original) {
        if (width == 16 && height == 16 && ae2lt$usesLdlibSlotDecorations()) {
            LdlibScreens.drawAtlasIcon(graphics, x, y, z, sprite);
        } else {
            original.call(graphics, x, y, z, width, height, sprite);
        }
    }

    @Unique
    private boolean ae2lt$usesLdlibSlotDecorations() {
        return LdlibSlotHost.usesLdlibSlotDecorations((Screen) (Object) this);
    }
}
