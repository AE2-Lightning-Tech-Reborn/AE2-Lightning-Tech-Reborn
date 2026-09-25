package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.mixin.client.ItemStackRenderStateAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Replaces the normal item shell with the portal and full-bright markings. */
final class HyperdimensionalPigmeeBakedModel extends SpinningFumoBakedModel {
    HyperdimensionalPigmeeBakedModel(ItemModel originalModel) { super(originalModel); }

    @Override
    public void update(ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver,
                       ItemDisplayContext displayContext, @Nullable ClientLevel level,
                       @Nullable ItemOwner owner, int seed) {
        var access = (ItemStackRenderStateAccessor) state;
        int firstLayer = access.ae2lt$activeLayerCount();
        super.update(state, stack, resolver, displayContext, level, owner, seed);
        if (firstLayer == access.ae2lt$activeLayerCount()) return;
        // Keep the baked extents and transforms, but do not draw the ordinary
        // opaque Pigmee body underneath the portal shell (as in 1.21.1's BEWLR).
        access.ae2lt$layers()[firstLayer].setupSpecialModel(HyperdimensionalPigmeeItemRenderer.INSTANCE, null);
    }
}
