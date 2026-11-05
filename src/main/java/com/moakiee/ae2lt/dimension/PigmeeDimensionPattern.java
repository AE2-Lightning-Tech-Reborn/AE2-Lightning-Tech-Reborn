package com.moakiee.ae2lt.dimension;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;

/** One surface block per cell, in z-major order, repeated at every chunk including negative coordinates. */
public final class PigmeeDimensionPattern {
    public static final int SIZE = 16;
    public static final int CELL_COUNT = SIZE * SIZE;
    public static final Codec<List<BlockState>> CODEC = BlockState.CODEC.listOf().validate(cells ->
            valid(cells) ? DataResult.success(cells) : DataResult.error(() -> "Expected zero or 256 Pigmee building cells"));

    private PigmeeDimensionPattern() {}

    public static boolean valid(List<BlockState> cells) {
        return (cells.isEmpty() || cells.size() == CELL_COUNT)
                && cells.stream().allMatch(state -> PigmeeDimensionMaterials.accepts(state.getBlock()));
    }

    public static List<BlockState> template(boolean even, BlockState frame, BlockState center) {
        var cells = new ArrayList<BlockState>(CELL_COUNT);
        for (int z = 0; z < SIZE; z++) for (int x = 0; x < SIZE; x++) {
            cells.add(switch (PigmeeDimensionLayout.cell(even, x, z)) {
                case FRAME -> frame;
                case CENTER -> center;
                case FLOOR -> PigmeeDimensionMaterials.floor();
            });
        }
        return List.copyOf(cells);
    }

    public static List<BlockState> decode(byte[] bytes) {
        if (bytes.length != CELL_COUNT) throw new IllegalArgumentException("A custom pattern must contain exactly 256 cells");
        var materials = PigmeeDimensionMaterials.all();
        var cells = new ArrayList<BlockState>(CELL_COUNT);
        for (byte value : bytes) {
            int index = Byte.toUnsignedInt(value);
            if (index >= materials.size()) throw new IllegalArgumentException("Invalid Pigmee material index");
            cells.add(materials.get(index).defaultBlockState());
        }
        return List.copyOf(cells);
    }

    public static byte[] encode(List<BlockState> cells) {
        if (!valid(cells)) throw new IllegalArgumentException("Invalid Pigmee cells");
        var materials = PigmeeDimensionMaterials.all();
        var result = new byte[cells.size()];
        for (int i = 0; i < result.length; i++) {
            int index = materials.indexOf(cells.get(i).getBlock());
            if (index < 0) throw new IllegalArgumentException("Unknown Pigmee material");
            result[i] = (byte) index;
        }
        return result;
    }
}
