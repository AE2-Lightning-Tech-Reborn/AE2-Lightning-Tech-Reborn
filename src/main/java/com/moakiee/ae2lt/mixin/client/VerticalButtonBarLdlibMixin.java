package com.moakiee.ae2lt.mixin.client;

import java.util.function.Consumer;

import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.widgets.VerticalButtonBar;
import com.lowdragmc.lowdraglib2.gui.texture.VanillaSpriteTexture;
import com.moakiee.ae2lt.client.ldlib.LdlibToolbarHost;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VerticalButtonBar.class, remap = false)
public abstract class VerticalButtonBarLdlibMixin {
    @Unique private static final VanillaSpriteTexture AE2LT_BACKGROUND =
            VanillaSpriteTexture.of("ae2:vertical_buttons_bg");

    @Unique private boolean ae2lt$ldlibBackground;

    @Shadow public abstract Rect2i getBounds();

    @Inject(method = "populateScreen", at = @At("HEAD"))
    private void ae2lt$chooseBackground(Consumer<AbstractWidget> widgets, Rect2i bounds,
                                       AEBaseScreen<?> owner, CallbackInfo ci) {
        ae2lt$ldlibBackground = LdlibToolbarHost.usesLdlibControls(owner);
    }

    @Inject(method = "drawBackgroundLayer", at = @At("HEAD"), cancellable = true)
    private void ae2lt$drawLdlibBackground(GuiGraphics graphics, Rect2i screen, Point mouse, CallbackInfo ci) {
        if (!ae2lt$ldlibBackground) {
            return;
        }
        var bounds = getBounds();
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(0, 0, 1);
            AE2LT_BACKGROUND.draw(graphics, mouse.getX(), mouse.getY(),
                    screen.getX() + bounds.getX() - 2, screen.getY() + bounds.getY() - 1,
                    bounds.getWidth() + 1, bounds.getHeight() + 4, 0);
        } finally {
            pose.popPose();
        }
        ci.cancel();
    }
}
