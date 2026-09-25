package com.moakiee.ae2lt.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import com.moakiee.ae2lt.blockentity.LightningAssemblyChamberBlockEntity;

public class LightningAssemblyChamberRenderer
        implements BlockEntityRenderer<LightningAssemblyChamberBlockEntity, LightningAssemblyChamberRenderer.State> {
    private final ItemModelResolver itemModelResolver;

    public static final class State extends BlockEntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        boolean blockItem;
    }

    public LightningAssemblyChamberRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(LightningAssemblyChamberBlockEntity blockEntity, State state, float partialTick,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, cameraPosition, breakProgress);
        var stack = blockEntity.getClientRecipeResult();
        state.blockItem = stack.getItem() instanceof BlockItem;
        itemModelResolver.updateForTopItem(state.item, stack, ItemDisplayContext.GROUND,
                blockEntity.getLevel(), null, (int) blockEntity.getBlockPos().asLong());
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.item.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5D, state.blockItem ? 0.3D : 0.2D, 0.5D);
        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
