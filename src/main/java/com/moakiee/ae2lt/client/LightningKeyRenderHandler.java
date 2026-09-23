package com.moakiee.ae2lt.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.client.renderer.texture.OverlayTexture;

import appeng.client.api.AEKeyRenderer;
import appeng.client.gui.style.Blitter;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.me.key.LightningKey;

public final class LightningKeyRenderHandler implements AEKeyRenderer<LightningKey, LightningKeyRenderHandler.State> {
    public static final LightningKeyRenderHandler INSTANCE = new LightningKeyRenderHandler();

    private static final Identifier HIGH_VOLTAGE_SPRITE =
            Identifier.fromNamespaceAndPath(AE2LightningTech.MODID, "item/high_voltage_lightning");
    private static final Identifier EXTREME_HIGH_VOLTAGE_SPRITE =
            Identifier.fromNamespaceAndPath(AE2LightningTech.MODID, "item/extreme_high_voltage_lightning");

    public static final class State {
        private LightningKey key;
    }

    private LightningKeyRenderHandler() {
    }

    private static TextureAtlasSprite spriteFor(LightningKey key) {
        Identifier id = key.tier() == LightningKey.Tier.EXTREME_HIGH_VOLTAGE
                ? EXTREME_HIGH_VOLTAGE_SPRITE : HIGH_VOLTAGE_SPRITE;
        return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).getSprite(id);
    }

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphicsExtractor graphics, int x, int y, LightningKey key) {
        Blitter.sprite(spriteFor(key)).dest(x, y, 16, 16).blit(graphics);
    }

    @Override
    public Class<State> stateClass() {
        return State.class;
    }

    @Override
    public State createState() {
        return new State();
    }

    @Override
    public void extract(State state, LightningKey key, Level level, int packedLight) {
        state.key = key;
    }

    @Override
    public void submit(PoseStack poseStack, State state, SubmitNodeCollector collector, int packedLight) {
        if (state.key == null) {
            return;
        }
        TextureAtlasSprite sprite = spriteFor(state.key);
        collector.submitCustomGeometry(poseStack, RenderTypes.itemCutout(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS), (pose, buffer) -> {
            float half = 0.45F;
            var transform = pose.pose();
            buffer.addVertex(transform, -half, -half, 0).setColor(-1)
                    .setUv(sprite.getU0(), sprite.getV1()).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(packedLight).setNormal(0, 0, 1);
            buffer.addVertex(transform, half, -half, 0).setColor(-1)
                    .setUv(sprite.getU1(), sprite.getV1()).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(packedLight).setNormal(0, 0, 1);
            buffer.addVertex(transform, half, half, 0).setColor(-1)
                    .setUv(sprite.getU1(), sprite.getV0()).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(packedLight).setNormal(0, 0, 1);
            buffer.addVertex(transform, -half, half, 0).setColor(-1)
                    .setUv(sprite.getU0(), sprite.getV0()).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(packedLight).setNormal(0, 0, 1);
        });
    }

    @Override
    public List<Component> getTooltip(LightningKey key) {
        return List.of(key.getDisplayName());
    }
}
