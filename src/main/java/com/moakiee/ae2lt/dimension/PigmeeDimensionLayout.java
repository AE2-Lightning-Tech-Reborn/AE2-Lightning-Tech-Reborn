package com.moakiee.ae2lt.dimension;

/** Chunk-aligned geometry, shared by world generation and the creation preview. */
public final class PigmeeDimensionLayout {
    public static final int MIN_Y = -64;
    public static final int HEIGHT = 384;
    public static final int STONE_LAYERS = 80;
    public static final int DIRT_LAYERS = 4;
    public static final int SURFACE_Y = MIN_Y + STONE_LAYERS + DIRT_LAYERS;
    public static final int SPAWN_Y = SURFACE_Y + 1;

    public enum Cell { FLOOR, FRAME, CENTER }

    private PigmeeDimensionLayout() {}

    public static Cell cell(boolean even, int blockX, int blockZ) {
        int x = Math.floorMod(blockX, 16);
        int z = Math.floorMod(blockZ, 16);
        // The frame fills the separating lane, leaving the entire 15x15 / 14x14 interior usable.
        // Odd: lane at 15. Even: the two-block lane straddles the chunk seam at 15 and 0.
        if (x == 15 || z == 15 || even && (x == 0 || z == 0)) return Cell.FRAME;
        int centerMax = even ? 8 : 7;
        if (x >= 7 && x <= centerMax && z >= 7 && z <= centerMax) return Cell.CENTER;
        return Cell.FLOOR;
    }
}
