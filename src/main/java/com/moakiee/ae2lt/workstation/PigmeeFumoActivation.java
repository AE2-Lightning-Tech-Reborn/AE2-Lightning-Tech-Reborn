package com.moakiee.ae2lt.workstation;

import com.moakiee.ae2lt.block.FumoBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Admin-only P1 conversion probe. No controller debit/refund rules are selected here.
 * Native protection cancellation is respected and an interrupted conversion is quarantined.
 */
public final class PigmeeFumoActivation {
    private PigmeeFumoActivation() { }

    public static PigmeeServerPlayer activate(ServerPlayer owner, BlockPos pos) {
        return activate(owner, pos, false);
    }

    public static PigmeeServerPlayer activateCreative(ServerPlayer owner, BlockPos pos) {
        return activate(owner, pos, true);
    }

    private static PigmeeServerPlayer activate(ServerPlayer owner, BlockPos pos, boolean creativeEntry) {
        var level = owner.serverLevel();
        if (!PigmeeWorkstationRuntime.enabled() || !owner.server.isSameThread()
                || owner.isFakePlayer() || (creativeEntry ? !owner.isCreative() : !owner.hasPermissions(2))) {
            throw new IllegalStateException("Only a real administrator may use the experimental activation entry");
        }
        if (!level.hasChunkAt(pos) || !level.mayInteract(owner, pos) || !owner.canInteractWithBlock(pos, 0)) {
            throw new IllegalStateException("Activation target is unloaded, protected or out of reach");
        }
        var source = level.getBlockState(pos);
        PigmeeVariant variant = PigmeeVariant.fromBlock(source);
        if (variant == null) throw new IllegalArgumentException("Target is not one of the three workstation Pigmees");
        var feet = Vec3.atBottomCenterOf(pos.above());
        if (pos.getY() + 3 >= level.getMaxBuildHeight() || !level.getWorldBorder().isWithinBounds(pos)
                || !level.noCollision(new AABB(feet.x - .3, feet.y, feet.z - .3, feet.x + .3, feet.y + 1.8, feet.z + .3))) {
            throw new IllegalStateException("No safe player-sized activation space above Fumo");
        }
        if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, source, owner)).isCanceled()) {
            throw new IllegalStateException("Activation denied by block protection event");
        }
        // Event handlers can change the source. Never overwrite that change.
        if (!level.getBlockState(pos).equals(source)) throw new IllegalStateException("Activation source changed");
        var be = level.getBlockEntity(pos);
        CompoundTag sourceData = be == null ? new CompoundTag() : be.saveWithFullMetadata(level.registryAccess());
        var manager = PigmeeWorkstationRuntime.manager(owner.server);
        Direction facing = source.getValue(FumoBlock.FACING);
        var record = manager.prepare(owner.getUUID(), level, feet, facing.toYRot(), variant);
        CompoundTag origin = new CompoundTag();
        origin.putString("Dimension", level.dimension().location().toString());
        origin.putLong("Pos", pos.asLong());
        origin.putString("Block", variant.blockId().toString());
        origin.put("BlockEntity", sourceData.copy());
        record.origin = origin;
        if (sourceData.contains("CustomName", net.minecraft.nbt.Tag.TAG_STRING)) {
            record.playerData.putString("CustomName", sourceData.getString("CustomName"));
        }
        var replacement = source.getFluidState().createLegacyBlock();
        boolean removed = false;
        try {
            // No drops: the placed Fumo is converted into the one runtime identity.
            removed = level.setBlock(pos, replacement, Block.UPDATE_ALL);
            if (!removed) throw new IllegalStateException("Source removal rejected");
            return manager.activate(record.id);
        } catch (RuntimeException failed) {
            record.state = PigmeeActorStore.State.QUARANTINED;
            record.diagnostic = "Activation did not complete: " + failed.getMessage();
            manager.store.setDirty();
            if (removed && level.getBlockState(pos).equals(replacement) && manager.active(record.id) == null) {
                level.setBlock(pos, source, Block.UPDATE_ALL);
                var restored = level.getBlockEntity(pos);
                if (restored != null) restored.loadWithComponents(sourceData, level.registryAccess());
            }
            throw failed;
        }
    }
}
