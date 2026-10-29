package com.moakiee.ae2lt.client.render;

import appeng.client.render.overlay.OverlayRenderType;
import com.moakiee.ae2lt.logic.management.DeviceLocation;
import com.moakiee.ae2lt.client.gui.NetworkManagementScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/** EAE-style temporary world outline, using AE2's own overlay render type. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class NetworkDeviceHighlight {
    private static DeviceLocation target;
    private static long expires;
    private static final org.joml.Quaternionf CAMERA_ROTATION = new org.joml.Quaternionf();
    public static boolean locate(DeviceLocation location) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;
        boolean sameDimension = mc.level.dimension().location().toString().equals(location.dimension());
        String address = NetworkManagementScreen.address(location);
        var hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                Component.translatable("ae2lt.management.copy_help").append("\n").append(address));
        var copy = new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, address);
        var coordinates = Component.literal("[" + location.pos().toShortString() + "]").withStyle(style ->
                style.withColor(ChatFormatting.GREEN).withClickEvent(copy).withHoverEvent(hover));
        var dimension = Component.literal(location.dimension()).withStyle(style ->
                style.withColor(ChatFormatting.AQUA).withClickEvent(copy).withHoverEvent(hover));
        var distance = sameDimension
                ? Component.translatable("ae2lt.management.location_distance", (int) Math.sqrt(mc.player.blockPosition().distSqr(location.pos())))
                : Component.translatable("ae2lt.management.location_other_dimension");
        mc.player.displayClientMessage(Component.translatable("ae2lt.management.location_message", coordinates, dimension, distance), false);
        target = null;
        if (!sameDimension) return false;
        target = location;
        expires = net.minecraft.Util.getMillis() + 15_000;
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
        var occluded = buffers.getBuffer(OverlayRenderType.getBlockHilightLine());
        net.minecraft.client.renderer.LevelRenderer.renderLineBox(pose, occluded, box, 1f, .35f, .15f, pulse * .8f);
        buffers.endBatch(OverlayRenderType.getBlockHilightLine());
        var consumer = buffers.getBuffer(OverlayRenderType.getBlockHilightLine());
        net.minecraft.client.renderer.LevelRenderer.renderLineBox(pose, consumer, box, 1f, .35f, .15f, pulse);
        buffers.endBatch(OverlayRenderType.getBlockHilightLine());
        pose.popPose();
    }
}
