package com.moakiee.ae2lt.client;

import com.moakiee.ae2lt.registry.ModBlocks;
import java.nio.file.Files;
import java.util.HashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Native 26.1 model bake and framebuffer acceptance, excluded from release jars. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class OverloadAlloyAnvilClientProbe {
    private static int ticks;
    private static boolean started, done;

    public static void verifyModels() throws Exception {
        var mc = Minecraft.getInstance();
        var models = mc.getModelManager().getBlockStateModelSet();
        var sprites = new HashSet<String>();
        for (var state : ModBlocks.OVERLOAD_ALLOY_ANVIL.get().getStateDefinition().getPossibleStates()) {
            var model = models.get(state);
            if (model == models.missingModel()) throw new AssertionError("missing anvil block model");
            sprites.add(model.particleMaterial().sprite().contents().name().toString());
            var parts = new java.util.ArrayList<BlockStateModelPart>();
            model.collectParts(RandomSource.create(0), parts);
            int quads = 0;
            for (var part : parts) {
                quads += part.getQuads(null).size();
                for (var side : net.minecraft.core.Direction.values()) quads += part.getQuads(side).size();
                sprites.add(part.particleMaterial().sprite().contents().name().toString());
            }
            if (quads == 0) throw new AssertionError("empty anvil block geometry");
        }
        var item = new ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(item, new ItemStack(ModBlocks.OVERLOAD_ALLOY_ANVIL.get()),
                ItemDisplayContext.GUI, mc.level, mc.player, 0);
        if (item.isEmpty()) throw new AssertionError("empty inventory model");
        sprites.add(item.pickParticleMaterial(RandomSource.create(0)).sprite().contents().name().toString());
        if (!sprites.equals(java.util.Set.of("ae2lt:block/overload_machine_frame"))) {
            throw new AssertionError("missing or wrong alloy texture: " + sprites);
        }
        Files.writeString(mc.gameDirectory.toPath().resolve("model-result.txt"),
                "PASS: all 4 block facings and inventory model have real geometry and alloy textures. " + sprites);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.alloyAnvilClientProbe") || done) return;
        var mc = Minecraft.getInstance();
        try {
            if (!started) {
                if (!(mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen)
                        || mc.getOverlay() != null) return;
                verifyModels();
                mc.setScreen(new Preview());
                started = true;
            } else if (++ticks == 40) {
                done = true;
                Screenshot.takeScreenshot(mc.getMainRenderTarget(), pixels -> {
                    try (pixels) { pixels.writeToFile(mc.gameDirectory.toPath().resolve("anvil-preview.png")); }
                    catch (Exception failure) { failure.printStackTrace(); }
                    mc.execute(mc::stop);
                });
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(mc.gameDirectory.toPath().resolve("model-result.txt"), "FAIL: " + failure); }
            catch (Exception ignored) { }
            done = true;
            mc.stop();
        }
    }

    public static final class Preview extends Screen {
        public Preview() { super(Component.literal("Alloy anvil texture acceptance")); }
        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xff252b35);
            graphics.text(font, "Overload Alloy Anvil / Vanilla Anvil", 16, 16, 0xffffffff);
            float scale = Math.min(width / 48f, (height - 40) / 20f);
            graphics.pose().pushMatrix();
            graphics.pose().translate(width / 2f - 20 * scale, (height - 16 * scale) / 2f + 12);
            graphics.pose().scale(scale, scale);
            graphics.item(new ItemStack(ModBlocks.OVERLOAD_ALLOY_ANVIL.get()), 0, 0);
            graphics.item(new ItemStack(Items.ANVIL), 24, 0);
            graphics.pose().popMatrix();
        }
    }
}
