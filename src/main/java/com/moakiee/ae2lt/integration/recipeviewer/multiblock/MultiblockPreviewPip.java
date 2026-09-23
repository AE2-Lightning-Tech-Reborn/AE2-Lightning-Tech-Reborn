package com.moakiee.ae2lt.integration.recipeviewer.multiblock;

import java.util.List;

import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import com.moakiee.ae2lt.AE2LightningTech;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

/** Captures the existing multiblock previews for Minecraft's deferred GUI renderer. */
@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public final class MultiblockPreviewPip {
    private MultiblockPreviewPip() {}

    public record Block(BlockState state, int x, int y, int z, int outlineColor) {}

    public record State(List<Block> blocks, int x0, int y0, int x1, int y1,
                        float scale, float pitch, float yaw, float panX, float panY,
                        float centerX, float centerY, float centerZ, float depthScale,
                        @Nullable ScreenRectangle scissorArea) implements PictureInPictureRenderState {
        @Override
        public @Nullable ScreenRectangle bounds() {
            return PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea);
        }
    }

    @SubscribeEvent
    public static void register(RegisterPictureInPictureRenderersEvent event) {
        event.register(State.class, Renderer::new);
    }

    public static void submit(GuiGraphicsExtractor graphics, int left, int top, int width, int height,
                              float scale, float pitch, float yaw, float panX, float panY,
                              float centerX, float centerY, float centerZ, float depthScale,
                              List<Block> blocks) {
        Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        Vector2f first = pose.transformPosition(left, top, new Vector2f());
        Vector2f second = pose.transformPosition(left + width, top + height, new Vector2f());
        int x0 = (int) Math.floor(Math.min(first.x, second.x));
        int y0 = (int) Math.floor(Math.min(first.y, second.y));
        int x1 = (int) Math.ceil(Math.max(first.x, second.x));
        int y1 = (int) Math.ceil(Math.max(first.y, second.y));
        if (x1 <= x0 || y1 <= y0 || blocks.isEmpty()) return;
        float guiScale = (x1 - x0) / (float) width;
        graphics.submitPictureInPictureRenderState(new State(
                List.copyOf(blocks), x0, y0, x1, y1, scale * guiScale,
                pitch, yaw, panX * guiScale, panY * guiScale,
                centerX, centerY, centerZ, depthScale, graphics.peekScissorStack()));
    }

    private static final class Renderer extends PictureInPictureRenderer<State> {
        private final BlockModelRenderState model = new BlockModelRenderState();

        private Renderer(MultiBufferSource.BufferSource buffers) {
            super(buffers);
        }

        @Override
        public Class<State> getRenderStateClass() {
            return State.class;
        }

        @Override
        protected float getTranslateY(int height, int guiScale) {
            return height / 2.0F;
        }

        @Override
        protected void renderToTexture(State state, PoseStack pose) {
            Minecraft client = Minecraft.getInstance();
            client.gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
            pose.translate(state.panX() / state.scale(), -state.panY() / state.scale(), 0.0F);
            pose.scale(1.0F, 1.0F, state.depthScale());
            pose.mulPose(Axis.XP.rotationDegrees(state.pitch()));
            pose.mulPose(Axis.YP.rotationDegrees(state.yaw()));
            pose.translate(-state.centerX(), -state.centerY(), -state.centerZ());
            BlockModelResolver resolver = new BlockModelResolver(client.getModelManager());
            var dispatcher = client.gameRenderer.getFeatureRenderDispatcher();
            var collector = dispatcher.getSubmitNodeStorage();
            for (Block block : state.blocks()) {
                pose.pushPose();
                pose.translate(block.x(), block.y(), block.z());
                resolver.update(model, block.state(), BlockDisplayContext.create());
                model.submitMultiLayer(pose, collector, LightCoordsUtil.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, block.outlineColor());
                if (block.outlineColor() != 0) {
                    model.submitOnlyOutline(pose, collector, LightCoordsUtil.FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY, block.outlineColor());
                }
                pose.popPose();
            }
            dispatcher.renderAllFeatures();
        }

        @Override
        protected String getTextureLabel() {
            return "AE2LT multiblock preview";
        }
    }
}
