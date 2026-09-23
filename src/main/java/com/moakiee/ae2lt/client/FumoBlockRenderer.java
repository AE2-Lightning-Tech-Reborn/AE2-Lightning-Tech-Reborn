package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.block.FumoBlock;
import com.moakiee.ae2lt.blockentity.FumoBlockEntity;
import com.moakiee.ae2lt.registry.ModFumos;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Spins the Fumo model and submits its portal and marking layers when present. */
public final class FumoBlockRenderer implements BlockEntityRenderer<FumoBlockEntity, FumoBlockRenderer.State> {
    public static final class State extends BlockEntityRenderState {
        final BlockModelRenderState model = new BlockModelRenderState();
        BlockStateModel portalModel;
        BlockState blockState;
        boolean hyperdimensional;
        float yRotation;
    }

    public FumoBlockRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(FumoBlockEntity blockEntity, State state, float partialTick,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockState original = blockEntity.getBlockState();
        boolean spinning = blockEntity.isSpinning();
        state.blockState = original;
        state.hyperdimensional = original.is(ModFumos.HYPERDIMENSIONAL_PIGMEE_FUMO.get());
        state.yRotation = spinning ? blockEntity.getRenderYRot(partialTick) : 0.0F;
        BlockState rendered = spinning && original.hasProperty(FumoBlock.FACING)
                ? original.setValue(FumoBlock.FACING, Direction.NORTH) : original;
        var manager = Minecraft.getInstance().getModelManager();
        state.portalModel = manager.getBlockStateModelSet().get(rendered);
        if (!state.hyperdimensional) {
            state.model.clear();
            manager.getBlockModelSet().get(rendered).update(
                    state.model, rendered, BlockDisplayContext.create(), blockEntity.getBlockPos().asLong());
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        if (state.yRotation != 0.0F) {
            poseStack.translate(0.5D, 0.0D, 0.5D);
            poseStack.mulPose(Axis.YP.rotationDegrees(state.yRotation));
            poseStack.translate(-0.5D, 0.0D, -0.5D);
        }
        if (state.hyperdimensional) {
            if (state.portalModel != null) {
                HyperdimensionalPigmeePortalLayer.submit(state.portalModel, poseStack, collector);
            }
            HyperdimensionalPigmeeTextureLayer.submitBlock(state.blockState, poseStack, collector, 0);
        } else {
            state.model.submit(poseStack, collector, state.lightCoords, 0, 0);
        }
        poseStack.popPose();
    }
}
