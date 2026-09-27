package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.registry.ModBlocks;
import java.nio.file.Files;
import java.util.HashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Isolated native model bake and framebuffer acceptance, excluded from release jars. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class OverloadAlloyAnvilClientProbe {
    private static int ticks;
    private static boolean started, done;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.alloyAnvilClientProbe") || done) return;
        var mc = Minecraft.getInstance();
        try {
            if (!started) {
                if (!(mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen)
                        || mc.getOverlay() != null) return;
                var sprites = new HashSet<String>();
                for (var state : ModBlocks.OVERLOAD_ALLOY_ANVIL.get().getStateDefinition().getPossibleStates()) {
                    checkModel(mc.getBlockRenderer().getBlockModel(state), sprites);
                }
                checkModel(mc.getItemRenderer().getModel(new ItemStack(ModBlocks.OVERLOAD_ALLOY_ANVIL.get()), null, null, 0), sprites);
                if (!sprites.equals(java.util.Set.of("ae2lt:block/overload_machine_frame"))) {
                    throw new AssertionError("alloy top, body and particles must all use alloy texture: " + sprites);
                }
                Files.writeString(mc.gameDirectory.toPath().resolve("model-result.txt"),
                        "PASS: all 4 block facings and inventory model have real quads and textures. Sprites: " + sprites);
                mc.getWindow().setWindowed(960, 600);
                mc.resizeDisplay();
                mc.setScreen(new Preview());
                started = true;
            } else if (++ticks == 40) {
                var path = mc.gameDirectory.toPath().resolve("anvil-preview.png");
                try (var pixels = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    pixels.writeToFile(path);
                }
                done = true;
                mc.stop();
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(mc.gameDirectory.toPath().resolve("model-result.txt"), "FAIL: " + failure); }
            catch (Exception ignored) { }
            done = true;
            mc.stop();
        }
    }

    private static void checkModel(BakedModel model, java.util.Set<String> sprites) {
        var missing = MissingTextureAtlasSprite.getLocation();
        if (model == Minecraft.getInstance().getModelManager().getMissingModel()) throw new AssertionError("missing model");
        if (model.getParticleIcon().contents().name().equals(missing)) throw new AssertionError("missing particle texture");
        sprites.add(model.getParticleIcon().contents().name().toString());
        var quads = new java.util.ArrayList<>(model.getQuads(null, null, RandomSource.create(0)));
        for (var side : net.minecraft.core.Direction.values()) quads.addAll(model.getQuads(null, side, RandomSource.create(0)));
        if (quads.isEmpty()) throw new AssertionError("empty model");
        for (var quad : quads) {
            var sprite = quad.getSprite().contents().name();
            if (sprite.equals(missing)) throw new AssertionError("missing face texture");
            sprites.add(sprite.toString());
        }
    }

    private static final class Preview extends Screen {
        Preview() { super(Component.literal("Alloy anvil texture acceptance")); }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xff252b35);
            graphics.drawCenteredString(font, "Overload Alloy Anvil / Vanilla Anvil", width / 2, 16, 0xffffff);
            float scale = Math.min(width / 48f, (height - 40) / 20f);
            graphics.pose().pushPose();
            graphics.pose().translate(width / 2f - 20 * scale, (height - 16 * scale) / 2f + 12, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.renderItem(new ItemStack(ModBlocks.OVERLOAD_ALLOY_ANVIL.get()), 0, 0);
            graphics.renderItem(new ItemStack(Items.ANVIL), 24, 0);
            graphics.pose().popPose();
        }
    }
}
