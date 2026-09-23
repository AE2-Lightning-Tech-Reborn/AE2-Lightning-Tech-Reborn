package com.moakiee.ae2lt.client;

import appeng.client.gui.style.Blitter;
import appeng.util.Icon;

/** Restores atlas subregions for the AE2 icons used by existing toolbar art. */
public final class LegacyAe2IconBlitter {
    private LegacyAe2IconBlitter() {}

    public static Blitter of(Icon icon) {
        return Blitter.texture(Icon.TEXTURE, Icon.TEXTURE_WIDTH, Icon.TEXTURE_HEIGHT)
                .src(icon.x, icon.y, icon.width, icon.height);
    }
}
