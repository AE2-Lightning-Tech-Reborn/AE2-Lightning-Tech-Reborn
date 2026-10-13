package com.moakiee.ae2lt.client.workstation;

import com.moakiee.ae2lt.block.FumoBlock;
import com.moakiee.ae2lt.blockentity.FumoBlockEntity;
import com.moakiee.ae2lt.client.render.FumoBlockRenderer;
import com.moakiee.ae2lt.workstation.PigmeeIdentityPacket;
import com.moakiee.ae2lt.workstation.PigmeeVariant;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class PigmeeClientIdentity {
    private static final Map<UUID, PigmeeVariant> IDENTITIES = new HashMap<>();
    private static final FumoBlockRenderer RENDERER = new FumoBlockRenderer(null);
    private static final Map<PigmeeVariant, FumoBlockEntity> MODELS = new HashMap<>();
    private static final java.util.Set<UUID> RENDERED = new java.util.HashSet<>();
    private PigmeeClientIdentity() { }
    public static void accept(PigmeeIdentityPacket packet) {
        if (packet.variant() < 0) { IDENTITIES.remove(packet.id()); RENDERED.remove(packet.id()); return; }
        if (packet.variant() >= PigmeeVariant.values().length || IDENTITIES.size() >= 8 && !IDENTITIES.containsKey(packet.id())) return;
        IDENTITIES.put(packet.id(), PigmeeVariant.values()[packet.variant()]);
    }
    public static PigmeeVariant variant(UUID id) { return IDENTITIES.get(id); }
    public static boolean rendered(UUID id) { return RENDERED.contains(id); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        IDENTITIES.clear(); MODELS.clear(); RENDERED.clear();
    }
    @SubscribeEvent public static void hideBodyHud(net.neoforged.neoforge.client.event.RenderGuiEvent.Pre event) {
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof PigmeeControlScreen) event.setCanceled(true);
    }
    @SubscribeEvent public static void render(RenderPlayerEvent.Pre event) {
        PigmeeVariant variant = IDENTITIES.get(event.getEntity().getUUID());
        if (variant == null) return;
        event.setCanceled(true);
        var player = event.getEntity();
        var model = MODELS.computeIfAbsent(variant, kind -> new FumoBlockEntity(BlockPos.ZERO,
                BuiltInRegistries.BLOCK.get(kind.blockId()).defaultBlockState().setValue(FumoBlock.FACING, Direction.NORTH)));
        var pose = event.getPoseStack();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(event.getPartialTick(), player.yBodyRotO, player.yBodyRot)));
        // P1 uses the native player collision/eye contract. Fit the existing Fumo to that body,
        // including crouching/swimming height, rather than hiding a 1.8m body inside a tiny model.
        var bounds = model.getBlockState().getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).bounds();
        pose.scale((float) (player.getBbWidth() / bounds.getXsize()),
                (float) (player.getBbHeight() / bounds.getYsize()), (float) (player.getBbWidth() / bounds.getZsize()));
        pose.translate(-bounds.getCenter().x, -bounds.minY, -bounds.getCenter().z);
        RENDERER.render(model, event.getPartialTick(), pose, event.getMultiBufferSource(), event.getPackedLight(), OverlayTexture.NO_OVERLAY);
        pose.popPose();
        RENDERED.add(player.getUUID());
    }
}
