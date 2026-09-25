package com.moakiee.ae2lt.client;

import appeng.blockentity.crafting.MolecularAssemblerAnimationStatus;
import appeng.core.particles.ParticleTypes;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.PigmeeMolecularAssemblerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import org.jspecify.annotations.Nullable;

public final class PigmeeMolecularAssemblerRenderer implements
        BlockEntityRenderer<PigmeeMolecularAssemblerBlockEntity, PigmeeMolecularAssemblerRenderer.State> {
    public static final Identifier LIGHTS_MODEL_ID = Identifier.fromNamespaceAndPath(
            AE2LightningTech.MODID, "block/pigmee_molecular_assembler_lights");
    public static final StandaloneModelKey<BlockStateModelPart> LIGHTS_MODEL =
            new StandaloneModelKey<>(LIGHTS_MODEL_ID::toString);

    public static final class State extends BlockEntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        boolean powered;
        boolean blockItem;
    }

    private final RandomSource particleRandom = RandomSource.create();
    private final ItemModelResolver itemModelResolver;

    public PigmeeMolecularAssemblerRenderer(BlockEntityRendererProvider.Context context) {
        itemModelResolver = context.itemModelResolver();
    }

    @Override public State createRenderState() { return new State(); }

    @Override
    public void extractRenderState(PigmeeMolecularAssemblerBlockEntity blockEntity, State state, float partialTick,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, cameraPosition, breakProgress);
        state.powered = blockEntity.isPowered();
        MolecularAssemblerAnimationStatus status = blockEntity.getAnimationStatus();
        state.item.clear();
        if (status == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isPaused()) {
            if (status.isExpired()) blockEntity.setAnimationStatus(null);
            status.setAccumulatedTicks(status.getAccumulatedTicks() + partialTick);
            status.setTicksUntilParticles(status.getTicksUntilParticles() - partialTick);
        }
        var stack = status.getIs();
        state.blockItem = stack.getItem() instanceof BlockItem;
        itemModelResolver.updateForTopItem(state.item, stack, ItemDisplayContext.GROUND,
                blockEntity.getLevel(), null, (int) blockEntity.getBlockPos().asLong());
        if (status.getTicksUntilParticles() <= 0) {
            status.setTicksUntilParticles(4);
            double x = blockEntity.getBlockPos().getX() + 0.5D;
            double y = blockEntity.getBlockPos().getY() + 0.5D;
            double z = blockEntity.getBlockPos().getZ() + 0.5D;
            for (int i = 0; i < (int) Math.ceil(status.getSpeed() / 5.0D); i++) {
                if (particleRandom.nextInt(4) == 0) {
                    minecraft.particleEngine.createParticle(
                            new ItemParticleOption(ParticleTypes.CRAFTING, stack.getItem()),
                            x, y, z, 0.0D, 0.0D, 0.0D);
                }
            }
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.item.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0.5D, 0.5D, 0.5D);
            poseStack.translate(0.0D, state.blockItem ? -0.2F : -0.3F, 0.0D);
            state.item.submit(poseStack, collector, state.lightCoords, 0, 0);
            poseStack.popPose();
        }
        if (state.powered) {
            BlockStateModelPart lights = Minecraft.getInstance().getModelManager().getStandaloneModel(LIGHTS_MODEL);
            if (lights != null) collector.submitBlockModel(poseStack,
                    RenderTypes.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS),
                    java.util.List.of(lights), BlockModelRenderState.EMPTY_TINTS, state.lightCoords, 0, 0);
        }
    }
}
