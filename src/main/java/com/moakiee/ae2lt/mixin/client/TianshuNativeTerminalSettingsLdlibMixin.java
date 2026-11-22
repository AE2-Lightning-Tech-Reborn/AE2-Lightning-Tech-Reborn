package com.moakiee.ae2lt.mixin.client;

import appeng.client.gui.AESubScreen;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.TerminalSettingsScreen;
import appeng.menu.me.common.MEStorageMenu;
import com.moakiee.ae2lt.client.ldlib.BackgroundLayer;
import com.moakiee.ae2lt.menu.TianshuMaintenanceMenu;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TerminalSettingsScreen.class, remap = false)
public abstract class TianshuNativeTerminalSettingsLdlibMixin<M extends MEStorageMenu>
        extends AESubScreen<M, MEStorageScreen<M>> {
    @Unique private BackgroundLayer ae2lt$background;

    protected TianshuNativeTerminalSettingsLdlibMixin(MEStorageScreen<M> parent, String stylePath) {
        super(parent, stylePath);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ae2lt$buildBackground(MEStorageScreen<M> parent, CallbackInfo ci) {
        if (menu instanceof TianshuMaintenanceMenu) {
            ae2lt$background = BackgroundLayer.fromStyle(style);
        }
    }

    @Unique
    private void ae2lt$releaseBackground() {
        if (ae2lt$background != null) {
            ae2lt$background.onRemoved();
        }
    }

    @Inject(method = "init()V", at = @At("HEAD"))
    private void ae2lt$releaseBackgroundBeforeInit(CallbackInfo ci) {
        ae2lt$releaseBackground();
    }

    @Override
    protected void onReturnToParent() {
        try {
            ae2lt$releaseBackground();
        } finally {
            super.onReturnToParent();
        }
    }

    @Override
    public void removed() {
        try {
            ae2lt$releaseBackground();
        } finally {
            super.removed();
        }
    }

    @Override
    public void drawBG(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float ticks) {
        if (ae2lt$background != null) {
            ae2lt$background.render(graphics, x, y, mouseX, mouseY, ticks);
        } else {
            super.drawBG(graphics, x, y, mouseX, mouseY, ticks);
        }
    }
}
