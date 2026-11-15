package com.moakiee.ae2lt.client.machine;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.integration.recipeviewer.multiblock.MultiblockStructureRecipe;
import com.moakiee.ae2lt.machine.largeoverload.LargeFactoryComponent;
import com.moakiee.ae2lt.machine.largeoverload.LargeFactoryControllerBlock;
import com.moakiee.ae2lt.machine.largeoverload.LargeFactoryRegistration;
import com.moakiee.ae2lt.machine.largeoverload.LargeFactoryStructure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/** One local, read-only projection, using the same cells as the recipe viewers. */
@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class LargeFactoryProjection {
    private static ClientLevel level;
    private static BlockPos anchor;
    private static Direction facing;
    private static MultiblockStructureRecipe recipe;

    private LargeFactoryProjection() { }

    public static boolean supports(MultiblockStructureRecipe value) {
        return value.id().getNamespace().equals(AE2LightningTech.MODID)
                && (value.id().getPath().equals("large_overload_factory")
                    || value.id().getPath().equals("large_overload_factory_firmament"));
    }

    public static boolean active() { return recipe != null && level == Minecraft.getInstance().level; }

    public static void toggle(MultiblockStructureRecipe value, BlockPos controller, Direction direction) {
        var client = Minecraft.getInstance();
        if (client.level == null || !supports(value)) return;
        if (active() && controller.equals(anchor) && direction == facing && recipe.id().equals(value.id())) {
            clear();
        } else {
            level = client.level;
            anchor = controller.immutable();
            facing = direction;
            recipe = value;
        }
    }

    public static void toggleFromCrosshair(MultiblockStructureRecipe value) {
        var client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;
        if (active()) { clear(); return; }
        if (!(client.hitResult instanceof BlockHitResult hit)) return;
        var state = client.level.getBlockState(hit.getBlockPos());
        boolean controller = LargeFactoryRegistration.component(state) == LargeFactoryComponent.CONTROLLER;
        toggle(value, controller ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection()),
                controller ? state.getValue(LargeFactoryControllerBlock.FACING) : client.player.getDirection().getOpposite());
        client.player.closeContainer();
    }

    private static void clear() { recipe = null; level = null; anchor = null; facing = null; }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || recipe == null) return;
        var client = Minecraft.getInstance();
        if (level != client.level) { clear(); return; }
        if (client.player == null || client.player.distanceToSqr(anchor.getCenter()) > 64 * 64) return;
        var pose = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        var buffers = client.renderBuffers().bufferSource();
        var type = RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
        var rotation = switch (facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        pose.pushPose();
        try {
            pose.translate(-camera.x, -camera.y, -camera.z);
            for (var cell : recipe.cells()) {
                var pos = LargeFactoryStructure.worldPosition(anchor, cell.localPos(), facing);
                if (!level.isLoaded(pos)) continue;
                var actual = level.getBlockState(pos);
                if (cell.alternatives().contains(actual.getBlock())) continue;
                pose.pushPose();
                try {
                    pose.translate(pos.getX(), pos.getY(), pos.getZ());
                    if (actual.isAir()) {
                        client.getBlockRenderer().renderSingleBlock(cell.state().rotate(rotation), pose,
                                ignored -> new GhostVertexConsumer(buffers.getBuffer(type)),
                                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, type);
                    } else {
                        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()),
                                -0.002, -0.002, -0.002, 1.002, 1.002, 1.002, 1, 0.2f, 0.2f, 0.9f);
                    }
                } finally { pose.popPose(); }
            }
            buffers.endBatch(type);
            buffers.endBatch(RenderType.lines());
        } finally { pose.popPose(); }
    }

    /** Keep baked textures and normals while tinting every vertex translucent cyan. */
    private record GhostVertexConsumer(VertexConsumer delegate) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x, float y, float z) { delegate.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { delegate.setColor(r * 3 / 5, g, b, a * 96 / 255); return this; }
        @Override public VertexConsumer setUv(float u, float v) { delegate.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { delegate.setNormal(x, y, z); return this; }
    }
}
