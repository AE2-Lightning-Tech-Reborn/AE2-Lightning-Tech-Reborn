package com.moakiee.ae2lt.logic.world;

import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Bridges packet/delayed actions to the same protection events as player actions. */
public final class PlayerWorldActions {
    private static final ThreadLocal<Boolean> CHECKING_INTERACTION = ThreadLocal.withInitial(() -> false);

    private PlayerWorldActions() {}

    /** Our interaction handlers must not perform actions during a permission probe. */
    public static boolean isCheckingInteraction() {
        return CHECKING_INTERACTION.get();
    }

    public static boolean mayInteract(Player player, Level level, BlockPos pos) {
        return mayInteract(player, level, pos, InteractionHand.MAIN_HAND, Direction.UP, Vec3.atCenterOf(pos));
    }

    public static boolean mayInteract(Player player, Level level, BlockPos pos,
                                      InteractionHand hand, Direction face, Vec3 hit) {
        return player != null && player.level() == level && checkInteraction(player, level, pos, hand, face, hit);
    }

    /** The caller must first validate the menu/session that authorizes access to this target. */
    public static boolean mayConfigure(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return checkInteraction(player, level, pos, InteractionHand.MAIN_HAND, Direction.UP, Vec3.atCenterOf(pos));
    }

    private static boolean checkInteraction(Player player, Level level, BlockPos pos,
                                            InteractionHand hand, Direction face, Vec3 hit) {
        if (player == null || !(level instanceof ServerLevel targetLevel) || player.getServer() != targetLevel.getServer()
                || player.isSpectator() || !level.isLoaded(pos) || !level.mayInteract(player, pos)
                || isCheckingInteraction()) return false;
        CHECKING_INTERACTION.set(true);
        try {
            var event = NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(
                    player, hand, pos, new BlockHitResult(hit, face, pos, false)) {
                // Protection listeners must inspect the configured device's world.
                @Override public Level getLevel() { return level; }
            });
            return !event.isCanceled() && event.getUseBlock() != TriState.FALSE
                    && event.getUseItem() != TriState.FALSE;
        } finally {
            CHECKING_INTERACTION.remove();
        }
    }

    /** Fixed-state placement with NeoForge's player placement/rollback semantics. Caller consumes items on success. */
    public static boolean placeBlock(Player player, Level level, BlockPos pos, BlockState state) {
        if (player == null || !(level instanceof ServerLevel) || player.level() != level
                || player.isSpectator() || !player.getAbilities().mayBuild || !level.isLoaded(pos)
                || !level.mayInteract(player, pos)
                || !player.mayUseItemAt(pos, Direction.UP, new ItemStack(state.getBlock()))
                || level.captureBlockSnapshots) return false;

        int start = level.capturedBlockSnapshots.size();
        var snapshots = new ArrayList<BlockSnapshot>();
        boolean accepted = false;
        try {
            boolean placed;
            level.captureBlockSnapshots = true;
            try {
                placed = level.setBlock(pos, state, Block.UPDATE_ALL);
            } finally {
                level.captureBlockSnapshots = false;
                snapshots.addAll(level.capturedBlockSnapshots.subList(start, level.capturedBlockSnapshots.size()));
                level.capturedBlockSnapshots.subList(start, level.capturedBlockSnapshots.size()).clear();
            }
            if (!placed || snapshots.isEmpty()) return false;
            boolean canceled = snapshots.size() == 1
                    ? EventHooks.onBlockPlace(player, snapshots.getFirst(), Direction.UP)
                    : EventHooks.onMultiBlockPlace(player, snapshots, Direction.UP);
            if (canceled) return false;
            accepted = true;
        } finally {
            if (!accepted) {
                boolean restoring = level.restoringBlockSnapshots;
                level.restoringBlockSnapshots = true;
                try {
                    for (BlockSnapshot snapshot : snapshots.reversed()) {
                        snapshot.restore(snapshot.getFlags() | Block.UPDATE_CLIENTS);
                    }
                } finally {
                    level.restoringBlockSnapshots = restoring;
                }
            }
        }
        for (var snapshot : snapshots) {
            var current = level.getBlockState(snapshot.getPos());
            current.onPlace(level, snapshot.getPos(), snapshot.getState(), false);
            level.markAndNotifyBlock(snapshot.getPos(), level.getChunkAt(snapshot.getPos()),
                    snapshot.getState(), current, snapshot.getFlags(), 512);
        }
        return true;
    }
}
