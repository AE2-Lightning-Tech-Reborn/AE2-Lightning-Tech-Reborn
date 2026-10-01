package com.moakiee.ae2lt.client.ctm;

import com.moakiee.ae2lt.registry.ModBlocks;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Opt-in real model bake and item framebuffer checks; excluded from release jars. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class PigmeeSlabItemClientProbe {
    private static boolean started, done;
    private static int ticks;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("ae2lt.pigmeeSlabItemClientProbe") || done) return;
        var mc = Minecraft.getInstance();
        try {
            if (!started) {
                if (!(mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen)
                        || mc.getOverlay() != null) return;
                checkModels();
                mc.options.guiScale().set(2);
                mc.options.pauseOnLostFocus = false;
                mc.getWindow().setWindowed(1200, 820);
                mc.resizeDisplay();
                mc.setScreen(new Preview());
                started = true;
            } else if (++ticks == 40) {
                try (var pixels = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    pixels.writeToFile(mc.gameDirectory.toPath().resolve("slab-items.png"));
                }
                report("PASS: 33 slab items match vanilla display transforms in all contexts and both hands; "
                        + "99 block shapes and inset faces verified; native item framebuffer captured.");
                done = true;
                mc.stop();
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            report("FAIL: " + failure);
            done = true;
            mc.stop();
        }
    }

    private static List<Block> slabs() {
        var blocks = new ArrayList<Block>();
        blocks.add(ModBlocks.PIGMEE_BUILDING_SLAB.get());
        for (var color : DyeColor.values()) {
            blocks.add(ModBlocks.PIGMEE_BUILDING_SLABS.get(color).get());
            blocks.add(ModBlocks.PIGMEE_FRAMED_BUILDING_SLABS.get(color).get());
        }
        return blocks;
    }

    private static BakedModel itemModel(ItemStack stack) {
        return Minecraft.getInstance().getItemRenderer().getModel(stack, null, null, 0);
    }

    private static void checkModels() {
        var mc = Minecraft.getInstance();
        var reference = itemModel(new ItemStack(Items.STONE_SLAB));
        var random = RandomSource.create(0);
        for (var block : slabs()) {
            var item = itemModel(new ItemStack(block));
            require(item != mc.getModelManager().getMissingModel(), "Missing item " + block);
            for (var context : ItemDisplayContext.values()) for (boolean left : new boolean[] {false, true}) {
                var expected = new PoseStack();
                var actual = new PoseStack();
                reference.applyTransform(context, expected, left);
                item.applyTransform(context, actual, left);
                require(expected.last().pose().equals(actual.last().pose(), .00001f),
                        "Display transform " + block + " / " + context + " / left=" + left);
            }
            for (var type : SlabType.values()) {
                var state = block.defaultBlockState().setValue(SlabBlock.TYPE, type);
                var model = mc.getBlockRenderer().getBlockModel(state);
                var quads = new ArrayList<>(model.getQuads(state, null, random, ModelData.EMPTY, null));
                for (var face : Direction.values()) {
                    quads.addAll(model.getQuads(state, face, random, ModelData.EMPTY, null));
                }
                require(!quads.isEmpty(), "Empty slab " + state);
                float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
                for (var quad : quads) {
                    require(!quad.getSprite().contents().name().getPath().contains("missing"), "Missing texture " + state);
                    var vertices = quad.getVertices();
                    for (int v = 0; v < 4; v++) {
                        float y = Float.intBitsToFloat(vertices[v * (vertices.length / 4) + 1]);
                        min = Math.min(min, y);
                        max = Math.max(max, y);
                    }
                }
                require(Math.abs(min - (type == SlabType.TOP ? .5f : 0)) < .00001f
                                && Math.abs(max - (type == SlabType.BOTTOM ? .5f : 1)) < .00001f,
                        "Slab bounds " + state);
                if (type != SlabType.DOUBLE) {
                    var inset = type == SlabType.BOTTOM ? Direction.UP : Direction.DOWN;
                    require(model.getQuads(state, inset, random, ModelData.EMPTY, null).isEmpty(), "Culled inset " + state);
                    require(!model.getQuads(state, null, random, ModelData.EMPTY, null).isEmpty(), "Missing inset " + state);
                }
            }
        }
    }

    private static final class Preview extends Screen {
        Preview() { super(Component.literal("Pigmee slab item rendering")); }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xff252b35);
            graphics.drawCenteredString(font, "Pigmee slabs - native inventory render (all 33 variants)", width / 2, 12, 0xffffff);
            var blocks = slabs();
            for (int i = 0; i < blocks.size(); i++) {
                graphics.pose().pushPose();
                graphics.pose().translate(20 + (i % 11) * 51, 34 + (i / 11) * 45, 0);
                graphics.pose().scale(2, 2, 2);
                graphics.renderItem(new ItemStack(blocks.get(i)), 0, 0);
                graphics.pose().popPose();
            }
            var contexts = List.of(ItemDisplayContext.GUI, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                    ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    ItemDisplayContext.THIRD_PERSON_LEFT_HAND, ItemDisplayContext.GROUND);
            String[] labels = {"GUI", "First / R", "First / L", "Third / R", "Third / L", "Ground"};
            var brown = new ItemStack(ModBlocks.PIGMEE_FRAMED_BUILDING_SLABS.get(DyeColor.BROWN).get());
            graphics.drawCenteredString(font, "Brown framed slab / Stone slab reference", width / 2, 180, 0xffffff);
            for (int i = 0; i < contexts.size(); i++) {
                int x = 50 + i * 98;
                graphics.drawCenteredString(font, labels[i], x, 207, 0xffffff);
                renderContext(graphics, brown, contexts.get(i), x, 265);
                renderContext(graphics, new ItemStack(Items.STONE_SLAB), contexts.get(i), x, 350);
            }
        }

        private void renderContext(GuiGraphics graphics, ItemStack stack, ItemDisplayContext context, int x, int y) {
            graphics.flush();
            Lighting.setupFor3DItems();
            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(x, y, 150);
            pose.scale(64, -64, 64);
            boolean left = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                    || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            minecraft.getItemRenderer().render(stack, context, left, pose, graphics.bufferSource(),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, itemModel(stack));
            graphics.flush();
            pose.popPose();
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void report(String message) {
        System.out.println("PIGMEE_SLAB_ITEM_PROBE " + message);
        try {
            Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("model-result.txt"), message);
        } catch (Exception failure) { failure.printStackTrace(); }
    }
}
