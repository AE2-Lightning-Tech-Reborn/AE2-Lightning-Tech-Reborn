package com.moakiee.ae2lt.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Keeps legacy count labels on their original explicit buffer submission boundary. */
@Mixin(GuiGraphics.class)
public interface GuiGraphicsBatchAccessor {
    @Accessor("managed")
    boolean ae2lt$isManaged();

    @Accessor("managed")
    void ae2lt$setManaged(boolean managed);
}
