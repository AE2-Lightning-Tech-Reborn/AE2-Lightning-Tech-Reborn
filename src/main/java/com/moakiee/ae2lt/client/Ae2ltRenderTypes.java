package com.moakiee.ae2lt.client;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/** The 26.1 debug filled-box pipeline supplies translucent, depth-independent overlays. */
public final class Ae2ltRenderTypes {
    private Ae2ltRenderTypes() {}

    public static RenderType getFaceSeeThrough() {
        return RenderTypes.debugFilledBox();
    }
}
