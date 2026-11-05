package com.moakiee.ae2lt.dimension;

import com.moakiee.ae2lt.block.PigmeeBuildingBlock;
import com.moakiee.ae2lt.block.PigmeeBuildingPanelBlock;
import com.moakiee.ae2lt.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class PigmeeDimensionMaterials {
    private PigmeeDimensionMaterials() {}

    public static boolean accepts(Block block) {
        return block instanceof PigmeeBuildingBlock || block instanceof PigmeeBuildingPanelBlock;
    }

    public static List<Block> all() {
        var result = new ArrayList<Block>();
        result.add(ModBlocks.PIGMEE_BUILDING_BLOCK.get());
        for (var color : DyeColor.values()) {
            result.add(ModBlocks.PIGMEE_BUILDING_PANELS.get(color).get());
            result.add(ModBlocks.PIGMEE_FRAMED_BUILDING_PANELS.get(color).get());
        }
        return List.copyOf(result);
    }

    public static BlockState floor() {
        return ModBlocks.PIGMEE_BUILDING_PANELS.get(DyeColor.WHITE).get().defaultBlockState();
    }

    public static BlockState defaultFrame() {
        return ModBlocks.PIGMEE_FRAMED_BUILDING_PANELS.get(DyeColor.CYAN).get().defaultBlockState();
    }

    public static BlockState defaultCenter() {
        return ModBlocks.PIGMEE_BUILDING_PANELS.get(DyeColor.PINK).get().defaultBlockState();
    }

    public static ResourceLocation id(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }

    public static BlockState resolve(String id, BlockState fallback) {
        var key = ResourceLocation.tryParse(id);
        var block = key == null ? null : BuiltInRegistries.BLOCK.get(key);
        return accepts(block) ? block.defaultBlockState() : fallback;
    }
}
