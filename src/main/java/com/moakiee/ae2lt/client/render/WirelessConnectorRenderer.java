package com.moakiee.ae2lt.client.render;

import com.lowdragmc.lowdraglib2.client.model.ModelFactory;
import com.lowdragmc.lowdraglib2.client.utils.RenderBufferUtils;
import com.lowdragmc.lowdraglib2.utils.ColorUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import appeng.client.render.overlay.OverlayRenderType;

import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPowerSupplyBlockEntity;
import com.moakiee.ae2lt.item.OverloadedWirelessConnectorItem;
import com.moakiee.ae2lt.logic.WirelessConnectorTargetHelper;
import com.moakiee.ae2lt.api.patternprovider.WirelessPatternProviderHost;

/**
 * Client-side renderer for the Overloaded Wireless Connector.
 * <p>
 * When the player holds the connector, wireless-capable hosts within range are
 * highlighted with their connections. If a host is selected, only that host is rendered.
 * A preview overlay is shown for the selected host.
 * <p>
 * Performance notes (matches the optimization-report guidance):
 * <ul>
 *   <li>The chunk/BE scan is performed at most once every {@link #RESCAN_INTERVAL_TICKS}
 *       game ticks, not every frame. At 200fps this turns ~200 scans/sec into ~5 scans/sec
 *       while remaining visually instantaneous (≤200ms staleness).</li>
 *   <li>Hot-path objects ({@link Quaternionf}, scratch {@link HashSet}) are reused across
 *       frames instead of re-allocated, to keep GC pressure flat under high frame rates
 *       (the report flags high-FPS as an amplifier of any per-frame leak path).</li>
 * </ul>
 */
@EventBusSubscriber(modid = AE2LightningTech.MODID, value = Dist.CLIENT)
public class WirelessConnectorRenderer {

    // Preview color: semi-transparent yellow (ARGB)
    private static final int COLOR_PREVIEW = 0x60FFFF00;
    // Connected face color: semi-transparent blue (ARGB)
    private static final int COLOR_CONNECTED = 0x600080FF;
    // Preview line color: bright yellow
    private static final int COLOR_PREVIEW_LINE = 0xC0FFFF00;
    // Host inner cube color (unselected): semi-transparent blue (ARGB)
    private static final int COLOR_HOST = 0x800080FF;
    // Host inner cube color (selected): semi-transparent yellow (ARGB)
    private static final int COLOR_HOST_SELECTED = 0x80FFFF00;
    // Line color: blue (ARGB)
    private static final int COLOR_LINE = 0xC00080FF;
    private static final float FACE_OFFSET = 0.001F;
    private static final Vector3f[][] FACE_CORNERS = buildFaceCorners();

    private static final int SCAN_RANGE = 64;
    /** How many game ticks between full chunk re-scans for wireless hosts. */
    private static final int RESCAN_INTERVAL_TICKS = 4;

    /** Cached host positions from the last scan; refreshed every {@link #RESCAN_INTERVAL_TICKS}. */
    private static final List<BlockPos> cachedHostPositions = new ArrayList<>();
    /** Game time of the last scan. -1 means never scanned. */
    private static long lastScanTick = -1L;
    /** Dimension key of the last scan; if it changes we force a re-scan immediately. */
    private static ResourceKey<Level> lastScanDimension = null;

