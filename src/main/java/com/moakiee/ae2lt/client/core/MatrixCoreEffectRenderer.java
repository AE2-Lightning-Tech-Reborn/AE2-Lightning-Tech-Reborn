package com.moakiee.ae2lt.client.core;

import com.moakiee.ae2lt.block.MatrixControllerBlock;
import com.moakiee.ae2lt.blockentity.MatrixControllerBlockEntity;
import com.moakiee.ae2lt.config.AE2LTClientConfig;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockComponent;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockScanner;
import com.moakiee.ae2lt.logic.craft.MatrixMultiblockTemplate;
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

public final class MatrixCoreEffectRenderer
        implements BlockEntityRenderer<MatrixControllerBlockEntity, MatrixCoreEffectRenderer.State> {
    private static final CoreEffectAnimationState.MotionProfile MOTION =
            new CoreEffectAnimationState.MotionProfile(
                    8.0D, 48.0D, 36_000.0D,
                    22.0D, 84.0D, 36_000.0D,
                    1.2D, 4.2D);
    private final Map<MatrixControllerBlockEntity, CoreEffectAnimationState> animations = new WeakHashMap<>();

    public static final class State extends BlockEntityRenderState {
        boolean visible;
        double x, y, z;
        CoreEffectPalette palette;
        CoreEffectAnimationState.Sample animation;
    }

    public MatrixCoreEffectRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MatrixControllerBlockEntity controller, State output, float partialTick,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(controller, output, partialTick, cameraPosition, breakProgress);
        var blockState = controller.getBlockState();
        output.visible = AE2LTClientConfig.useCoreShaderRendering()
                && AE2LTClientConfig.renderMultiblockCoreEffects()
                && blockState.hasProperty(MatrixControllerBlock.FORMED)
                && blockState.getValue(MatrixControllerBlock.FORMED)
                && controller.getLevel() != null;
        if (!output.visible) {
            return;
        }
        Direction facing = controller.getOrientation();
        BlockPos center = MatrixMultiblockScanner.worldPos(
                controller.getBlockPos(), MatrixMultiblockTemplate.CRAFTING_CENTER_LOCAL, facing);
        var component = MatrixMultiblockScanner.componentAt(controller.getLevel(), center);
        output.palette = palette(component);
        boolean working = blockState.hasProperty(MatrixControllerBlock.WORKING)
                && blockState.getValue(MatrixControllerBlock.WORKING);
        double renderTick = controller.getLevel().getGameTime() + partialTick;
        output.animation = animations.computeIfAbsent(controller, ignored -> new CoreEffectAnimationState())
                .sample(renderTick, working, MOTION);
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
        CoreEffectGeometry.renderMatrix(stack, collector, state.palette, state.animation);
        stack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    private static CoreEffectPalette palette(MatrixMultiblockComponent component) {
        return switch (component) {
            case QUANTUM_MAIN_CORE -> new CoreEffectPalette(0.22F, 0.62F, 0.78F, 0.58F, 0.86F, 0.92F);
            case OVERLOAD_MAIN_CORE -> new CoreEffectPalette(0.78F, 0.26F, 0.10F, 0.96F, 0.60F, 0.22F);
            case MULTIDIMENSIONAL_MAIN_CORE -> new CoreEffectPalette(0.54F, 0.20F, 0.66F, 0.18F, 0.70F, 0.62F);
            default -> new CoreEffectPalette(0.36F, 0.58F, 0.70F, 0.72F, 0.82F, 0.86F);
        };
    }
}
