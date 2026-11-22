package com.moakiee.ae2lt.client.vision;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import appeng.client.render.overlay.OverlayRenderType;
import com.moakiee.ae2lt.celestweave.module.NightVisionSubmodule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;

@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class BlockVision {
    private static final int SECTION_RADIUS = 2;
    private static final int WIDTH = 2 * SECTION_RADIUS + 1;
    private static final Map<SectionPos, ArrayList<Hit>> SECTIONS = new HashMap<>();
    private static ClientLevel world;
    private static SectionPos center;
    private static int cursor;
    private record Hit(BlockPos pos, int rgb) {}

    private BlockVision() {}

    static void reset() {
        SECTIONS.clear();
        center = null;
        world = null;
        cursor = 0;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !VisualEnhancement.canRender(NightVisionSubmodule.XRAY)
                || VisionTargets.colors().isEmpty()) {
            if (world != null) reset();
            return;
        }
        if (world != mc.level) {
            reset();
            world = mc.level;
        }
        var current = SectionPos.of(mc.player.blockPosition());
        if (!current.equals(center)) {
            center = current;
            cursor = 0;
            SECTIONS.keySet().removeIf(pos -> Math.abs(pos.x() - center.x()) > SECTION_RADIUS
                    || Math.abs(pos.y() - center.y()) > SECTION_RADIUS
                    || Math.abs(pos.z() - center.z()) > SECTION_RADIUS);
        }
        // Refresh a bounded number of loaded sections per tick, including after block updates.
        for (int i = 0; i < 4; i++) {
            var pos = center.offset(cursor % WIDTH - SECTION_RADIUS, cursor / WIDTH % WIDTH - SECTION_RADIUS,
                    cursor / (WIDTH * WIDTH) - SECTION_RADIUS);
            cursor = (cursor + 1) % (WIDTH * WIDTH * WIDTH);
            refresh(pos);
        }
    }

    private static void refresh(SectionPos pos) {
        int index = world.getSectionIndex(pos.minBlockY());
        var chunk = world.getChunkSource().getChunkNow(pos.x(), pos.z());
        if (chunk == null || index < 0 || index >= chunk.getSectionsCount()) {
            SECTIONS.remove(pos);
            return;
        }
        var section = chunk.getSection(index);
        Map<Block, Integer> colors = VisionTargets.colors();
        if (!section.maybeHas(state -> colors.containsKey(state.getBlock()))) {
            SECTIONS.remove(pos);
            return;
        }
        var hits = new ArrayList<Hit>();
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    var color = colors.get(section.getBlockState(x, y, z).getBlock());
                    if (color != null) hits.add(new Hit(new BlockPos(pos.minBlockX() + x, pos.minBlockY() + y, pos.minBlockZ() + z), color));
                }
            }
        }
        SECTIONS.put(pos, hits);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || SECTIONS.isEmpty()
                || world != Minecraft.getInstance().level || !VisualEnhancement.canRender(NightVisionSubmodule.XRAY)) return;
        var pose = event.getPoseStack();
        var camera = event.getCamera();
        var eye = camera.getPosition();
        pose.pushPose();
        pose.mulPose(new Quaternionf(camera.rotation()).invert());
        pose.translate(-eye.x, -eye.y, -eye.z);
        var source = Minecraft.getInstance().renderBuffers().bufferSource();
        for (var type : java.util.List.of(OverlayRenderType.getBlockHilightLineOccluded(), OverlayRenderType.getBlockHilightLine())) {
            var buffer = source.getBuffer(type);
            for (var hits : SECTIONS.values()) {
                for (var hit : hits) {
                    var box = new AABB(hit.pos());
                    if (!event.getFrustum().isVisible(box)) continue;
                    int color = hit.rgb();
                    LevelRenderer.renderLineBox(pose, buffer, box,
                            (color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, 1);
                }
            }
            source.endBatch(type);
        }
        pose.popPose();
    }
}
