package com.moakiee.ae2lt.client.core;

import com.moakiee.ae2lt.block.TianshuSupercomputerControllerBlock;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerControllerBlockEntity;
import com.moakiee.ae2lt.config.AE2LTClientConfig;
import com.moakiee.ae2lt.logic.tianshu.TianshuMultiblockScanner;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

public final class TianshuCoreEffectRenderer
        implements BlockEntityRenderer<TianshuSupercomputerControllerBlockEntity, TianshuCoreEffectRenderer.State> {
    private static final BlockPos CORE_LOCAL = new BlockPos(3, 3, 3);
    private static final CoreEffectPalette CORE_PALETTE =
            new CoreEffectPalette(0.30F, 0.12F, 0.50F, 0.80F, 0.48F, 1.00F);
    private static final CoreEffectAnimationState.MotionProfile MOTION =
            new CoreEffectAnimationState.MotionProfile(
                    1.0D / 5.5D, 1.0D / 0.72D, 18.0D,
                    3.0D, 30.0D, 360.0D,
                    0.0D, 0.0D);
    private final Map<TianshuSupercomputerControllerBlockEntity, CoreEffectAnimationState> animations = new WeakHashMap<>();

    public static final class State extends BlockEntityRenderState {
        boolean visible;
        double x, y, z;
        double stepPhase, spinDegrees;
    }

    public TianshuCoreEffectRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TianshuSupercomputerControllerBlockEntity controller, State output, float partialTick,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(controller, output, partialTick, cameraPosition, breakProgress);
        var blockState = controller.getBlockState();
        output.visible = AE2LTClientConfig.useCoreShaderRendering()
                && AE2LTClientConfig.renderMultiblockCoreEffects()
                && blockState.hasProperty(TianshuSupercomputerControllerBlock.FORMED)
                && blockState.getValue(TianshuSupercomputerControllerBlock.FORMED)
                && controller.getLevel() != null;
        if (!output.visible) {
            return;
        }
        Direction facing = blockState.getValue(TianshuSupercomputerControllerBlock.FACING);
        BlockPos center = TianshuMultiblockScanner.worldPos(controller.getBlockPos(), CORE_LOCAL, facing);
        boolean working = blockState.hasProperty(TianshuSupercomputerControllerBlock.WORKING)
                && blockState.getValue(TianshuSupercomputerControllerBlock.WORKING);
        double renderTick = controller.getLevel().getGameTime() + partialTick;
        var animation = animations.computeIfAbsent(controller, ignored -> new CoreEffectAnimationState())
                .sample(renderTick, working, MOTION);
        output.stepPhase = animation.primaryPhase();
        output.spinDegrees = animation.secondaryPhase();
        output.x = center.getX() + 0.5D - controller.getBlockPos().getX();
        output.y = center.getY() + 0.5D - controller.getBlockPos().getY();
        output.z = center.getZ() + 0.5D - controller.getBlockPos().getZ();
    }

    @Override
    public void submit(State state, PoseStack stack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.visible) {
            return;
        }
        stack.pushPose();
        stack.translate(state.x, state.y, state.z);
        CoreEffectGeometry.renderTianshu(stack, collector, CORE_PALETTE, state.stepPhase, state.spinDegrees);
        stack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
