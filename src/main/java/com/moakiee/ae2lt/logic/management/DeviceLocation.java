package com.moakiee.ae2lt.logic.management;

import appeng.api.networking.IGridNode;
import appeng.parts.AEBasePart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.block.entity.BlockEntity;

/** No chunk lookups: a node's live host supplies its identity and position. */
public record DeviceLocation(String dimension, BlockPos pos, int face, String type, String name) {
    public static DeviceLocation of(IGridNode node) {
        if (node == null) return null;
        Object owner = node.getOwner();
        BlockEntity be;
        int face = -1;
        String type;
        if (owner instanceof AEBasePart part) {
            be = part.getBlockEntity();
            face = part.getSide() == null ? -1 : part.getSide().ordinal();
            type = BuiltInRegistries.ITEM.getKey(part.getPartItem().asItem()).toString();
        } else if (owner instanceof BlockEntity block) {
            be = block;
            type = BuiltInRegistries.ITEM.getKey(be.getBlockState().getBlock().asItem()).toString();
        } else return null;
        if (be == null || be.isRemoved()) return null;
        String name = owner instanceof Nameable named && named.hasCustomName()
                ? named.getCustomName().getString() : "";
        return new DeviceLocation(node.getLevel().dimension().location().toString(), be.getBlockPos().immutable(), face, type, name);
    }
}
