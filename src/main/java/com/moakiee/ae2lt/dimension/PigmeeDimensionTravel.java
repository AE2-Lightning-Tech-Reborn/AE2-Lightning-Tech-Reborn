package com.moakiee.ae2lt.dimension;

import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

public final class PigmeeDimensionTravel {
    private static final String RETURN = "ae2lt_pigmee_return";
    private PigmeeDimensionTravel() {}

    public static boolean enter(ServerPlayer player, int id) {
        var entry = PigmeeDimensionData.get(player.server).find(id);
        if (entry == null || !entry.access().getPlayerAccess(player).canUse()) {
            message(player, "no_access");
            return false;
        }
        var target = PigmeeDimensions.getOrCreate(player.server, entry);
        var feet = findSafe(target, new BlockPos(8, PigmeeDimensionLayout.SPAWN_Y, 8));
        if (feet == null || !ensureReturnBlock(target, feet)) {
            message(player, "no_space");
            return false;
        }
        CompoundTag origin = null;
        if (!PigmeeDimensions.isPigmee(player.level())) {
            origin = new CompoundTag();
            origin.putString("Dimension", player.level().dimension().location().toString());
            origin.putLong("Pos", player.blockPosition().asLong());
            origin.putFloat("Yaw", player.getYRot());
            origin.putFloat("Pitch", player.getXRot());
        }
        var expected = target.dimension();
        player.closeContainer();
        player.teleportTo(target, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, player.getYRot(), player.getXRot());
        if (!player.level().dimension().equals(expected)) return false;
        if (origin != null) player.getPersistentData().put(RETURN, origin);
        player.fallDistance = 0;
        return true;
    }

    public static boolean leave(ServerPlayer player) {
        if (!PigmeeDimensions.isPigmee(player.level())) return false;
        var origin = player.getPersistentData().getCompound(RETURN);
        var resource = ResourceLocation.tryParse(origin.getString("Dimension"));
        ServerLevel target = resource == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, resource));
        BlockPos desired;
        if (target == null || PigmeeDimensions.isPigmee(target)) {
            target = player.server.overworld();
            desired = target.getSharedSpawnPos();
        } else desired = BlockPos.of(origin.getLong("Pos"));
        var feet = findSafe(target, desired);
        if (feet == null) {
            message(player, "no_space");
            return false;
        }
        player.closeContainer();
        player.teleportTo(target, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5,
                origin.getFloat("Yaw"), origin.getFloat("Pitch"));
        if (player.level() != target) return false;
        player.getPersistentData().remove(RETURN);
        player.fallDistance = 0;
        return true;
    }

    public static BlockPos findSafe(ServerLevel level, BlockPos requested) {
        for (int radius = 0; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = requested.getX() + dx, z = requested.getZ() + dz;
                    for (int dy : new int[] {0, 1, -1, 2, -2}) {
                        var feet = new BlockPos(x, requested.getY() + dy, z);
                        if (safe(level, feet)) return feet;
                    }
                    var feet = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                    if (safe(level, feet)) return feet;
                }
            }
        }
        return null;
    }

    private static boolean safe(ServerLevel level, BlockPos feet) {
        if (feet.getY() <= level.getMinBuildHeight() || feet.getY() + 2 >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(feet)) return false;
        var below = level.getBlockState(feet.below());
        if (!below.isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)
                || below.is(Blocks.MAGMA_BLOCK) || below.is(Blocks.CAMPFIRE) || below.is(Blocks.SOUL_CAMPFIRE)
                || !below.getFluidState().isEmpty()) return false;
        var box = new AABB(feet.getX() + .2, feet.getY(), feet.getZ() + .2,
                feet.getX() + .8, feet.getY() + 1.8, feet.getZ() + .8);
        return level.noCollision(box) && level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir();
    }

    private static boolean ensureReturnBlock(ServerLevel level, BlockPos feet) {
        // Reuse an existing return point, never replace player construction.
        for (var pos : BlockPos.betweenClosed(feet.offset(-3, -1, -3), feet.offset(3, 1, 3))) {
            if (level.getBlockState(pos).is(ModBlocks.PIGMEE_DIMENSION_RETURN.get())) return true;
        }
        for (var offset : new BlockPos[] {new BlockPos(2, 0, 0), new BlockPos(-2, 0, 0),
                new BlockPos(0, 0, 2), new BlockPos(0, 0, -2)}) {
            var pos = feet.offset(offset);
            if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                    && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) {
                level.setBlockAndUpdate(pos, ModBlocks.PIGMEE_DIMENSION_RETURN.get().defaultBlockState());
                return true;
            }
        }
        return false;
    }

    public static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable("gui.ae2lt.pigmee_dimension." + key), false);
    }
}
