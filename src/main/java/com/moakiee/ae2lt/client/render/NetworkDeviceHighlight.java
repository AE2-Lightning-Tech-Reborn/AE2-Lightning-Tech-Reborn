package com.moakiee.ae2lt.client.render;

import appeng.client.render.overlay.OverlayRenderType;
import com.moakiee.ae2lt.logic.management.DeviceLocation;
import com.moakiee.ae2lt.client.gui.NetworkManagementScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** EAE-style temporary world outline, using AE2's own overlay render type. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class NetworkDeviceHighlight {
    private static DeviceLocation target;
    private static long expires;
    private static final org.joml.Quaternionf CAMERA_ROTATION = new org.joml.Quaternionf();
    public static boolean locate(DeviceLocation location) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;
        if (!mc.level.dimension().location().toString().equals(location.dimension())) {
            mc.player.displayClientMessage(Component.translatable("ae2lt.management.other_dimension", NetworkManagementScreen.address(location)), false);
            return false;
        }
        target = location;
        expires = net.minecraft.Util.getMillis() + 15_000;
        mc.player.displayClientMessage(Component.translatable("ae2lt.management.highlighted", NetworkManagementScreen.address(location)), true);
        return true;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { target = null; }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (target == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        var mc = Minecraft.getInstance();
        long now = net.minecraft.Util.getMillis();
        if (mc.level == null || expires < now || !mc.level.dimension().location().toString().equals(target.dimension())) {
            target = null; return;
        }
        float pulse = (now / 400 & 1) == 0 ? .4f : 1f;
        AABB box = new AABB(target.pos()).inflate(.005);
        if (target.face() >= 0) {
            double x = target.pos().getX(), y = target.pos().getY(), z = target.pos().getZ();
            box = switch (net.minecraft.core.Direction.values()[target.face()]) {
                case DOWN -> new AABB(x + .125, y - .005, z + .125, x + .875, y + .13, z + .875);
                case UP -> new AABB(x + .125, y + .87, z + .125, x + .875, y + 1.005, z + .875);
                case NORTH -> new AABB(x + .125, y + .125, z - .005, x + .875, y + .875, z + .13);
                case SOUTH -> new AABB(x + .125, y + .125, z + .87, x + .875, y + .875, z + 1.005);
                case WEST -> new AABB(x - .005, y + .125, z + .125, x + .13, y + .875, z + .875);
                case EAST -> new AABB(x + .87, y + .125, z + .125, x + 1.005, y + .875, z + .875);
            };
        }
        var pose = event.getPoseStack(); var camera = event.getCamera().getPosition();
        pose.pushPose();
        // AFTER_LEVEL has a camera-relative origin but no camera rotation in its pose stack.
        pose.mulPose(CAMERA_ROTATION.set(event.getCamera().rotation()).invert());
        pose.translate(-camera.x, -camera.y, -camera.z);
        var buffers = mc.renderBuffers().bufferSource();
        var occluded = buffers.getBuffer(OverlayRenderType.getBlockHilightLineOccluded());
        net.minecraft.client.renderer.LevelRenderer.renderLineBox(pose, occluded, box, 1f, .35f, .15f, pulse * .8f);
        buffers.endBatch(OverlayRenderType.getBlockHilightLineOccluded());
        var consumer = buffers.getBuffer(OverlayRenderType.getBlockHilightLine());
        net.minecraft.client.renderer.LevelRenderer.renderLineBox(pose, consumer, box, 1f, .35f, .15f, pulse);
        buffers.endBatch(OverlayRenderType.getBlockHilightLine());
        pose.popPose();
    }
}
