package com.moakiee.ae2lt.mixin.client;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateAccessor {
    @Accessor("activeLayerCount") int ae2lt$activeLayerCount();
    @Accessor("layers") ItemStackRenderState.LayerRenderState[] ae2lt$layers();
}
