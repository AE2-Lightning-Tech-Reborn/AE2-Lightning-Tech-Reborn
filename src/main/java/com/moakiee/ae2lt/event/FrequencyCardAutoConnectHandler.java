package com.moakiee.ae2lt.event;

import com.moakiee.ae2lt.logic.world.PlayerWorldActions;

import appeng.api.implementations.parts.ICablePart;
import appeng.api.parts.IPartItem;
import appeng.parts.PartPlacement;
import appeng.util.InteractionUtil;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.grid.wirelesslink.WirelessLinkRegistry;
import com.moakiee.ae2lt.item.OverloadedFrequencyCardItem;
import com.moakiee.ae2lt.item.TerminalCardAccess;
import com.moakiee.ae2lt.network.FrequencyCardUsePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class FrequencyCardAutoConnectHandler {
    private FrequencyCardAutoConnectHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled()) return;
        if (!(event.getLevel() instanceof ServerLevelAccessor levelAccessor)) {
            return;
        }
        var registry = WirelessLinkRegistry.get();
        if (registry == null) {
            return;
        }

        var level = levelAccessor.getLevel();
        ServerPlayer player = event.getEntity() instanceof ServerPlayer p && !(p instanceof net.minecraftforge.common.util.FakePlayer) ? p : null;
        var meCard = player == null ? ItemStack.EMPTY
                : OverloadedFrequencyCardItem.findAutoConnectCard(player).orElse(ItemStack.EMPTY);
        var feCard = player == null ? ItemStack.EMPTY
                : OverloadedFrequencyCardItem.findAutoConnectEnergyCard(player).orElse(ItemStack.EMPTY);
        int meFrequency = meCard.isEmpty() ? -1 : OverloadedFrequencyCardItem.getData(meCard).frequencyId();
        int feFrequency = feCard.isEmpty() ? -1 : OverloadedFrequencyCardItem.getData(feCard).frequencyId();
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            // The inherited event position is only the first snapshot. Wands may report the entire placement here.
            var seen = new java.util.HashSet<BlockPos>();
            for (var snapshot : multi.getReplacedBlockSnapshots()) {
                if (seen.size() >= 8192) break;
                if (snapshot.getLevel() == level && seen.add(snapshot.getPos())) {
                    queuePlacedBlock(registry, player, level, snapshot.getPos(), meFrequency, feFrequency);
                }
            }
        } else {
            queuePlacedBlock(registry, player, level, event.getPos(), meFrequency, feFrequency);
        }
    }

    /** Compatibility entry for tools whose placement event precedes the actual world edit. */
    public static void onSuccessfulPlacement(ServerPlayer player, ServerLevel level, BlockPos pos) {
        var registry = WirelessLinkRegistry.get();
        if (registry == null || (player instanceof net.minecraftforge.common.util.FakePlayer)) return;
        var meCard = OverloadedFrequencyCardItem.findAutoConnectCard(player).orElse(ItemStack.EMPTY);
        var feCard = OverloadedFrequencyCardItem.findAutoConnectEnergyCard(player).orElse(ItemStack.EMPTY);
        queuePlacedBlock(registry, player, level, pos,
                meCard.isEmpty() ? -1 : OverloadedFrequencyCardItem.getData(meCard).frequencyId(),
                feCard.isEmpty() ? -1 : OverloadedFrequencyCardItem.getData(feCard).frequencyId());
    }

    private static void queuePlacedBlock(WirelessLinkRegistry registry, ServerPlayer player, ServerLevel level,
                                        BlockPos pos, int meFrequency, int feFrequency) {
        if (!level.isLoaded(pos)) return;
        if (registry.isPotentialLinkTarget(level, pos)) {
            registry.queueClusterTopologyChange(level, pos);
            if (player != null && meFrequency > 0) registry.queuePlacedBlockAutoConnect(player, level, pos, meFrequency);
        }
        if (player != null && feFrequency > 0) {
            com.moakiee.ae2lt.logic.energy.WirelessEnergyAdmissions.placed(player, level, pos, feFrequency);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevelAccessor levelAccessor)) {
            return;
        }
        var registry = WirelessLinkRegistry.get();
        if (registry != null) {
            registry.onBlockChanged(levelAccessor.getLevel(), event.getPos());
        }
    }

    /**
     * AE2 cable-bus parts can be removed without breaking their host block, so a
     * BlockEvent is not guaranteed. Snapshot the linked physical cluster before
     * the click; the delayed reconciliation is harmless if the click does not
     * actually change topology.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        var registry = WirelessLinkRegistry.get();
        if (registry != null && registry.isPotentialLinkTarget(level, event.getPos())) {
            registry.prepareClusterTopologyChange(level, event.getPos());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onWrenchDisassemble(PlayerInteractEvent.RightClickBlock event) {
        if (PlayerWorldActions.isCheckingInteraction()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || !InteractionUtil.isInAlternateUseMode(player)
                || !InteractionUtil.canWrenchDisassemble(event.getItemStack())) {
            return;
        }
        var registry = WirelessLinkRegistry.get();
        if (registry != null && registry.isPotentialLinkTarget(level, event.getPos())) {
            registry.prepareClusterTopologyChange(level, event.getPos());
        }
    }

    /**
     * Lets a frequency card installed inside a wireless terminal link devices by
     * right-clicking them while holding the terminal — mirroring the held-card
     * behaviour. When the held item is a terminal carrying a bound card and the
     * clicked block is an AE2 network target, the interaction is intercepted
     * (so neither the device's nor the terminal's GUI opens) and the link/unlink
     * is performed server-side. Runs before {@link #onRightClickBlock}; a
     * terminal is never an {@link IPartItem}, so the two handlers never overlap.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTerminalCardRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (PlayerWorldActions.isCheckingInteraction()) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isShiftKeyDown()) {
            return;
        }
        ItemStack held = event.getItemStack();
        // The card-in-hand path is handled by the item itself.
        if (held.getItem() instanceof OverloadedFrequencyCardItem) {
            return;
        }
        ItemStack card = TerminalCardAccess.findCard(held);
        if (card.isEmpty() || !OverloadedFrequencyCardItem.getData(card).isBound()) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        var pos = event.getPos();
        var registry = WirelessLinkRegistry.get(level.getServer());
        if (registry == null || (!registry.isPotentialLinkTarget(level, pos)
                && (event.getFace() == null || !com.moakiee.ae2lt.logic.energy.WirelessEnergyEndpoints.get(level.getServer())
                        .isPotentialTarget(level, pos, event.getFace())))) {
            return;
        }

        // Take over the interaction: prevent the clicked AE2 device (or the
        // terminal's own use) from opening a GUI, then run the link/unlink.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        var face = event.getFace();
        if (face == null) {
            return;
        }
        FrequencyCardUsePacket.tryLinkWithCard(
                player, event.getHand(), card, level, pos, face, event.getHitVec().getLocation());
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (PlayerWorldActions.isCheckingInteraction()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getItemStack().getItem() instanceof IPartItem<?> partItem)) {
            return;
        }
        var clickedFace = event.getFace();
        if (clickedFace == null) {
            return;
        }
        var placement = PartPlacement.getPartPlacement(
                player,
                player.level(),
                event.getItemStack(),
                event.getPos(),
                clickedFace,
                event.getHitVec().getLocation());
        if (placement == null) {
            return;
        }
        var target = toAutoConnectTarget(event.getPos(), clickedFace, placement);
        var registry = WirelessLinkRegistry.get();
        if (registry != null) {
            if (player.level() instanceof ServerLevel level) {
                registry.queueClusterTopologyChange(level, target.pos());
            }
            var partId = IPartItem.getId(partItem);
            var expectedPartSideName = FrequencyCardAutoConnectTarget.placedPartStorageSideName(
                    target.side().getName(),
                    ICablePart.class.isAssignableFrom(partItem.getPartClass()));
            registry.queuePartAutoConnect(
                    player,
                    player.level().dimension(),
                    target.pos(),
                    target.side(),
                    partId.toString(),
                    expectedPartSideName,
                    2);
        }
    }

    static PartAutoConnectTarget toAutoConnectTarget(
            BlockPos clickedPos,
            Direction clickedSide,
            PartPlacement.Placement placement) {
        var target = FrequencyCardAutoConnectTarget.fromPartPlacement(
                new FrequencyCardAutoConnectTarget.GridPos(clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()),
                clickedSide.getName(),
                new FrequencyCardAutoConnectTarget.GridPos(
                        placement.pos().getX(),
                        placement.pos().getY(),
                        placement.pos().getZ()),
                placement.side().getName());
        return new PartAutoConnectTarget(
                new BlockPos(target.pos().x(), target.pos().y(), target.pos().z()),
                Direction.byName(target.sideName()));
    }

    record PartAutoConnectTarget(BlockPos pos, Direction side) {
    }
}
