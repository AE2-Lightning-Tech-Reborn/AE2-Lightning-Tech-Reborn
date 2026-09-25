package com.moakiee.ae2lt.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import com.moakiee.ae2lt.block.LightningSimulationChamberBlock;
import com.moakiee.ae2lt.blockentity.LightningSimulationChamberBlockEntity;
import com.moakiee.ae2lt.machine.lightningchamber.LightningSimulationChamberInventory;

public class LightningSimulationChamberRenderer
        implements BlockEntityRenderer<LightningSimulationChamberBlockEntity, LightningSimulationChamberRenderer.State> {
    private static final float ITEM_SCALE = 0.35F;
    private static final float ITEM_BASE_HEIGHT = 2.05F / 16.0F;
    private static final float ITEM_LAYER_OFFSET = 0.01F;
    private static final float ITEM_DEPTH = 0.50F;
    private static final float[] INPUT_X_POSITIONS = {0.34F, 0.50F, 0.66F};
    private final ItemModelResolver itemModelResolver;

    public static final class State extends BlockEntityRenderState {
        final ItemStackRenderState[] items = {
                new ItemStackRenderState(), new ItemStackRenderState(), new ItemStackRenderState()};
        Direction facing = Direction.NORTH;
    }

    public LightningSimulationChamberRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(LightningSimulationChamberBlockEntity blockEntity, State state, float partialTick,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, cameraPosition, breakProgress);
        var blockState = blockEntity.getBlockState();
        state.facing = blockState.hasProperty(LightningSimulationChamberBlock.FACING)
                ? blockState.getValue(LightningSimulationChamberBlock.FACING) : Direction.NORTH;
        for (int i = 0; i < state.items.length; i++) {
            itemModelResolver.updateForTopItem(state.items[i],
                    blockEntity.getInventory().getStackInSlot(LightningSimulationChamberInventory.SLOT_INPUT_0 + i),
                    ItemDisplayContext.FIXED, blockEntity.getLevel(), null,
                    (int) blockEntity.getBlockPos().asLong() + i);
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(state.facing.toYRot()));
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        for (int i = 0; i < state.items.length; i++) {
            if (state.items[i].isEmpty()) {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(INPUT_X_POSITIONS[i], ITEM_BASE_HEIGHT + ITEM_LAYER_OFFSET * i, ITEM_DEPTH);
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            state.items[i].submit(poseStack, collector, state.lightCoords, 0, 0);
            poseStack.popPose();
        }
        poseStack.popPose();
    }
}
