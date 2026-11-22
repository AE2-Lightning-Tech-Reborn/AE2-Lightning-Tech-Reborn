package com.moakiee.ae2lt.client.ldlib;

import org.jetbrains.annotations.Nullable;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.AESubScreen;
import appeng.menu.AEBaseMenu;
import net.minecraft.client.gui.GuiGraphics;

public abstract class LdlibAeSubScreen<M extends AEBaseMenu, P extends AEBaseScreen<M>>
        extends AESubScreen<M, P> implements LdlibToolbarHost {
    private final @Nullable BackgroundLayer background;

    protected LdlibAeSubScreen(P parent, String stylePath) {
        super(parent, stylePath);
        background = BackgroundLayer.fromStyle(style);
    }

    protected final void releaseBackground() {
        if (background != null) {
            background.onRemoved();
        }
    }

    @Override
    protected void init() {
        releaseBackground();
        super.init();
    }

    @Override
    protected void onReturnToParent() {
        releaseBackground();
        super.onReturnToParent();
    }

    @Override
    public void removed() {
        try {
            releaseBackground();
        } finally {
            super.removed();
        }
    }

    @Override
    public void drawBG(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float ticks) {
        if (background == null) {
            super.drawBG(graphics, x, y, mouseX, mouseY, ticks);
        } else {
            background.render(graphics, x, y, mouseX, mouseY, ticks);
        }
    }
}