    /** Reusable rotation; avoids {@code new Quaternionf(...)} every frame. */
    private static final Quaternionf scratchRotation = new Quaternionf();
    /** Reusable scratch set used by {@link #collectConnectionsForFace}; cleared between calls. */
    private static final Set<BlockPos> scratchConnectionSet = new HashSet<>();

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }

        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        // Check if player is holding the wireless connector in either hand
        ItemStack stack = ItemStack.EMPTY;
        for (var hand : net.minecraft.world.InteractionHand.values()) {
            var held = player.getItemInHand(hand);
            if (held.getItem() instanceof OverloadedWirelessConnectorItem) {
                stack = held;
                break;
            }
        }
        if (stack.isEmpty()) {
            return;
        }

        // -- Set up camera offset --
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        poseStack.pushPose();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        // Reuse a single Quaternionf instance instead of allocating per frame.
        // mc.gameRenderer.getMainCamera().rotation() returns a reference Quaternionf
        // owned by the camera; copying-and-inverting through scratchRotation keeps
        // the camera's value untouched while avoiding a per-frame allocation.
        scratchRotation.set(mc.gameRenderer.getMainCamera().rotation());
        scratchRotation.invert();
        poseStack.mulPose(scratchRotation);
        poseStack.translate(-cam.x, -cam.y, -cam.z);

        // Resolve selected host for special coloring and preview rendering.
        var selectedHost = getSelectedHost(stack);
        var selectedPos = selectedHost != null ? selectedHost.pos() : null;
        var selectedDim = selectedHost != null ? selectedHost.dimension() : null;
        var selectedHostType = selectedHost != null ? selectedHost.hostType() : null;
        boolean hasSelection = selectedPos != null
                && selectedDim != null
                && selectedHostType != null;
        boolean selectionInCurrentDimension = hasSelection && mc.level.dimension().equals(selectedDim);
        long selectedPosLong = selectedPos != null ? selectedPos.asLong() : 0L;
        boolean selectedRendered = false;

        // Refresh the cached host position list at most every RESCAN_INTERVAL_TICKS,
        // or immediately if the player has changed dimensions since the last scan.
        long gameTime = mc.level.getGameTime();
        ResourceKey<Level> currentDim = mc.level.dimension();
        if (lastScanTick < 0L
                || lastScanDimension == null
                || !lastScanDimension.equals(currentDim)
                || gameTime - lastScanTick >= RESCAN_INTERVAL_TICKS) {
            rescanHosts(mc.level, player.blockPosition());
            lastScanTick = gameTime;
            lastScanDimension = currentDim;
        }

        // Render eligible cached hosts. We re-fetch the BlockEntity from the level
        // so connection state stays live; only which positions to consider is cached.
        for (BlockPos bePos : cachedHostPositions) {
            if (!mc.level.isLoaded(bePos)) continue;
            var be = mc.level.getBlockEntity(bePos);
            if (be instanceof WirelessPatternProviderHost provider) {
                if (!provider.isWirelessProvider()) {
                    continue;
                }
                boolean isSelected = hasSelection
                        && selectionInCurrentDimension
                        && OverloadedWirelessConnectorItem.HOST_PROVIDER.equals(selectedHostType)
                        && bePos.equals(selectedPos);
                if (!WirelessConnectorRenderFilter.shouldRenderHost(
                        hasSelection,
                        selectionInCurrentDimension,
                        selectedPosLong,
                        selectedHostType,
                        OverloadedWirelessConnectorItem.HOST_PROVIDER,
                        bePos.asLong())) {
                    continue;
                }
                renderProviderHost(poseStack, buffer, mc.level, bePos, provider, isSelected);
                selectedRendered |= isSelected;
            } else if (be instanceof OverloadedInterfaceBlockEntity iface) {
                if (iface.getInterfaceMode() != OverloadedInterfaceBlockEntity.InterfaceMode.WIRELESS) {
                    continue;
                }
                boolean isSelected = hasSelection
                        && selectionInCurrentDimension
                        && OverloadedWirelessConnectorItem.HOST_INTERFACE.equals(selectedHostType)
                        && bePos.equals(selectedPos);
                if (!WirelessConnectorRenderFilter.shouldRenderHost(
                        hasSelection,
                        selectionInCurrentDimension,
                        selectedPosLong,
                        selectedHostType,
                        OverloadedWirelessConnectorItem.HOST_INTERFACE,
                        bePos.asLong())) {
                    continue;
                }
                renderInterfaceHost(poseStack, buffer, mc.level, bePos, iface, isSelected);
                selectedRendered |= isSelected;
            } else if (be instanceof OverloadedPowerSupplyBlockEntity powerSupply) {
                boolean isSelected = hasSelection
                        && selectionInCurrentDimension
                        && OverloadedWirelessConnectorItem.HOST_POWER_SUPPLY.equals(selectedHostType)
                        && bePos.equals(selectedPos);
                if (!WirelessConnectorRenderFilter.shouldRenderHost(
                        hasSelection,
                        selectionInCurrentDimension,
                        selectedPosLong,
                        selectedHostType,
                        OverloadedWirelessConnectorItem.HOST_POWER_SUPPLY,
                        bePos.asLong())) {
                    continue;
                }
                renderPowerSupplyHost(poseStack, buffer, mc.level, bePos, powerSupply, isSelected);
                selectedRendered |= isSelected;
            }
        }

        if (selectionInCurrentDimension && !selectedRendered && mc.level.isLoaded(selectedPos)) {
            var selectedBe = mc.level.getBlockEntity(selectedPos);
            if (OverloadedWirelessConnectorItem.HOST_PROVIDER.equals(selectedHostType)
                    && selectedBe instanceof WirelessPatternProviderHost provider
                    && provider.isWirelessProvider()) {
                renderProviderHost(poseStack, buffer, mc.level, selectedPos, provider, true);
            } else if (OverloadedWirelessConnectorItem.HOST_INTERFACE.equals(selectedHostType)
                    && selectedBe instanceof OverloadedInterfaceBlockEntity iface
                    && iface.getInterfaceMode() == OverloadedInterfaceBlockEntity.InterfaceMode.WIRELESS) {
                renderInterfaceHost(poseStack, buffer, mc.level, selectedPos, iface, true);
            } else if (OverloadedWirelessConnectorItem.HOST_POWER_SUPPLY.equals(selectedHostType)
                    && selectedBe instanceof OverloadedPowerSupplyBlockEntity powerSupply) {
                renderPowerSupplyHost(poseStack, buffer, mc.level, selectedPos, powerSupply, true);
            }
        }

        // Preview face overlay (only for the selected wireless host).
        if (selectionInCurrentDimension) {
            var selectedBe = mc.level.getBlockEntity(selectedPos);
            if (OverloadedWirelessConnectorItem.HOST_PROVIDER.equals(selectedHostType)
                    && selectedBe instanceof WirelessPatternProviderHost selectedProvider
                    && selectedProvider.isWirelessProvider()
                    && mc.hitResult instanceof BlockHitResult bhr
                    && bhr.getType() == HitResult.Type.BLOCK
                    && !bhr.getBlockPos().equals(selectedPos)
                    && mc.level.getBlockEntity(bhr.getBlockPos()) != null) {

                var previewTargets = WirelessConnectorTargetHelper.collectTargets(
                        mc.level,
                        bhr.getBlockPos(),
                        net.minecraft.client.gui.screens.Screen.hasControlDown());
                Direction lookFace = bhr.getDirection();
                var existingConnections = collectConnectionsForFace(
                        selectedProvider.getConnections(),
                        mc.level,
                        lookFace,
                        c -> c.dimension(),
                        c -> c.pos(),
                        c -> c.boundFace());
                for (var lookPos : previewTargets) {
                    if (!existingConnections.contains(lookPos)) {
                        renderFaceOverlay(poseStack, buffer, lookPos, lookFace, COLOR_PREVIEW);
                        renderLine(poseStack, buffer, selectedPos, lookPos, lookFace, COLOR_PREVIEW_LINE);
                    }
                }
            } else if (OverloadedWirelessConnectorItem.HOST_INTERFACE.equals(selectedHostType)
                    && selectedBe instanceof OverloadedInterfaceBlockEntity selectedInterface
                    && mc.hitResult instanceof BlockHitResult bhr
                    && bhr.getType() == HitResult.Type.BLOCK
                    && !bhr.getBlockPos().equals(selectedPos)
                    && mc.level.getBlockEntity(bhr.getBlockPos()) != null) {

                var previewTargets = WirelessConnectorTargetHelper.collectTargets(
                        mc.level,
                        bhr.getBlockPos(),
                        net.minecraft.client.gui.screens.Screen.hasControlDown());
                Direction lookFace = bhr.getDirection();
                var existingConnections = collectConnectionsForFace(
                        selectedInterface.getConnections(),
                        mc.level,
                        lookFace,
                        c -> c.dimension(),
                        c -> c.pos(),
                        c -> c.boundFace());
                for (var lookPos : previewTargets) {
                    if (!existingConnections.contains(lookPos)) {
                        renderFaceOverlay(poseStack, buffer, lookPos, lookFace, COLOR_PREVIEW);
                        renderLine(poseStack, buffer, selectedPos, lookPos, lookFace, COLOR_PREVIEW_LINE);
                    }
                }
            } else if (OverloadedWirelessConnectorItem.HOST_POWER_SUPPLY.equals(selectedHostType)
                    && selectedBe instanceof OverloadedPowerSupplyBlockEntity selectedPowerSupply
                    && mc.hitResult instanceof BlockHitResult bhr
                    && bhr.getType() == HitResult.Type.BLOCK
                    && !bhr.getBlockPos().equals(selectedPos)
                    && mc.level.getBlockEntity(bhr.getBlockPos()) != null) {

                var previewTargets = WirelessConnectorTargetHelper.collectTargets(
                        mc.level,
                        bhr.getBlockPos(),
                        net.minecraft.client.gui.screens.Screen.hasControlDown());
                Direction lookFace = bhr.getDirection();
                var existingConnections = collectConnectionsForFace(
                        selectedPowerSupply.getConnections(),
                        mc.level,
                        lookFace,
                        c -> c.dimension(),
                        c -> c.pos(),
                        c -> c.boundFace());
                for (var lookPos : previewTargets) {
                    if (!existingConnections.contains(lookPos)) {
                        renderFaceOverlay(poseStack, buffer, lookPos, lookFace, COLOR_PREVIEW);
                        renderLine(poseStack, buffer, selectedPos, lookPos, lookFace, COLOR_PREVIEW_LINE);
                    }
                }
            }
        }

        poseStack.popPose();

        // Flush render batches
        buffer.endBatch(Ae2ltRenderTypes.getFaceSeeThrough());
        buffer.endBatch(OverlayRenderType.getBlockHilightFace());
        buffer.endBatch(OverlayRenderType.getBlockHilightLine());
    }

    // -- Render helpers --

    /**
     * Render a small cube inside the block using a see-through render type
     * (GREATER depth test) so it is visible through the opaque block.
     */
    private static void renderInnerCube(PoseStack poseStack, MultiBufferSource buffer,
            BlockPos pos, int color) {
        var consumer = buffer.getBuffer(Ae2ltRenderTypes.getFaceSeeThrough());
        poseStack.pushPose();
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
        RenderBufferUtils.drawCubeFace(poseStack, consumer,
                0.25f, 0.25f, 0.25f, 0.75f, 0.75f, 0.75f,
                ColorUtils.red(color), ColorUtils.green(color), ColorUtils.blue(color), ColorUtils.alpha(color), false);
        poseStack.popPose();
    }

    private static void renderProviderHost(PoseStack poseStack, MultiBufferSource buffer,
            Level level, BlockPos hostPos, WirelessPatternProviderHost provider, boolean selected) {
        renderInnerCube(poseStack, buffer, hostPos, selected ? COLOR_HOST_SELECTED : COLOR_HOST);

        for (var conn : provider.getConnections()) {
            if (!conn.dimension().equals(level.dimension())) continue;
            renderFaceOverlay(poseStack, buffer, conn.pos(), conn.boundFace(), COLOR_CONNECTED);
            renderLine(poseStack, buffer, hostPos, conn.pos(), conn.boundFace(), COLOR_LINE);
        }
    }

    private static void renderInterfaceHost(PoseStack poseStack, MultiBufferSource buffer,
            Level level, BlockPos hostPos, OverloadedInterfaceBlockEntity iface, boolean selected) {
        renderInnerCube(poseStack, buffer, hostPos, selected ? COLOR_HOST_SELECTED : COLOR_HOST);

        for (var conn : iface.getConnections()) {
            if (!conn.dimension().equals(level.dimension())) continue;
            renderFaceOverlay(poseStack, buffer, conn.pos(), conn.boundFace(), COLOR_CONNECTED);
            renderLine(poseStack, buffer, hostPos, conn.pos(), conn.boundFace(), COLOR_LINE);
        }
    }

    private static void renderPowerSupplyHost(PoseStack poseStack, MultiBufferSource buffer,
            Level level, BlockPos hostPos, OverloadedPowerSupplyBlockEntity powerSupply, boolean selected) {
        renderInnerCube(poseStack, buffer, hostPos, selected ? COLOR_HOST_SELECTED : COLOR_HOST);

        for (var conn : powerSupply.getConnections()) {
            if (!conn.dimension().equals(level.dimension())) continue;
            renderFaceOverlay(poseStack, buffer, conn.pos(), conn.boundFace(), COLOR_CONNECTED);
            renderLine(poseStack, buffer, hostPos, conn.pos(), conn.boundFace(), COLOR_LINE);
        }
    }

    /**
     * Render a single face overlay (quad) on the given block face.
     */
    private static void renderFaceOverlay(PoseStack poseStack, MultiBufferSource buffer,
            BlockPos pos, Direction face, int color) {
        VertexConsumer consumer = buffer.getBuffer(OverlayRenderType.getBlockHilightFace());
        poseStack.pushPose();
        try {
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
            var matrix = poseStack.last().pose();
            for (var corner : FACE_CORNERS[face.ordinal()]) {
                consumer.addVertex(matrix, corner.x, corner.y, corner.z)
                        .setColor(color).setNormal(face.getStepX(), face.getStepY(), face.getStepZ());
            }
        } finally {
            poseStack.popPose();
        }
    }

    private static Vector3f[][] buildFaceCorners() {
        var directions = Direction.values();
        var corners = new Vector3f[directions.length][];
        for (var face : directions) {
            var rotation = ModelFactory.getQuaternion(face);
            var quad = new Vector3f[] {
                    new Vector3f(-0.5F, -0.5F, -0.5F), new Vector3f(-0.5F, 0.5F, -0.5F),
                    new Vector3f(0.5F, 0.5F, -0.5F), new Vector3f(0.5F, -0.5F, -0.5F)
            };
            for (var corner : quad) {
                rotation.transform(corner).add(0.5F, 0.5F, 0.5F);
                // Snap the quarter-turn result to block corners, then offset it to avoid z-fighting.
                corner.set(Math.round(corner.x) + face.getStepX() * FACE_OFFSET,
                        Math.round(corner.y) + face.getStepY() * FACE_OFFSET,
                        Math.round(corner.z) + face.getStepZ() * FACE_OFFSET);
            }
            corners[face.ordinal()] = quad;
        }
        return corners;
    }

    /**
     * Render a line from provider center to the center of the connected face.
     */
    private static void renderLine(PoseStack poseStack, MultiBufferSource buffer,
            BlockPos from, BlockPos to, Direction face, int color) {
        // Provider center
        float fx = from.getX() + 0.5f;
        float fy = from.getY() + 0.5f;
        float fz = from.getZ() + 0.5f;

        // Target face center
        float tx = to.getX() + 0.5f + face.getStepX() * 0.501f;
        float ty = to.getY() + 0.5f + face.getStepY() * 0.501f;
        float tz = to.getZ() + 0.5f + face.getStepZ() * 0.501f;

        float dx = tx - fx, dy = ty - fy, dz = tz - fz;
        if (dx * dx + dy * dy + dz * dz < 1e-12f) {
            return;
        }
        float red = ColorUtils.red(color);
        float green = ColorUtils.green(color);
        float blue = ColorUtils.blue(color);
        float alpha = ColorUtils.alpha(color);
        var consumer = buffer.getBuffer(OverlayRenderType.getBlockHilightLine());
        RenderBufferUtils.drawLine(poseStack.last(), consumer, fx, fy, fz, tx, ty, tz,
                red, green, blue, alpha, red, green, blue, alpha);
    }

    // -- Item NBT helpers (client-side read-only) --

    private static final String TAG_SELECTED = "SelectedProvider";
    private static final String TAG_DIM = "Dim";
    private static final String TAG_POS = "Pos";
    private static final String TAG_HOST_TYPE = "HostType";

    private static <T> Set<BlockPos> collectConnectionsForFace(Iterable<T> connections,
            Level level, Direction face,
            Function<T, ResourceKey<Level>> dimensionGetter,
            Function<T, BlockPos> posGetter,
            Function<T, Direction> faceGetter) {
        // Reuse a single scratch set to keep allocations off the hot render path.
        // Safe because RenderLevelStageEvent is only fired on the client render thread.
        scratchConnectionSet.clear();
        for (var conn : connections) {
            if (dimensionGetter.apply(conn).equals(level.dimension()) && faceGetter.apply(conn) == face) {
                scratchConnectionSet.add(posGetter.apply(conn));
            }
        }
        return scratchConnectionSet;
    }

    /**
     * Refreshes {@link #cachedHostPositions} by walking the chunks in {@link #SCAN_RANGE}
     * around {@code playerPos}. Called at most every {@link #RESCAN_INTERVAL_TICKS} ticks
     * (or immediately on dimension change) to keep the per-frame render path allocation-free.
     */
    private static void rescanHosts(net.minecraft.client.multiplayer.ClientLevel level, BlockPos playerPos) {
        cachedHostPositions.clear();

        int minCX = (playerPos.getX() - SCAN_RANGE) >> 4;
        int maxCX = (playerPos.getX() + SCAN_RANGE) >> 4;
        int minCZ = (playerPos.getZ() - SCAN_RANGE) >> 4;
        int maxCZ = (playerPos.getZ() + SCAN_RANGE) >> 4;

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                var chunk = level.getChunk(cx, cz);
                for (var bePos : chunk.getBlockEntitiesPos()) {
                    var be = chunk.getBlockEntity(bePos);
                    if (be instanceof WirelessPatternProviderHost
                            || be instanceof OverloadedInterfaceBlockEntity
                            || be instanceof OverloadedPowerSupplyBlockEntity) {
                        // Defensive copy: chunk.getBlockEntitiesPos() returns positions
                        // that may share identity with the BE's stored pos; we only need
                        // the immutable coordinates.
                        cachedHostPositions.add(bePos.immutable());
                    }
                }
            }
        }
    }

    private static SelectedHost getSelectedHost(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(TAG_SELECTED, CompoundTag.TAG_COMPOUND)) {
            return null;
        }
        var sel = tag.getCompound(TAG_SELECTED);
        var dimStr = sel.getString(TAG_DIM);
        if (dimStr.isEmpty()) {
            return null;
        }
        return new SelectedHost(
                BlockPos.of(sel.getLong(TAG_POS)),
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimStr)),
                sel.contains(TAG_HOST_TYPE, CompoundTag.TAG_STRING)
                        ? sel.getString(TAG_HOST_TYPE)
                        : OverloadedWirelessConnectorItem.HOST_PROVIDER);
    }

    private record SelectedHost(BlockPos pos, ResourceKey<Level> dimension, String hostType) {
    }

    /**
     * Drop cached host positions when the player disconnects. Without this, a
     * subsequent login to a different world would briefly render highlights at
     * the previous world's coordinates on the very first frame (until the next
     * scheduled re-scan replaces them). Cheap and prevents stale-state bleed.
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        cachedHostPositions.clear();
        scratchConnectionSet.clear();
        lastScanTick = -1L;
        lastScanDimension = null;
    }
}
