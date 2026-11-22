package com.moakiee.ae2lt.client.ldlib;

import appeng.client.gui.style.Blitter;
import net.minecraft.client.gui.GuiGraphics;

/** Preserves AE2's immediate texture contract inside the inherited-control bridge. */
public final class LdlibControlSprite {
    private LdlibControlSprite() {
    }

    public static boolean draw(Blitter image, GuiGraphics graphics) {
        // SpriteTexture batching changes inherited depth/write state and blend factors.
        // Keep the caller's full Blitter, including transforms, automatic size and Z.
        image.blit(graphics);
        return true;
    }
}
