package com.moakiee.ae2lt.event;

import java.util.ArrayDeque;
import java.util.HashSet;

import appeng.api.parts.PartHelper;
import appeng.api.util.DimensionalBlockPos;
import appeng.core.definitions.AEParts;
import appeng.util.Platform;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.logic.world.PlayerWorldActions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = AE2LightningTech.MODID)
public final class FirmamentPlanePlacementHandler {
    public static final int MAX_PLACEMENTS = 64;
    private static final int MAX_VISITED = 256;
    private static final int RADIUS = 8;

    private FirmamentPlanePlacementHandler() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (PlayerWorldActions.isCheckingInteraction() || event.isCanceled()
                || event.getUseBlock() == TriState.FALSE || event.getUseItem() == TriState.FALSE) return;
        var player = event.getEntity();
        var level = event.getLevel();
        if (!player.isShiftKeyDown() || player.isSpectator() || !player.getAbilities().mayBuild
                || event.getHand() != InteractionHand.MAIN_HAND || event.getFace() != Direction.UP
                || !event.getItemStack().is(AEParts.ANNIHILATION_PLANE.asItem())
                || !isAtBuildLimit(event.getPos().getY(), level.getMaxBuildHeight())
                || PartHelper.getPartHost(level, event.getPos()) == null) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
        if (level instanceof ServerLevel serverLevel) {
            int placed = placePlanes(player, serverLevel, event.getPos(), event.getHand());
            player.displayClientMessage(Component.translatable("ae2lt.firmament.planes_placed", placed), true);
        }
    }

    public static int placePlanes(Player player, ServerLevel level, BlockPos origin, InteractionHand hand) {
        if (player.level() != level || player.isSpectator() || !player.getAbilities().mayBuild
                || !isAtBuildLimit(origin.getY(), level.getMaxBuildHeight())) return 0;
        var pending = new ArrayDeque<BlockPos>();
        var visited = new HashSet<BlockPos>();
        pending.add(origin.immutable());
        visited.add(origin.immutable());
        int placed = 0;
        while (!pending.isEmpty() && placed < MAX_PLACEMENTS) {
            var held = player.getItemInHand(hand);
            if (!held.is(AEParts.ANNIHILATION_PLANE.asItem())) break;
            var pos = pending.removeFirst();
            if (!level.isLoaded(pos)) continue;
            var host = PartHelper.getPartHost(level, pos);
            if (host == null || host.getPart(null) == null) continue;
            var hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
            if (!player.mayUseItemAt(pos, Direction.UP, held)
                    || !Platform.hasPermissions(new DimensionalBlockPos(level, pos), player)
                    || !PlayerWorldActions.mayInteract(player, level, pos, hand, Direction.UP, hit.getLocation())) continue;

            if (host.getPart(Direction.UP) == null && host.canAddPart(held, Direction.UP)) {
                PartHelper.usePartItem(new UseOnContext(player, hand, hit));
                if (host.getPart(Direction.UP) != null
                        && host.getPart(Direction.UP).getPartItem().asItem() == AEParts.ANNIHILATION_PLANE.asItem()) {
                    placed++;
                }
            }
            for (var direction : Direction.Plane.HORIZONTAL) {
                var next = pos.relative(direction);
                if (Math.abs(next.getX() - origin.getX()) <= RADIUS
                        && Math.abs(next.getZ() - origin.getZ()) <= RADIUS
                        && visited.size() < MAX_VISITED && visited.add(next)) pending.addLast(next);
            }
        }
        return placed;
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (event.getItemStack().is(AEParts.ANNIHILATION_PLANE.asItem())) {
            event.getToolTip().add(Component.translatable("tooltip.ae2lt.firmament_plane_bulk_place")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static boolean isAtBuildLimit(int hostY, int maxBuildHeight) {
        return hostY + 1 >= maxBuildHeight;
    }
}
