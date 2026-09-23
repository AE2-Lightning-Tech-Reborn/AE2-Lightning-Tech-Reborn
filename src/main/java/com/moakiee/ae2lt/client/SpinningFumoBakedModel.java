package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.blockentity.FumoBlockEntity;
import com.moakiee.ae2lt.mixin.client.ItemLayerRenderStateAccessor;
import com.moakiee.ae2lt.mixin.client.ItemStackRenderStateAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/** Applies the original head-slot spin after the normal item model transform. */
class SpinningFumoBakedModel implements ItemModel {
    protected final ItemModel originalModel;

    SpinningFumoBakedModel(ItemModel originalModel) { this.originalModel = originalModel; }

    @Override
    public void update(ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver,
                       ItemDisplayContext displayContext, @Nullable ClientLevel level,
                       @Nullable ItemOwner owner, int seed) {
        originalModel.update(state, stack, resolver, displayContext, level, owner, seed);
        if (displayContext != ItemDisplayContext.HEAD || level == null) return;
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float angle = (level.getGameTime() % 60L + partialTick) * FumoBlockEntity.SPIN_DEGREES_PER_TICK;
        var access = (ItemStackRenderStateAccessor) state;
        for (int i = 0; i < access.ae2lt$activeLayerCount(); i++) {
            var layer = access.ae2lt$layers()[i];
            Matrix4f transform = new Matrix4f(((ItemLayerRenderStateAccessor) layer).ae2lt$localTransform());
            transform.rotateY((float) Math.toRadians(angle));
            layer.setLocalTransform(transform);
        }
        state.setAnimated();
    }
}
