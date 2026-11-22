package com.moakiee.ae2lt.client.equipment;

import com.lowdragmc.lowdraglib2.client.model.ModelFactory;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import appeng.api.client.AEKeyRenderHandler;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.me.key.LightningKey;

public final class LightningKeyRenderHandler implements AEKeyRenderHandler<LightningKey> {
    public static final LightningKeyRenderHandler INSTANCE = new LightningKeyRenderHandler();

    private static final ResourceLocation HIGH_VOLTAGE_SPRITE =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "item/high_voltage_lightning");
    private static final ResourceLocation EXTREME_HIGH_VOLTAGE_SPRITE =
            ResourceLocation.fromNamespaceAndPath(AE2LightningTech.MODID, "item/extreme_high_voltage_lightning");

    private static final IGuiTexture HIGH_VOLTAGE_ICON = atlasIcon(HIGH_VOLTAGE_SPRITE);
    private static final IGuiTexture EXTREME_HIGH_VOLTAGE_ICON = atlasIcon(EXTREME_HIGH_VOLTAGE_SPRITE);

    private LightningKeyRenderHandler() {
    }

    /** Fixed-size 16-pixel icon backed by the animated block atlas. */
    public static IGuiTexture guiIcon(LightningKey stack) {
        return stack.tier() == LightningKey.Tier.EXTREME_HIGH_VOLTAGE
                ? EXTREME_HIGH_VOLTAGE_ICON
                : HIGH_VOLTAGE_ICON;
    }

    private static IGuiTexture atlasIcon(ResourceLocation id) {
        return (graphics, mouseX, mouseY, x, y, width, height, ticks) ->
                appeng.client.gui.style.Blitter.sprite(ModelFactory.getBlockSprite(id))
                        .dest((int) x, (int) y, 16, 16).blit(graphics);
    }

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphics guiGraphics, int x, int y, LightningKey stack) {
        guiIcon(stack).draw(guiGraphics, 0, 0, x, y, 16, 16, 0);
    }

    @Override
    public void drawOnBlockFace(PoseStack poseStack, MultiBufferSource buffers, LightningKey what, float scale,
            int combinedLight, Level level) {
        var sprite = ModelFactory.getBlockSprite(what.tier() == LightningKey.Tier.EXTREME_HIGH_VOLTAGE
                ? EXTREME_HIGH_VOLTAGE_SPRITE
                : HIGH_VOLTAGE_SPRITE);

        poseStack.pushPose();
        poseStack.translate(0, 0, 0.01f);

        var buffer = buffers.getBuffer(RenderType.cutout());

        // Match FluidKeyRenderHandler: shrink slightly since sprites typically fill the full texel.
        scale -= 0.05f;
        float x0 = -scale / 2f;
        float y0 = scale / 2f;
        float x1 = scale / 2f;
        float y1 = -scale / 2f;

        var transform = poseStack.last().pose();
        buffer.addVertex(transform, x0, y1, 0)
                .setColor(0xFFFFFFFF)
                .setUv(sprite.getU0(), sprite.getV1())
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(combinedLight)
                .setNormal(0, 0, 1);
        buffer.addVertex(transform, x1, y1, 0)
                .setColor(0xFFFFFFFF)
                .setUv(sprite.getU1(), sprite.getV1())
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(combinedLight)
                .setNormal(0, 0, 1);
        buffer.addVertex(transform, x1, y0, 0)
                .setColor(0xFFFFFFFF)
                .setUv(sprite.getU1(), sprite.getV0())
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(combinedLight)
                .setNormal(0, 0, 1);
        buffer.addVertex(transform, x0, y0, 0)
                .setColor(0xFFFFFFFF)
                .setUv(sprite.getU0(), sprite.getV0())
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(combinedLight)
                .setNormal(0, 0, 1);

        poseStack.popPose();
    }

    @Override
    public Component getDisplayName(LightningKey stack) {
        return stack.getDisplayName();
    }
}
