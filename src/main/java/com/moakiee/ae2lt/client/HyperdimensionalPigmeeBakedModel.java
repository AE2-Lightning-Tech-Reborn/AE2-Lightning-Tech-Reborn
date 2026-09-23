package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.mixin.client.ItemLayerRenderStateAccessor;
import com.moakiee.ae2lt.mixin.client.ItemStackRenderStateAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Adds portal and full-bright markings after the normal item model. */
final class HyperdimensionalPigmeeBakedModel extends SpinningFumoBakedModel {
    HyperdimensionalPigmeeBakedModel(ItemModel originalModel) { super(originalModel); }

    @Override
    public void update(ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver,
                       ItemDisplayContext displayContext, @Nullable ClientLevel level,
                       @Nullable ItemOwner owner, int seed) {
        super.update(state, stack, resolver, displayContext, level, owner, seed);
        var access = (ItemStackRenderStateAccessor) state;
        int count = access.ae2lt$activeLayerCount();
        var special = state.newLayer();
        if (count > 0) {
            var first = (ItemLayerRenderStateAccessor) access.ae2lt$layers()[0];
            special.setItemTransform(first.ae2lt$itemTransform());
            special.setLocalTransform(first.ae2lt$localTransform());
        }
        special.setupSpecialModel(HyperdimensionalPigmeeItemRenderer.INSTANCE, null);
    }
}
