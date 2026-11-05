package com.moakiee.ae2lt.dimension;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PigmeeDimensionLayoutTest {
    @Test void oddFootprintHasOneBlockGapAndOneCenter() {
        assertEquals("...............F", row(false, 0));
        assertEquals(".......C.......F", row(false, 7));
        assertEquals("FFFFFFFFFFFFFFFF", row(false, 15));
        assertEquals(31, count(false, 'F'));
        assertEquals(15 * 15, count(false, '.') + count(false, 'C'));
        assertEquals(1, count(false, 'C'));
    }

    @Test void evenFootprintSplitsTwoBlockGapAcrossChunkEdge() {
        assertEquals("FFFFFFFFFFFFFFFF", row(true, 0));
        assertEquals("F..............F", row(true, 1));
        assertEquals("F......CC......F", row(true, 7));
        assertEquals("F......CC......F", row(true, 8));
        assertEquals("FFFFFFFFFFFFFFFF", row(true, 15));
        assertEquals(60, count(true, 'F'));
        assertEquals(14 * 14, count(true, '.') + count(true, 'C'));
        assertEquals(4, count(true, 'C'));
    }

    @Test void patternIsPeriodicAcrossPositiveAndNegativeChunkBoundaries() {
        for (boolean even : new boolean[] {false, true}) {
            for (int x = -33; x <= 33; x++) for (int z = -33; z <= 33; z++) {
                assertEquals(PigmeeDimensionLayout.cell(even, x, z), PigmeeDimensionLayout.cell(even, x + 16, z - 32));
            }
        }
        assertEquals(PigmeeDimensionLayout.Cell.FRAME, PigmeeDimensionLayout.cell(false, -1, 7));
        assertEquals(PigmeeDimensionLayout.Cell.FLOOR, PigmeeDimensionLayout.cell(false, -2, 7));
        assertEquals(PigmeeDimensionLayout.Cell.FLOOR, PigmeeDimensionLayout.cell(false, 0, 7));
        assertEquals(PigmeeDimensionLayout.Cell.FRAME, PigmeeDimensionLayout.cell(true, -1, 7));
        assertEquals(PigmeeDimensionLayout.Cell.FRAME, PigmeeDimensionLayout.cell(true, 0, 7));
        assertEquals(PigmeeDimensionLayout.Cell.FLOOR, PigmeeDimensionLayout.cell(true, -2, 7));
        assertEquals(PigmeeDimensionLayout.Cell.FLOOR, PigmeeDimensionLayout.cell(true, 1, 7));
    }

    private static String row(boolean even, int z) {
        var row = new StringBuilder();
        for (int x = 0; x < 16; x++) row.append(symbol(even, x, z));
        return row.toString();
    }

    private static char symbol(boolean even, int x, int z) {
        return switch (PigmeeDimensionLayout.cell(even, x, z)) {
            case FRAME -> 'F'; case CENTER -> 'C'; case FLOOR -> '.';
        };
    }

    private static int count(boolean even, char symbol) {
        int count = 0;
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) if (symbol(even, x, z) == symbol) count++;
        return count;
    }
}
